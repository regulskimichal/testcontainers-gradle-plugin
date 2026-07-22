package org.testcontainers.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.testcontainers.lifecycle.Startable

@DisableCachingByDefault(because = "Stops external containers")
abstract class StopContainersTask : DefaultTask() {

    @get:Input
    abstract val containerDefinitions: ListProperty<ContainerDefinition>

    @get:Internal
    abstract val testcontainersService: Property<TestcontainersBuildService>

    @TaskAction
    fun stop() {
        val service = testcontainersService.get()
        for (definition in containerDefinitions.get()) {
            logger.lifecycle("Stopping container: ${definition.name}")
            val container = service.getContainer<Startable>(definition)
            container.stop()
        }
    }
}
