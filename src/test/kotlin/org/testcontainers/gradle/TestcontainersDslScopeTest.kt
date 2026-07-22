package org.testcontainers.gradle

import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@RequiresDocker
class TestcontainersDslScopeTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `DSL marker prevents accessing outer scope implicitly`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        @Language("kotlin") val settingsKts = """rootProject.name = "test-dsl-marker""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")
        @Language("kotlin") val buildKts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                genericContainer("redis") {
                    image("redis:7-alpine")
                    // Implicit outer scope access should fail due to @DslMarker
                    genericContainer("nestedRedis") {
                        image("redis:7-alpine")
                    }
                }
            }
        """.trimIndent()
        buildFile.writeText(buildKts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("help")
            .buildAndFail()

        // Then
        assertTrue(result.output.contains("cannot be called in this context with an implicit receiver"))
    }

    @Test
    fun `DSL marker prevents calling jdbcContainer inside genericContainer block`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        @Language("kotlin") val settingsKts = """rootProject.name = "test-dsl-illegal-jdbc-in-generic""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")
        @Language("kotlin") val buildKts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                genericContainer("redis") {
                    image("redis:7-alpine")
                    // Illegal: outer scope method called inside genericContainer
                    jdbcContainer("postgres", "postgresql") {
                        databaseName("db")
                    }
                }
            }
        """.trimIndent()
        buildFile.writeText(buildKts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("help")
            .buildAndFail()

        // Then
        assertTrue(result.output.contains("cannot be called in this context with an implicit receiver"))
    }

    @Test
    fun `DSL marker prevents calling composeContainer inside jdbcContainer block`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        @Language("kotlin") val settingsKts = """rootProject.name = "test-dsl-illegal-compose-in-jdbc""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")
        @Language("kotlin") val buildKts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                jdbcContainer("postgres", "postgresql") {
                    databaseName("db")
                    // Illegal: outer scope method called inside jdbcContainer
                    composeContainer("stack", "compose.yaml")
                }
            }
        """.trimIndent()
        buildFile.writeText(buildKts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("help")
            .buildAndFail()

        // Then
        assertTrue(result.output.contains("cannot be called in this context with an implicit receiver"))
    }

    @Test
    fun `DSL marker prevents implicit access to extension property service inside spec`() {
        // Given
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        @Language("kotlin") val settingsKts = """rootProject.name = "test-dsl-illegal-service-access""""
        settingsFile.writeText(settingsKts)

        val buildFile = File(testProjectDir, "build.gradle.kts")
        @Language("kotlin") val buildKts = """
            plugins {
                id("io.github.regulskimichal.testcontainers")
            }

            testcontainers {
                jdbcContainer("postgres", "postgresql") {
                    databaseName("db")
                    // Illegal: implicitly accessing 'service' property of outer extension scope
                    val s = service
                }
            }
        """.trimIndent()
        buildFile.writeText(buildKts)

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("help")
            .buildAndFail()

        // Then
        assertTrue(
            result.output.contains("cannot be called in this context with an implicit receiver") ||
                    result.output.contains("can't be called in this context by implicit receiver") ||
                    result.output.contains("Unresolved reference 'service'")
        )
    }
}
