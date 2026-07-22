package org.testcontainers.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.bouncycastle.cms.RecipientId.password
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            image("postgres:18-alpine")
            databaseName("testdb")
            username("testuser")
            password("testpassword")
            reuse(true)
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
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", "postgresql") {
            // No configuration parameters passed at all
        }

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

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
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.testcontainers")

        val extension = project.extensions.getByType(TestcontainersExtension::class.java)
        extension.jdbcContainer("postgresdb", "postgresql") {
            image("custom-postgres:latest")
        }

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val startTask = project.tasks.findByName("startPostgresdbContainer") as? StartContainersTask
        assertNotNull(startTask)

        val postgresDef = startTask.containerDefinitions.get()[0] as ContainerDefinition.JdbcDatabase
        assertEquals("postgresdb", postgresDef.name)
        assertNotNull(postgresDef.dockerImageName)
        assertEquals("custom-postgres:latest", postgresDef.dockerImageName.image)
        assertEquals("postgres", postgresDef.dockerImageName.compatibleSubstituteFor)
    }
}
