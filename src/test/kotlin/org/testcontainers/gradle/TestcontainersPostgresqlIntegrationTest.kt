package org.testcontainers.gradle

import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class TestcontainersPostgresqlIntegrationTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `verifies postgresql container constructor is called once`() {
        mockkConstructor(PostgreSQLContainer::class)
        every { anyConstructed<PostgreSQLContainer<*>>().dockerImageName } returns "postgres"
        every { anyConstructed<PostgreSQLContainer<*>>().start() } just runs

        val project = ProjectBuilder.builder().build()
        project.plugins.apply(TESTCONTAINERS_PLUGIN_ID)

        val ext = project.extensions.getByType(TestcontainersExtension::class.java)
        ext.jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
            image("postgres:16-alpine")
            databaseName("testdb")
            username("user")
            password("secret")
        }

        (project as ProjectInternal).evaluate()

        val startTask = project.tasks.getByName("startPostgresContainer") as StartContainersTask
        startTask.start()

        verify(exactly = 1) {
            anyConstructed<PostgreSQLContainer<*>>().start()
        }
    }
}
