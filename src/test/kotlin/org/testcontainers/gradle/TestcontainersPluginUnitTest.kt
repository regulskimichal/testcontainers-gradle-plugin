package org.testcontainers.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestcontainersPluginUnitTest {

    @Test
    fun `plugin registers start and stop tasks with correct postgres configuration`() {
        // Create a mock project
        val project = ProjectBuilder.builder().build()

        // Apply the plugin under test
        project.plugins.apply("org.testcontainers")

        // Configure Postgres container via the plugin's DSL extension
        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", "postgresql") {
            image = "postgres:17-alpine"
            databaseName = "testdb"
            username = "testuser"
            password = "testpassword"
        }

        // Force project evaluation to trigger the project.afterEvaluate { ... } blocks
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // Verify that start and stop tasks were registered for our postgresdb container
        val startTask = project.tasks.findByName("startPostgresdbContainer") as? StartContainersTask
        val stopTask = project.tasks.findByName("stopPostgresdbContainer") as? StopContainersTask

        assertNotNull(startTask, "Start task should be registered")
        assertNotNull(stopTask, "Stop task should be registered")

        // Verify task definitions match our config
        val startDefinitions = startTask.containerDefinitions.get()
        assertEquals(1, startDefinitions.size)
        
        val postgresDef = startDefinitions[0] as ContainerDefinition.JdbcDatabase
        assertEquals("postgresdb", postgresDef.name)
        assertEquals("postgres:17-alpine", postgresDef.image)
        assertEquals("postgresql", postgresDef.databaseType)
        assertEquals("testdb", postgresDef.databaseName)
        assertEquals("testuser", postgresDef.username)
        assertEquals("testpassword", postgresDef.password)

        val stopDefinitions = stopTask.containerDefinitions.get()
        assertEquals(1, stopDefinitions.size)
        assertEquals("postgresdb", stopDefinitions[0].name)
    }
}
