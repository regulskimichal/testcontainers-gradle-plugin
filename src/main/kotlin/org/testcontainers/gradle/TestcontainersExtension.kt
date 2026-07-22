package org.testcontainers.gradle

import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import javax.inject.Inject

/**
 * Public extension for configuring container strategies in build scripts.
 *
 * Exposed as `testcontainers { }` DSL block in build.gradle.kts files, allowing users to:
 * - Register JDBC containers with [TestcontainersConfig.jdbcContainer]
 * - Register generic containers with [TestcontainersConfig.genericContainer]
 * - Register Docker Compose stacks with [TestcontainersConfig.composeContainer]
 * - Access running containers at execution time via [service]
 *
 * Example:
 * ```kotlin
 * testcontainers {
 *     jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
 *         databaseName("mydb")
 *         portMapping(5432)
 *     }
 *     genericContainer("redis") {
 *         image("redis:7-alpine")
 *         exposedPorts(6379)
 *     }
 * }
 * ```
 *
 * @see TestcontainersConfig for configuration methods
 * @see TestcontainersBuildService for runtime container access
 */
abstract class TestcontainersExtension @Inject constructor(
    layout: ProjectLayout
) : TestcontainersConfig(layout) {

    @PublishedApi
    internal val config: TestcontainersConfig get() = this

    /**
     * Service provider for accessing running containers and their connection details at execution time.
     *
     * Use this to lazily retrieve container instances in tasks or to check if a container was started:
     * ```kotlin
     * val postgres = testcontainers.service.map { it.getContainer<JdbcDatabaseContainer<*>>("postgres") }
     * task("migrate") {
     *     dependsOn("startPostgresContainer")
     *     doLast {
     *         val container = postgres.get()
     *         val url = container.jdbcUrl
     *     }
     * }
     * ```
     *
     * Thread-safe and configuration cache-compatible.
     */
    lateinit var service: Provider<TestcontainersBuildService>
}
