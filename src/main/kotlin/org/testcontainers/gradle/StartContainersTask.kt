package org.testcontainers.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.testcontainers.lifecycle.Startable

@DisableCachingByDefault(because = "Starts external containers")
abstract class StartContainersTask : DefaultTask() {

    @get:Input
    abstract val containerDefinitions: ListProperty<ContainerDefinition>

    @get:Internal
    abstract val testcontainersService: Property<TestcontainersBuildService>

    @TaskAction
    fun start() {
        val service = testcontainersService.get()
        for (definition in containerDefinitions.get()) {
            logger.lifecycle("Starting container: ${definition.name}")
            val container = service.getContainer<Startable>(definition)
            container.start()
        }
    }
}
