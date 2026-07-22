package org.testcontainers.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.testcontainers.lifecycle.Startable

/**
 * Gradle task that stops registered containers and cleans up resources.
 *
 * Automatically registered by the plugin as `stop{Name}Container` for each container
 * definition. For example, a container named "postgres" generates a `stopPostgresContainer` task.
 *
 * This task:
 * - Stops all containers managed by [TestcontainersBuildService]
 * - Is only executed if the corresponding start task actually started the container
 * - Logs lifecycle events and handles stop failures gracefully
 * - Is automatically executed at build end via the build service lifecycle
 *
 * Normally you don't need to explicitly depend on stop tasks — they are automatically
 * managed by Gradle based on whether the start task actually ran. However, you can
 * make a task depend on a stop task to ensure cleanup happens before specific operations:
 *
 * ```kotlin
 * tasks.register("publish") {
 *     dependsOn("stopPostgresContainer")  // Ensure DB is stopped before publishing
 *     // ... publish logic ...
 * }
 * ```
 *
 * The task is automatically skipped (via [onlyIf]) if the corresponding start task didn't
 * actually start the container (i.e., it was UP-TO-DATE), preventing unnecessary logging
 * and avoiding errors from stopping containers that were never started.
 *
 * @see TestcontainersPlugin for automatic task registration
 * @see StartContainersTask for container startup
 * @see TestcontainersBuildService for container lifecycle management
 */
@DisableCachingByDefault(because = "Stops external containers")
abstract class StopContainersTask : DefaultTask() {

    /**
     * Container definitions to stop.
     * Automatically populated by the plugin during task registration.
     */
    @get:Input
    abstract val containerDefinitions: ListProperty<ContainerDefinition>

    /**
     * Reference to the shared build service managing container instances.
     * Automatically populated by the plugin and enforces single-threaded access.
     *
     * Internal API - do not set from build scripts.
     */
    @get:Internal
    abstract val testcontainersService: Property<TestcontainersBuildService>

    /**
     * Task action that stops all registered containers.
     *
     * Called by Gradle to shut down containers. Stops each container gracefully
     * and logs lifecycle events. Container stop failures are logged but do not
     * cause the task to fail (cleanup is best-effort).
     *
     * Internal API - do not call from build scripts.
     */
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
