package org.testcontainers.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@RequiresDocker
class TestcontainersPluginTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `plugin starts database container lazily on demand`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-project""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = $$"""
            import org.testcontainers.containers.JdbcDatabaseContainer
            import org.testcontainers.gradle.getContainer

            plugins {
                id("io.github.regulskimichal.testcontainers")
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
                    val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("db").get()
                    val url = db.jdbcUrl
                    val user = db.username
                    val password = db.password
                    println("TESTCONTAINERS_JDBC_URL=$url")
                    println("TESTCONTAINERS_USER=$user")
                    println("TESTCONTAINERS_PASSWORD=$password")
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output

        // Then
        val task = result.task(":testTask")
        assertEquals(TaskOutcome.SUCCESS, task?.outcome)

        assertTrue(output.contains("TESTCONTAINERS_JDBC_URL=jdbc:postgresql://"))
        assertTrue(output.contains("TESTCONTAINERS_USER=testuser"))
        assertTrue(output.contains("TESTCONTAINERS_PASSWORD=testpassword"))
    }

    @Test
    fun `plugin starts compose services dynamically`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-compose-project""""
        settingsFile.writeText(settingsKts)

        val composeFile = File(testProjectDir, "compose.yaml")

        @Language("yaml")
        val yaml = """
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
        """.trimIndent()
        composeFile.writeText(yaml)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = $$"""
            import org.testcontainers.containers.ComposeContainer
            import org.testcontainers.gradle.getContainer

            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                composeContainer("my-stack", "compose.yaml") {
                    service("web", 8080)
                    service("cache", 6379)
                }
            }

            tasks.register("testTask") {
                dependsOn("startMyStackContainer")
                usesService(testcontainers.service)
                doFirst {
                    val container = testcontainers.getContainer<ComposeContainer>("my-stack").get()
                    val webHost = container.getServiceHost("web", 8080)
                    val webPort = container.getServicePort("web", 8080)
                    val cacheHost = container.getServiceHost("cache", 6379)
                    val cachePort = container.getServicePort("cache", 6379)
                    
                    println("COMPOSE_WEB_HOST=$webHost")
                    println("COMPOSE_WEB_PORT=$webPort")
                    println("COMPOSE_CACHE_HOST=$cacheHost")
                    println("COMPOSE_CACHE_PORT=$cachePort")
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output

        // Then
        val task = result.task(":testTask")
        assertEquals(TaskOutcome.SUCCESS, task?.outcome)

        assertTrue(output.contains("COMPOSE_WEB_HOST="))
        assertTrue(output.contains("COMPOSE_WEB_PORT="))
        assertTrue(output.contains("COMPOSE_CACHE_HOST="))
        assertTrue(output.contains("COMPOSE_CACHE_PORT="))
    }

    @Test
    fun `plugin supports configuration cache`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-config-cache""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                jdbcContainer("db", "postgresql") {
                    image("postgres:17-alpine")
                }
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                "testcontainersClasspath"("org.testcontainers:postgresql:1.20.1")
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("startDbContainer", "--configuration-cache")

        val firstResult = runner.build()

        // Then
        assertTrue(firstResult.output.contains("Configuration cache entry stored"))

        // When (subsequent execution)
        val secondResult = runner.build()

        // Then
        assertTrue(secondResult.output.contains("Configuration cache entry reused"))
    }

    @Test
    fun `plugin supports project isolation`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-project-isolation""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                jdbcContainer("db", "postgresql") {
                    image("postgres:17-alpine")
                }
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                "testcontainersClasspath"("org.testcontainers:postgresql:1.20.1")
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "-Dorg.gradle.unsafe.isolated-projects=true")
            .build()

        // Then
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `plugin shares container instance in multi project build`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """
            rootProject.name = "multi-project"
            include("app", "core")
        """.trimIndent()
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
            plugins {
                id("io.github.regulskimichal.testcontainers") apply false
            }
            
            subprojects {
                apply(plugin = "io.github.regulskimichal.testcontainers")

                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    "testcontainersClasspath"("org.testcontainers:postgresql:1.20.1")
                }

                configure<org.testcontainers.gradle.TestcontainersExtension> {
                    jdbcContainer("db", "postgresql") {
                        image("postgres:17-alpine")
                    }
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        val appDir = File(testProjectDir, "app").apply { mkdirs() }
        val appBuildFile = File(appDir, "build.gradle.kts")

        @Language("kotlin")
        val appKts = """
            import org.testcontainers.containers.JdbcDatabaseContainer
            import org.testcontainers.gradle.getContainer

            tasks.register("printAppDb") {
                dependsOn("startDbContainer")
                usesService(testcontainers.service)
                doLast {
                    val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("db").get()
                    println("APP_DB_PORT=" + db.firstMappedPort)
                }
            }
        """.trimIndent()
        appBuildFile.writeText(appKts)

        val coreDir = File(testProjectDir, "core").apply { mkdirs() }
        val coreBuildFile = File(coreDir, "build.gradle.kts")

        @Language("kotlin")
        val coreKts = """
            import org.testcontainers.containers.JdbcDatabaseContainer
            import org.testcontainers.gradle.getContainer

            tasks.register("printCoreDb") {
                dependsOn("startDbContainer")
                usesService(testcontainers.service)
                doLast {
                    val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("db").get()
                    println("CORE_DB_PORT=" + db.firstMappedPort)
                }
            }
        """.trimIndent()
        coreBuildFile.writeText(coreKts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments(":app:printAppDb", ":core:printCoreDb")
            .build()

        val output = result.output

        // Then
        val appPortRegex = Regex("APP_DB_PORT=(\\d+)")
        val corePortRegex = Regex("CORE_DB_PORT=(\\d+)")

        val appPortMatch = appPortRegex.find(output)
        val corePortMatch = corePortRegex.find(output)

        val appPort = appPortMatch?.groupValues?.get(1)
        val corePort = corePortMatch?.groupValues?.get(1)

        assertTrue(appPort != null, "App port was not found in output")
        assertTrue(corePort != null, "Core port was not found in output")
        assertEquals(appPort, corePort, "Subprojects did not share the same container instance!")
    }

    @Test
    fun `generic container supports wait strategy and volume mount`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-volume-mount""""
        settingsFile.writeText(settingsKts)

        val htmlDir = File(testProjectDir, "html").apply { mkdirs() }
        val indexHtmlFile = File(htmlDir, "index.html")
        val uniqueContent = "Unique HTML content: " + java.util.UUID.randomUUID().toString()
        indexHtmlFile.writeText(uniqueContent)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = $$"""
            import org.testcontainers.containers.GenericContainer
            import org.testcontainers.gradle.getContainer
            import org.testcontainers.containers.wait.strategy.Wait
            import java.net.URL

            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                genericContainer("web") {
                    image("nginx:alpine")
                    exposedPorts(80)
                    mountVolume("$${htmlDir.absolutePath.replace("""\""", "/")}", "/usr/share/nginx/html", true)
                    waitHttp("/")
                }
            }

            tasks.register("testWeb") {
                dependsOn("startWebContainer")
                usesService(testcontainers.service)
                doLast {
                    val web = testcontainers.getContainer<GenericContainer<*>>("web").get()
                    val host = web.host
                    val port = web.firstMappedPort
                    val url = URL("http://$host:$port/")
                    val content = url.readText()
                    println("WEB_CONTENT=" + content.trim())
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testWeb")
            .build()

        val output = result.output

        // Then
        assertTrue(output.contains("WEB_CONTENT=$uniqueContent"))
    }

    @Test
    fun `start and stop tasks lifecycle with finalizedBy`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-lifecycle-failure""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
        plugins {
            id("io.github.regulskimichal.testcontainers")
        }

            testcontainers {
                genericContainer("nginx") {
                    image("nginx:alpine")
                    exposedPorts(80)
                }
            }

            tasks.register("failingTask") {
                dependsOn("startNginxContainer")
                finalizedBy("stopNginxContainer")
                doLast {
                    throw GradleException("Deliberate test failure")
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("failingTask")
            .buildAndFail()

        val output = result.output

        // Then
        val startTask = result.task(":startNginxContainer")
        val failingTask = result.task(":failingTask")
        val stopTask = result.task(":stopNginxContainer")

        assertEquals(TaskOutcome.SUCCESS, startTask?.outcome)
        assertEquals(TaskOutcome.FAILED, failingTask?.outcome)
        assertEquals(TaskOutcome.SUCCESS, stopTask?.outcome)

        assertTrue(output.contains("Stopping container: nginx"))
    }

    @Test
    fun `jdbc database supports compatible image substitute`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-jdbc-substitute""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
            import org.testcontainers.containers.JdbcDatabaseContainer
            import org.testcontainers.gradle.getContainer

                    plugins {
                        id("io.github.regulskimichal.testcontainers")
                    }

            testcontainers {
                jdbcContainer("db", "postgresql") {
                    image("postgis/postgis:15-3.3-alpine")
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
                doLast {
                    val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("db").get()
                    println("RESOLVED_IMAGE=" + db.dockerImageName)
                    println("DB_URL=" + db.jdbcUrl)
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output

        // Then
        assertTrue(output.contains("RESOLVED_IMAGE=postgis/postgis:15-3.3-alpine"))
        assertTrue(output.contains("DB_URL=jdbc:postgresql://"))
    }

    @Test
    fun `generic container supports custom compatible image substitute`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-custom-generic-substitute""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
            import org.testcontainers.containers.GenericContainer
            import org.testcontainers.gradle.getContainer

                    plugins {
                        id("io.github.regulskimichal.testcontainers")
                    }

            testcontainers {
                genericContainer("redis") {
                    image("redis:7-alpine", "redis")
                    exposedPorts(6379)
                }
            }

            tasks.register("testTask") {
                dependsOn("startRedisContainer")
                usesService(testcontainers.service)
                doLast {
                    val redis = testcontainers.getContainer<GenericContainer<*>>("redis").get()
                    println("RESOLVED_IMAGE=" + redis.dockerImageName)
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .build()

        val output = result.output

        // Then
        assertTrue(output.contains("RESOLVED_IMAGE=redis:7-alpine"))
    }

    @Test
    fun `compose container fails at startup when compose file is missing`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-missing-compose""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = """
        plugins {
            id("io.github.regulskimichal.testcontainers")
        }

            testcontainers {
                composeContainer("my-stack", "non-existent-compose.yaml") {
                    service("web", 8080)
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("startMyStackContainer")
            .buildAndFail()

        val output = result.output

        // Then
        assertTrue(output.contains("Unable to parse YAML file") || output.contains("non-existent-compose.yaml"))
    }

    @Test
    fun `accessor throws ClassCastException when retrieved with wrong container type`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")

        @Language("kotlin")
        val settingsKts = """rootProject.name = "test-wrong-accessor""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")

        @Language("kotlin")
        val kts = $$"""
            import org.testcontainers.containers.JdbcDatabaseContainer
            import org.testcontainers.gradle.getContainer

                    plugins {
                        id("io.github.regulskimichal.testcontainers")
                    }

            testcontainers {
                genericContainer("web") {
                    image("nginx:alpine")
                }
            }

            tasks.register("testTask") {
                dependsOn("startWebContainer")
                usesService(testcontainers.service)
                doLast {
                    val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("web").get()
                    println("RESOLVED_DB=$db")
                }
            }
        """.trimIndent()
        buildFile.writeText(kts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("testTask")
            .buildAndFail()

        val output = result.output

        // Then
        assertTrue(output.contains("ClassCastException") || output.contains("cannot be cast to"))
    }
}
