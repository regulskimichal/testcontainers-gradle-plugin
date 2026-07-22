import net.ltgt.gradle.flyway.tasks.FlywayMigrate
import net.ltgt.gradle.jooq.tasks.JooqCodegen
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.gradle.DatabaseType
import org.testcontainers.gradle.StartContainersTask
import org.testcontainers.gradle.getContainer
import org.testcontainers.gradle.wasContainerStarted


plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("net.ltgt.jooq-kotlin") version "1.0.0"
    id("io.github.regulskimichal.testcontainers") version "0.1.0-SNAPSHOT"
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
    "jooqCodegen"("org.jooq:jooq-codegen")
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

// startPostgresContainer is registered dynamically by the plugin in afterEvaluate,
// so project-specific inputs (trackedFiles) must also be set in afterEvaluate.
// The plugin automatically handles mustRunAfter(clean), usesService and onlyIf for stopPostgresContainer.
afterEvaluate {
    tasks.named<StartContainersTask>("startPostgresContainer") {
        trackedFiles.from(dbMigrationDir)
    }
}

val flywayMigrate = tasks.named<FlywayMigrate>("flywayMigrate") {
    // Note: We conditionally skip starting the Postgres container to speed up incremental builds
    // when no database migrations are needed. StartContainersTask tracks migration file changes
    // via Gradle's built-in UP-TO-DATE mechanism (trackedFiles + markerFile).
    dependsOn("startPostgresContainer")
    val postgresProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")

    // Assign to a local val to avoid capturing the outer script class in the onlyIf closure
    // (Configuration Cache requirement).
    val service = testcontainers.service
    usesService(service)

    url = postgresProvider.map { it.jdbcUrl }
    user = postgresProvider.map { it.username }
    password = postgresProvider.map { it.password }
    migrationLocations.setFrom(dbMigrationDir)

    // Since FlywayMigrate is untracked by Gradle (it modifies an external database),
    // it never becomes UP-TO-DATE natively. We skip it when the container was not started —
    // meaning migration files haven't changed since the last successful build.
    onlyIf { service.wasContainerStarted("postgres") }
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

    // Assign to a local val to avoid capturing the outer script class in the onlyIf closure
    // (Configuration Cache requirement).
    val service = testcontainers.service
    usesService(service)
    val postgresProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")

    configurationFile = jooqCodegenXml
    url = postgresProvider.map { it.jdbcUrl }
    user = postgresProvider.map { it.username }
    password = postgresProvider.map { it.password }

    val outDir = layout.projectDirectory.dir("src/main/generated")
    outputDirectory = outDir

    // JooqCodegen is also untracked. We execute it only if migrations ran (container started),
    // otherwise the database schema is unchanged and the existing generated sources are valid.
    onlyIf { service.wasContainerStarted("postgres") }
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
