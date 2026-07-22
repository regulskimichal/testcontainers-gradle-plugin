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

@DisableCachingByDefault(because = "Starts external containers")
abstract class StartContainersTask : DefaultTask() {

    @get:Input
    abstract val containerDefinitions: ListProperty<ContainerDefinition>

    @get:Internal
    abstract val testcontainersService: Property<TestcontainersBuildService>

    /**
     * Optional set of files to track for UP-TO-DATE checking.
     * When set together with [markerFile], the task is automatically skipped by Gradle
     * when the file contents have not changed since the last successful run.
     */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val trackedFiles: ConfigurableFileCollection

    /**
     * Optional output marker file written after a successful container start.
     * Together with [trackedFiles], enables Gradle's built-in UP-TO-DATE detection —
     * no manual hashing or [onlyIf] needed in the build script.
     */
    @get:OutputFile
    @get:Optional
    abstract val markerFile: RegularFileProperty

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
