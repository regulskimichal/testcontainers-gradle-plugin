package org.testcontainers.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.DockerClientFactory
import java.io.File

class TestcontainersPluginTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `plugin starts database container lazily on demand`() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable) {
            "Docker is not available, skipping integration test"
        }

        // Setup mock build files
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"test-project\"")

        val buildFile = File(testProjectDir, "build.gradle.kts")
        buildFile.writeText("""
            import org.testcontainers.gradle.getJdbcDatabaseContainer

            plugins {
                id("org.testcontainers")
            }

            testcontainers {
                jdbcContainer("db", "postgresql") {
                    image("postgres:17-alpine")
                    databaseName("testdb")
                    username("testuser")
                    password("testpassword")
                }
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                "testcontainersClasspath"("org.testcontainers:postgresql:1.20.1")
            }

            tasks.register("testTask") {
                dependsOn("startDbContainer")
                usesService(testcontainers.service)
                doFirst {
                    val db = testcontainers.getJdbcDatabaseContainer("db").get()
                    val url = db.jdbcUrl
                    val user = db.username
                    val password = db.password
                    println("TESTCONTAINERS_JDBC_URL=" + url)
                    println("TESTCONTAINERS_USER=" + user)
                    println("TESTCONTAINERS_PASSWORD=" + password)
                }
            }
        """.trimIndent())

        // Run Gradle build task using TestKit
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output
        println(output)

        // Verify task executed successfully
        val task = result.task(":testTask")
        assertEquals(TaskOutcome.SUCCESS, task?.outcome)

        // Verify dynamic coordinates were queried and logged
        assertTrue(output.contains("TESTCONTAINERS_JDBC_URL=jdbc:postgresql://"))
        assertTrue(output.contains("TESTCONTAINERS_USER=testuser"))
        assertTrue(output.contains("TESTCONTAINERS_PASSWORD=testpassword"))
    }

    @Test
    fun `plugin starts compose services dynamically`() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable) {
            "Docker is not available, skipping integration test"
        }

        // Setup settings.gradle.kts
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"test-compose-project\"")

        // Setup mock compose.yaml (using a small, fast generic image)
        val composeFile = File(testProjectDir, "compose.yaml")
        composeFile.writeText("""
            services:
              web:
                image: alpine:3.18
                command: nc -lk -p 8080 -e echo "hello"
                ports:
                  - "8080"
              cache:
                image: alpine:3.18
                command: nc -lk -p 6379 -e echo "hello"
                ports:
                  - "6379"
        """.trimIndent())

        // Setup build.gradle.kts
        val buildFile = File(testProjectDir, "build.gradle.kts")
        buildFile.writeText("""
            import org.testcontainers.gradle.getComposeContainer

            plugins {
                id("org.testcontainers")
            }

            testcontainers {
                composeContainer("my-stack", "compose.yaml") {
                    service("web", 8080)
                    service("cache", 6379)
                }
            }

            tasks.register("testTask") {
                dependsOn("startMy-stackContainer")
                usesService(testcontainers.service)
                doFirst {
                    val container = testcontainers.getComposeContainer("my-stack").get()
                    val webHost = container.getServiceHost("web", 8080)
                    val webPort = container.getServicePort("web", 8080)
                    val cacheHost = container.getServiceHost("cache", 6379)
                    val cachePort = container.getServicePort("cache", 6379)
                    
                    println("COMPOSE_WEB_HOST=" + webHost)
                    println("COMPOSE_WEB_PORT=" + webPort)
                    println("COMPOSE_CACHE_HOST=" + cacheHost)
                    println("COMPOSE_CACHE_PORT=" + cachePort)
                }
            }
        """.trimIndent())

        // Run Gradle build task using TestKit
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output
        println(output)

        // Verify task executed successfully
        val task = result.task(":testTask")
        assertEquals(TaskOutcome.SUCCESS, task?.outcome)

        // Verify coordinates were dynamically resolved and logged
        assertTrue(output.contains("COMPOSE_WEB_HOST="))
        assertTrue(output.contains("COMPOSE_WEB_PORT="))
        assertTrue(output.contains("COMPOSE_CACHE_HOST="))
        assertTrue(output.contains("COMPOSE_CACHE_PORT="))
    }
}
