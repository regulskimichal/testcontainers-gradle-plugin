package org.testcontainers.gradle.spec

import org.testcontainers.gradle.SerializableDockerImageName
import java.io.Serializable

/**
 * Configuration specification for JDBC database containers.
 *
 * Used within [org.testcontainers.gradle.TestcontainersConfig.jdbcContainer] DSL blocks to configure database containers
 * (PostgreSQL, MySQL, Oracle, etc.) with credentials, port mappings, and optional custom images.
 *
 * Example:
 * ```kotlin
 * testcontainers {
 *     jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
 *         databaseName("testdb")
 *         username("user")
 *         password("password")
 *         portMapping(5432)  // Fixed host port 5432
 *     }
 * }
 * ```
 *
 * At runtime, access the container to get JDBC connection details:
 * ```kotlin
 * task("migrate") {
 *     dependsOn("startPostgresContainer")
 *     doLast {
 *         val db = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres").get()
 *         println("URL: " + db.jdbcUrl)
 *         println("Username: " + db.username)
 *         println("Password: " + db.password)
 *     }
 * }
 * ```
 *
 * @param defaultCompatibleSubstitute Optional compatibility label for database-specific image substitution
 *
 * @see org.testcontainers.gradle.TestcontainersConfig.jdbcContainer for registration
 * @see org.testcontainers.gradle.DatabaseType for type-safe database selection
 */
class JdbcContainerSpec(internal val defaultCompatibleSubstitute: String? = null) {
    internal var dockerImageName: SerializableDockerImageName? = null
    internal var databaseName: String? = null
    internal var username: String? = null
    internal var password: String? = null
    internal var reuse: Boolean = false
    internal val portMappings: MutableList<PortMappingSpec> = mutableListOf()

    /**
     * Sets a custom Docker image name for this database container.
     *
     * Use this to override the default image for the database type. If not set,
     * the default image from [org.testcontainers.gradle.DatabaseType] is used.
     *
     * Examples:
     * - `image("postgres:15-alpine")` - Use PostgreSQL 15 with Alpine Linux
     * - `image("mysql:8.0.36")` - Use MySQL 8.0.36
     * - `image("registry.example.com/postgres:custom")` - Use a private registry image
     *
     * @param name The Docker image reference in standard format (repository:tag)
     */
    fun image(name: String) {
        this.dockerImageName = SerializableDockerImageName(name, defaultCompatibleSubstitute)
    }

    /**
     * Sets the initial database name to create inside the container.
     *
     * This database is created automatically when the container starts.
     * Not all databases support this (e.g., some Oracle versions may require manual setup).
     *
     * @param name The database name (e.g., "testdb", "myapp_db")
     */
    fun databaseName(name: String) {
        this.databaseName = name
    }

    /**
     * Sets the database administrator username.
     *
     * This is typically the root or system user used to connect to the database
     * and manage other users/permissions.
     *
     * @param name The username (e.g., "postgres", "root", "admin")
     */
    fun username(name: String) {
        this.username = name
    }

    /**
     * Sets the database administrator password.
     *
     * This is the password for the user specified by [username].
     * Keep in mind this is for local testing/development builds, not production.
     *
     * @param name The password
     */
    fun password(name: String) {
        this.password = name
    }

    /**
     * Whether to keep the container running across separate build executions.
     *
     * When set to `true`, Testcontainers will reuse an existing container instead of
     * creating a new one on each build. This speeds up builds but means state persists
     * (data from previous builds may still exist).
     *
     * Useful for:
     * - Development builds where full cleanup isn't required
     * - Avoiding container startup overhead
     *
     * Not recommended for:
     * - CI/CD pipelines where isolation is important
     * - Tests that require a clean database state
     *
     * @param reuse `true` to reuse containers, `false` to always create fresh containers
     */
    fun reuse(reuse: Boolean) {
        this.reuse = reuse
    }

    /**
     * Binds a container database port to a fixed host port.
     *
     * Without port mappings, Testcontainers assigns random available ports at startup.
     * Use this method to bind to fixed ports for predictable connections in build scripts.
     *
     * Examples:
     * - `portMapping(5432)` - Bind container port 5432 to host port 5432 (most databases use well-known ports)
     * - `portMapping(3306, 33060)` - Bind container port 3306 to host port 33060
     *
     * **Note**: Fixed port bindings can cause conflicts if multiple containers try to use the same port.
     * Consider using Testcontainers' dynamic port assignment (the default) in CI/CD environments.
     *
     * @param containerPort The port the database server listens on inside the container
     * @param hostPort The port to bind on the host machine. Defaults to the same value as [containerPort].
     *
     * @see org.testcontainers.gradle.ContainerDefinition.PortMapping for the internal representation
     */
    fun portMapping(containerPort: Int, hostPort: Int = containerPort) {
        portMappings.add(PortMappingSpec(hostPort, containerPort))
    }

    data class PortMappingSpec(
        val hostPort: Int,
        val containerPort: Int
    ) : Serializable
}
