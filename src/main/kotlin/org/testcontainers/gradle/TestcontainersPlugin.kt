package org.testcontainers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.assign

/**
 * A minimal, framework-agnostic Gradle plugin that manages container lifecycle
 * for build-time tasks (code generation, schema inspection, etc.).
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
        @Suppress("UNCHECKED_CAST")
        ext.service  = serviceProvider as Provider<out BuildService<*>>

        // 4. Automatically register start/stop tasks for each container definition.
        project.afterEvaluate {
            for (definition in ext.config.resolveDefinitions()) {
                val capitalizedName = definition.name.replaceFirstChar {
                    if (it.isLowerCase()) it.uppercase() else it.toString()
                }
                
                project.tasks.register<StartContainersTask>("start${capitalizedName}Container") {
                    @Suppress("UNCHECKED_CAST")
                    testcontainersService.set(serviceProvider as Provider<TestcontainersBuildService>)
                    containerDefinitions.add(definition)
                }

                project.tasks.register<StopContainersTask>("stop${capitalizedName}Container") {
                    @Suppress("UNCHECKED_CAST")
                    testcontainersService.set(serviceProvider as Provider<TestcontainersBuildService>)
                    containerDefinitions.add(definition)
                }
            }
        }
    }
}
