import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.gradle.DatabaseType
import org.testcontainers.gradle.getContainer

plugins {
    id("io.github.regulskimichal.testcontainers") version "0.2.1" // x-release-please-version
}

testcontainers {
    jdbcContainer("db", DatabaseType.POSTGRESQL) {
        image("postgres:latest")
    }
}

dependencies {
    "testcontainersClasspath"("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.register("printDbInfo") {
    dependsOn("startDbContainer")
    usesService(testcontainers.service)

    val dbProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("db")

    doFirst {
        val db = dbProvider.get()
        logger.warn(
            """
            SUCCESSFULLY CONFIGURED POSTGRES CONTAINER!
            JDBC URL: ${db.jdbcUrl}
            Username: ${db.username}
            Password: ${db.password}""".trimIndent()
        )
    }
}
