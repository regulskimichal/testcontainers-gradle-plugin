import net.ltgt.gradle.flyway.tasks.FlywayMigrate
import net.ltgt.gradle.jooq.tasks.JooqCodegen
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun
import org.testcontainers.gradle.DatabaseType
import org.testcontainers.gradle.getJdbcDatabaseContainer

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("net.ltgt.jooq-kotlin") version "1.0.0"
    id("org.testcontainers") version "1.0.0-SNAPSHOT"
    id("net.ltgt.flyway") version "1.0.0"
}

group = "org.example.bookstore"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // jOOQ codegen classpath
    "jooqCodegen"("org.jooq:jooq-codegen:3.21.5")
    "jooqCodegen"("org.postgresql:postgresql")

    // Dynamic classpath for our generic Testcontainers Gradle plugin
    "testcontainersClasspath"("org.testcontainers:testcontainers-postgresql")

    // Flyway configuration for net.ltgt.flyway plugin
    "flyway"("org.flywaydb:flyway-core")
    "flyway"("org.flywaydb:flyway-database-postgresql")
    "flyway"("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}

// Declare required containers
testcontainers {
    jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
        image("postgres:18-alpine")
        databaseName("postgres")
        username("postgres")
        password("postgres")
    }
}

val dbMigrationDir = provider { layout.projectDirectory.dir("src/main/resources/db/migration") }
val migrationMarkerFile = layout.buildDirectory.file("flyway/migration.marker")


// A Gradle BuildService is used to share in-memory task execution state (whether migrations actually ran)
// across tasks in a 100% Configuration Cache-safe manner.
abstract class FlywayExecutionService : BuildService<BuildServiceParameters.None> {
    var flywayDidWork: Boolean = false
}

val flywayExecutionService = gradle.sharedServices.registerIfAbsent("flywayExecutionService", FlywayExecutionService::class.java) {}

val flywayMigrate = tasks.named<FlywayMigrate>("flywayMigrate") {
    // Note: We always depend on starting the Postgres container because it is a very lightweight operation
    // if no SQL statements run, and attempting to conditionally skip starting the container could break 
    // the lazy Gradle Providers and URL/credentials configuration passed to tasks using it.
    dependsOn("startPostgresContainer")
    val postgresProvider = testcontainers.getJdbcDatabaseContainer("postgres")
    
    // Assign properties to local variables to prevent capturing the outer script class (this$0)
    // inside the execution closures (onlyIf/doLast), avoiding Configuration Cache serialization issues.
    val service = flywayExecutionService
    usesService(testcontainers.service)
    usesService(service)

    url = postgresProvider.map { it.jdbcUrl }
    user = postgresProvider.map { it.username }
    password = postgresProvider.map { it.password }
    migrationLocations.setFrom(dbMigrationDir)

    val migrationDirFile = dbMigrationDir.get().asFile
    val markerFile = migrationMarkerFile.get().asFile

    // Since the FlywayMigrate task is untracked by Gradle (as it modifies an external database),
    // it never becomes UP-TO-DATE natively. We implement manual file-based hashing inside onlyIf
    // to determine whether to execute migrations.
    onlyIf {
        if (!markerFile.exists()) {
            true
        } else {
            val currentHash = migrationDirFile.walkTopDown()
                .filter { it.isFile && it.extension == "sql" }
                .sortedBy { it.name }
                .fold(0L) { acc, f -> acc xor f.readBytes().contentHashCode().toLong() }
                .toString()
            markerFile.readText().trim() != currentHash
        }
    }

    doLast {
        val currentHash = migrationDirFile.walkTopDown()
            .filter { it.isFile && it.extension == "sql" }
            .sortedBy { it.name }
            .fold(0L) { acc, f -> acc xor f.readBytes().contentHashCode().toLong() }
            .toString()
        markerFile.parentFile.mkdirs()
        markerFile.writeText(currentHash)
        
        // Mark that migration executed in this build run
        service.get().flywayDidWork = true
    }
}

val jooqCodegenXml = layout.buildDirectory.file("jooq/jooq-codegen.xml")

val generateJooqXml = tasks.register("generateJooqXml") {
    // language="XML"
    val xmlContent = """
        <configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.21.0.xsd">
            <generator>
                <name>org.jooq.codegen.KotlinGenerator</name>
                <database>
                    <name>org.jooq.meta.postgres.PostgresDatabase</name>
                    <inputSchema>public</inputSchema>
                    <excludes>flyway_schema_history</excludes>
                </database>
                <generate>
                    <deprecated>false</deprecated>
                    <records>true</records>
                    <immutablePojos>true</immutablePojos>
                    <fluentSetters>true</fluentSetters>
                </generate>
                <target>
                    <packageName>org.example.bookstore.generated</packageName>
                </target>
            </generator>
        </configuration>
        """.trimIndent()

    inputs.property("xmlContent", xmlContent)
    outputs.file(jooqCodegenXml)
    outputs.cacheIf { true }

    doLast {
        val content = inputs.properties["xmlContent"] as String
        outputs.files.singleFile.writeText(content)
    }
}

val jooqCodegen = tasks.named<JooqCodegen>("jooq") {
    dependsOn(generateJooqXml)
    dependsOn(flywayMigrate)
    finalizedBy("stopPostgresContainer")

    // Assign properties to local variables to prevent capturing the outer script class
    val service = flywayExecutionService
    usesService(testcontainers.service)
    usesService(service)
    val postgresProvider = testcontainers.getJdbcDatabaseContainer("postgres")

    configurationFile = jooqCodegenXml
    url = postgresProvider.map { it.jdbcUrl }
    user = postgresProvider.map { it.username }
    password = postgresProvider.map { it.password }

    val outDir = layout.projectDirectory.dir("src/main/generated")
    outputDirectory = outDir

    // JooqCodegen is also untracked. We execute it only if flywayMigrate performed migrations.
    // Otherwise, we skip it since the database schema remains unchanged.
    onlyIf {
        service.get().flywayDidWork
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    sourceSets {
        named("main") {
            kotlin.srcDir("src/main/generated")
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(jooqCodegen)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootRun>("bootRun") {
    jvmArgs("-XX:+UseZGC", "-XX:+UseCompactObjectHeaders")
}
