package org.testcontainers.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.testcontainers.lifecycle.Startable

/**
 * Gradle task that starts registered containers and marks them as started for the build.
 *
 * Automatically registered by the plugin as `start{Name}Container` for each container
 * definition. For example, a container named "postgres" generates a `startPostgresContainer` task.
 *
 * This task:
 * - Retrieves container instances from [TestcontainersBuildService]
 * - Starts each container and logs lifecycle events
 * - Marks containers as "started" for [TestcontainersBuildService.wasContainerStarted] checks
 * - Supports UP-TO-DATE detection via [markerFile] and [trackedFiles]
 * - Can be used as a task dependency for code generation, migrations, or tests
 *
 * UP-TO-DATE detection example:
 * ```kotlin
 * tasks.register<StartContainersTask>("startMyDb") {
 *     markerFile.set(layout.buildDirectory.file("markers/db.started"))
 *     trackedFiles.setFrom(fileTree("migrations") { include("*.sql") })
 * }
 * ```
 *
 * If tracked files haven't changed since last run, the task is skipped (UP-TO-DATE),
 * and [TestcontainersBuildService.wasContainerStarted] returns `false` for that container.
 * This allows downstream tasks to be automatically skipped when the container is not actually restarted.
 *
 * @see TestcontainersPlugin for automatic task registration
 * @see StopContainersTask for container shutdown
 * @see TestcontainersBuildService for container lifecycle management
 */
@DisableCachingByDefault(because = "Starts external containers")
abstract class StartContainersTask : DefaultTask() {

    /**
     * Container definitions to start.
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
     * Optional set of files to track for UP-TO-DATE checking.
     *
     * When set together with [markerFile], Gradle automatically skips this task if all tracked
     * files have unchanged contents since the last successful run. This enables conditional
     * execution of dependent tasks via [TestcontainersBuildService.wasContainerStarted].
     *
     * Common use case - skip database migrations if migration files unchanged:
     * ```kotlin
     * task("startDb") {
     *     trackedFiles.setFrom(fileTree("migrations"))
     *     markerFile.set(buildDir.file("markers/db.marker"))
     * }
     *
     * task("migrate") {
     *     dependsOn("startDb")
     *     val service = testcontainers.service
     *     onlyIf { service.wasContainerStarted("postgres") }
     * }
     * ```
     *
     * @see markerFile for the corresponding output file
     */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val trackedFiles: ConfigurableFileCollection

    /**
     * Optional output marker file written after successful container startup.
     *
     * Together with [trackedFiles], enables Gradle's built-in UP-TO-DATE detection using
     * file contents (not just timestamps). No manual hashing or `onlyIf` blocks needed.
     *
     * When set:
     * - The file is created/updated after all containers successfully start
     * - Gradle compares its content with previous runs
     * - If tracked files haven't changed, the task is skipped (UP-TO-DATE)
     * - [TestcontainersBuildService.wasContainerStarted] returns `false` for skipped tasks
     *
     * Automatically configured by the plugin to `buildDir/testcontainers/start{Name}.marker`
     * but can be overridden for custom behavior.
     *
     * @see trackedFiles for the corresponding input files
     */
    @get:OutputFile
    @get:Optional
    abstract val markerFile: RegularFileProperty

    /**
     * Task action that starts all registered containers.
     *
     * Called by Gradle after input/output validation. Retrieves containers from the build service,
     * starts each one, marks them as started, and writes the marker file if configured.
     *
     * Internal API - do not call from build scripts.
     */
    @TaskAction
    fun start() {
        val service = testcontainersService.get()
        for (definition in containerDefinitions.get()) {
            logger.lifecycle("Starting container: ${definition.name}")
            val container = service.getContainer<Startable>(definition)
            container.start()
            service.markContainerStarted(definition.name)
        }
        markerFile.orNull?.asFile?.also { f ->
            f.parentFile.mkdirs()
            f.writeText("started")
        }
    }
}
