package org.testcontainers.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestcontainersPluginUnitTest {

    @Test
    fun `plugin registers start and stop tasks with correct postgres configuration`() {
        // Given
        val project = ProjectBuilder.builder().build()

        // Apply the plugin under test
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        // Configure Postgres container via the plugin's DSL extension
        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", DatabaseType.POSTGRESQL) {
            image("postgres:18-alpine")
            databaseName("testdb")
            username("testuser")
            password("testpassword")
            reuse(true)
        }

        // When
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // Then
        val startTask = project.tasks.findByName("startPostgresdbContainer") as? StartContainersTask
        val stopTask = project.tasks.findByName("stopPostgresdbContainer") as? StopContainersTask

        assertNotNull(startTask, "Start task should be registered")
        assertNotNull(stopTask, "Stop task should be registered")

        val startDefinitions = startTask.containerDefinitions.get()
        assertEquals(1, startDefinitions.size)

        val postgresDef = startDefinitions[0] as ContainerDefinition.JdbcDatabase
        assertEquals("postgresdb", postgresDef.name)
        assertNotNull(postgresDef.dockerImageName)
        assertEquals("postgres:18-alpine", postgresDef.dockerImageName.image)
        assertEquals("postgresql", postgresDef.databaseType)
        assertEquals("testdb", postgresDef.databaseName)
        assertEquals("testuser", postgresDef.username)
        assertEquals("testpassword", postgresDef.password)
        assertTrue(postgresDef.reuse)

        val stopDefinitions = stopTask.containerDefinitions.get()
        assertEquals(1, stopDefinitions.size)
        assertEquals("postgresdb", stopDefinitions[0].name)
    }

    @Test
    fun `plugin registers start task without explicitly configuring optional parameters`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", DatabaseType.POSTGRESQL) {
            // No configuration parameters passed at all
        }

        // When
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // Then
        val startTask = project.tasks.findByName("startPostgresdbContainer") as? StartContainersTask
        assertNotNull(startTask)

        val postgresDef = startTask.containerDefinitions.get()[0] as ContainerDefinition.JdbcDatabase
        assertEquals("postgresdb", postgresDef.name)
        kotlin.test.assertNull(postgresDef.dockerImageName, "DockerImageName should be null")
        kotlin.test.assertNull(postgresDef.databaseName, "Database name should be null")
        kotlin.test.assertNull(postgresDef.username, "Username should be null")
        kotlin.test.assertNull(postgresDef.password, "Password should be null")
    }

    @Test
    fun `plugin automatically resolves compatible substitute from database type`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", DatabaseType.POSTGRESQL) {
            image("custom-postgres:latest")
        }

        // When
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // Then
        val startTask = project.tasks.findByName("startPostgresdbContainer") as? StartContainersTask
        assertNotNull(startTask)

        val postgresDef = startTask.containerDefinitions.get()[0] as ContainerDefinition.JdbcDatabase
        assertEquals("postgresdb", postgresDef.name)
        assertNotNull(postgresDef.dockerImageName)
        assertEquals("custom-postgres:latest", postgresDef.dockerImageName.image)
        assertEquals("postgres", postgresDef.dockerImageName.compatibleSubstituteFor)
    }

    @Test
    fun `genericContainer validation fails when image is missing`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)

        // When & Then
        val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            extension.genericContainer("invalid") {
                // no image configured
            }
        }
        assertEquals("Generic container requires DockerImageName", exception.message)
    }

    @Test
    fun `composeContainer validation fails when no services are exposed`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)

        // When & Then
        val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            extension.composeContainer("invalid-compose", "compose.yaml") {
                // no service() called
            }
        }
        assertEquals(
            "Container 'invalid-compose' error: at least one service must be exposed via service().",
            exception.message
        )
    }

    @Test
    fun `extension accessors throw error for unregistered container name`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.regulskimichal.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)

        // When & Then
        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            extension.getContainer<org.testcontainers.containers.JdbcDatabaseContainer<*>>("non-existent")
        }
        assertEquals("No container registered with name 'non-existent'.", exception.message)
    }

    @Test
    fun `resolveCanonicalImageName resolves all DatabaseType enums and aliases correctly`() {
        // When & Then
        // Test all enum entries resolve to their canonical image names
        DatabaseType.entries.forEach { dbType ->
            assertEquals(dbType.canonicalImageName, resolveCanonicalImageName(dbType.id))
            assertEquals(dbType.canonicalImageName, resolveCanonicalImageName(dbType.name))
        }

        // Test common aliases and partial strings
        assertEquals("postgres", resolveCanonicalImageName("postgres"))
        assertEquals("mcr.microsoft.com/mssql/server", resolveCanonicalImageName("mssql"))
        assertEquals("mcr.microsoft.com/mssql/server", resolveCanonicalImageName("sqlserver"))
        assertEquals("postgis/postgis", resolveCanonicalImageName("my-postgis-db"))
        assertEquals("timescale/timescaledb", resolveCanonicalImageName("timescale"))

        // Test invalid/unknown database type
        kotlin.test.assertNull(resolveCanonicalImageName("unknown-db"))
    }
}
