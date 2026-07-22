package org.testcontainers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register

/**
 * A minimal, framework-agnostic Gradle plugin that manages container lifecycle for build-time tasks.
 *
 * This plugin provides:
 * - Lazy container lifecycle management (start/stop containers on demand)
 * - Build-time container registration for tasks that need external services
 * - Automatic generation of `start` and `stop` container tasks
 * - Configuration cache and build cache support
 * - Reusable container instances across build executions (via Testcontainers reuse feature)
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("org.testcontainers")
 * }
 *
 * testcontainers {
 *     jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
 *         databaseName = "testdb"
 *         portMapping(5432)
 *     }
 * }
 * ```
 *
 * The plugin automatically registers `startPostgresContainer` and `stopPostgresContainer` tasks
 * that can be used as task dependencies for build-time code generation or schema inspection tasks.
 */
class TestcontainersPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // 1. Create the extension — users configure strategy here
        val ext = project.extensions.create("testcontainers", TestcontainersExtension::class.java)

        // Create a configuration for custom Testcontainers modules
        val classpath = project.configurations.create("testcontainersClasspath") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        // 2. Register the build service — parameters resolved lazily after project evaluation
        val serviceName = "testcontainers-${project.rootProject.name.replace(":", "-")}"
        val serviceProvider = project.gradle.sharedServices
            .registerIfAbsent(serviceName, TestcontainersBuildService::class.java) {
                // Restrict parallel task execution using this service to 1.
                // This prevents parallel tasks (e.g. concurrent tests or codegen steps)
                // from causing race conditions on the same shared running container instance.
                maxParallelUsages = 1
                parameters {
                    classpathFiles.setFrom(classpath)
                }
            }

        // 3. Wire execution-time providers onto the extension.
        ext.service = serviceProvider

        // 4. Automatically register start/stop tasks for each container definition.
        project.afterEvaluate {
            for (definition in ext.config.resolveDefinitions()) {
                val sanitizedName = definition.name.split(Regex("[^a-zA-Z0-9]+"))
                    .filter { it.isNotEmpty() }
                    .joinToString("") { part ->
                        part.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
                    }
                val containerName = definition.name

                project.tasks.register<StartContainersTask>("start${sanitizedName}Container") {
                    testcontainersService.set(serviceProvider)
                    containerDefinitions.add(definition)
                    // Ensure the start task runs after clean so that UP-TO-DATE checks
                    // are re-evaluated properly when the user runs `clean build`.
                    mustRunAfter(project.tasks.matching { it.name == "clean" })
                    // Automatically configure a default marker file to support UP-TO-DATE checks.
                    markerFile.set(project.layout.buildDirectory.file("testcontainers/start${sanitizedName}.marker"))
                }

                project.tasks.register<StopContainersTask>("stop${sanitizedName}Container") {
                    testcontainersService.set(serviceProvider)
                    containerDefinitions.add(definition)
                    // Wire the build service so Gradle enforces max-parallelism constraints.
                    usesService(serviceProvider)
                    // Skip stopping when the container was never started (e.g. all tasks were
                    // UP-TO-DATE and the container start was skipped).
                    onlyIf { serviceProvider.get().wasContainerStarted(containerName) }
                }
            }
        }
    }
}
