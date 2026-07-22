package org.testcontainers.gradle

import org.gradle.api.provider.Provider

import org.testcontainers.lifecycle.Startable

/**
 * Retrieves a registered container instance as a [Provider] for lazy execution-time access.
 *
 * This is the primary method for accessing running containers in Gradle tasks.
 * The provider ensures that:
 * - Containers are only retrieved at task execution time (not during configuration)
 * - Values are cached within a single build execution
 * - Configuration cache and distributed builds are fully supported
 *
 * Type parameter `T` must be a Testcontainers container type:
 * - [org.testcontainers.containers.JdbcDatabaseContainer] for JDBC databases
 * - [org.testcontainers.containers.GenericContainer] for generic containers
 * - [org.testcontainers.containers.ComposeContainer] for Docker Compose stacks
 * - [org.testcontainers.lifecycle.Startable] for any startable container
 *
 * Example - accessing a PostgreSQL database:
 * ```kotlin
 * import org.testcontainers.containers.JdbcDatabaseContainer
 *
 * task("migrate") {
 *     dependsOn("startPostgresContainer")
 *     doLast {
 *         val postgres = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")
 *         println("URL: " + postgres.get().jdbcUrl)
 *         println("Username: " + postgres.get().username)
 *         println("Password: " + postgres.get().password)
 *     }
 * }
 * ```
 *
 * Example - accessing a Redis container:
 * ```kotlin
 * import org.testcontainers.containers.GenericContainer
 *
 * task("test") {
 *     val redis = testcontainers.getContainer<GenericContainer<*>>("redis")
 *     doLast {
 *         val host = redis.get().host
 *         val port = redis.get().getFirstMappedPort()
 *     }
 * }
 * ```
 *
 * @param T The Testcontainers container type to return
 * @param name The container name as registered in the configuration (e.g., "postgres", "redis")
 * @return A configuration cache-safe [Provider] that lazily retrieves the container at execution time
 *
 * @throws IllegalArgumentException if no container is registered with the given name
 *
 * @see TestcontainersExtension for DSL registration
 * @see wasContainerStarted for checking if a container was actually started
 */
inline fun <reified T : Startable> TestcontainersExtension.getContainer(name: String): Provider<T> {
    val definition = this.config.definitions[name] ?: error("No container registered with name '$name'.")
    return this.service.map { service ->
        service.getContainer(definition)
    }
}

/**
 * Returns `true` if the named container was actually started during this build execution.
 *
 * This is useful for conditionally skipping downstream tasks when container startup is skipped
 * due to Gradle's UP-TO-DATE detection. When the [StartContainersTask] is UP-TO-DATE (because
 * tracked files haven't changed), the container is never started and this returns `false`,
 * allowing dependent tasks to be skipped as well.
 *
 * **Configuration Cache Compatible** - This pattern is safe for use with Gradle's configuration cache:
 * ```kotlin
 * tasks.register("migrate") {
 *     dependsOn("startPostgresContainer")
 *
 *     val service = testcontainers.service  // Capture Provider in configuration block
 *     onlyIf { service.wasContainerStarted("postgres") }
 *
 *     doLast {
 *         // Container is definitely running here if we reach this point
 *         val postgres = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")
 *         runMigrations(postgres.get())
 *     }
 * }
 * ```
 *
 * Why not just call `testcontainers.service.wasContainerStarted()`?
 * - The `onlyIf` block is evaluated during configuration phase
 * - Accessing `testcontainers.service` directly would force initialization of the build service
 * - This extension on [Provider] checks the service without forcing initialization
 *
 * @param name The container name as registered in the configuration (e.g., "postgres", "redis")
 * @return `true` if [StartContainersTask] successfully started the container; `false` if task was UP-TO-DATE
 *
 * @see StartContainersTask for when containers are marked as started
 * @see TestcontainersBuildService.wasContainerStarted for the underlying check
 */
fun Provider<TestcontainersBuildService>.wasContainerStarted(name: String): Boolean =
    get().wasContainerStarted(name)

