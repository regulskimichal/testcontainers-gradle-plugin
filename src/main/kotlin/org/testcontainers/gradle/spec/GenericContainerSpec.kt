package org.testcontainers.gradle.spec

import org.testcontainers.gradle.SerializableDockerImageName
import org.testcontainers.gradle.dsl.TestcontainersDslMarker
import java.io.Serial
import java.io.Serializable
import java.time.Duration

/**
 * Configuration specification for generic Docker containers.
 *
 * Used within [org.testcontainers.gradle.TestcontainersConfig.genericContainer] DSL blocks to configure any Docker image
 * not covered by specialized container types (JDBC databases, Docker Compose).
 *
 * Useful for services like:
 * - Redis, Memcached (caching)
 * - Kafka, RabbitMQ (messaging)
 * - Elasticsearch, OpenSearch (search)
 * - Any public Docker image from Docker Hub or private registries
 *
 * Example - Redis cache:
 * ```kotlin
 * testcontainers {
 *     genericContainer("redis") {
 *         image("redis:7-alpine")
 *         exposedPorts(6379)
 *         waitPort()
 *     }
 * }
 * ```
 *
 * Example - Custom application:
 * ```kotlin
 * testcontainers {
 *     genericContainer("app") {
 *         image("myregistry.azurecr.io/myapp:latest")
 *         exposedPorts(8080, 8443)
 *         env("DEBUG" to "true", "LOG_LEVEL" to "info")
 *         mountVolume("./config", "/etc/app/config")
 *         waitHttp("/health", 200)
 *         startupTimeoutSeconds(30)
 *     }
 * }
 * ```
 *
 * @see org.testcontainers.gradle.TestcontainersConfig.genericContainer for registration
 * @see WaitStrategySpec for container readiness detection
 */
@Suppress("TooManyFunctions")
@TestcontainersDslMarker
class GenericContainerSpec {
    internal var dockerImageName: SerializableDockerImageName? = null
    internal var exposedPorts: List<Int> = emptyList()
    internal var env: Map<String, String> = emptyMap()
    internal var reuse: Boolean = false
    internal var startupTimeoutSeconds: Long = Duration.ofMinutes(1).seconds
    internal var waitStrategy: WaitStrategySpec = WaitStrategySpec.ListeningPort
    internal val volumeMounts = mutableListOf<VolumeMountSpec>()

    /**
     * Sets the Docker image name.
     *
     * Examples:
     * - `image("redis:7-alpine")` - Redis 7 with Alpine Linux
     * - `image("postgres:15")` - PostgreSQL 15 (for custom database container)
     * - `image("myregistry.example.com/service:v1.2.3")` - Custom registry image
     *
     * @param name The Docker image reference in standard format
     * @param compatibleSubstituteFor Optional Testcontainers compatibility label for image substitution
     */
    fun image(name: String, compatibleSubstituteFor: String? = null) {
        this.dockerImageName = SerializableDockerImageName(name, compatibleSubstituteFor)
    }

    /**
     * Sets the list of ports to expose from the container using varargs.
     *
     * Exposed ports are made available to the host and can be accessed via
     * [org.testcontainers.containers.GenericContainer.getFirstMappedPort] or [org.testcontainers.containers.GenericContainer.getMappedPort].
     * Without explicit mapping, Testcontainers assigns random available host ports.
     *
     * Examples:
     * - `exposedPorts(6379)` - Redis port
     * - `exposedPorts(6379, 6380)` - Redis with sentinel
     * - `exposedPorts(9200, 9300)` - Elasticsearch (client + cluster communication)
     *
     * @param ports Variable number of container ports to expose
     */
    fun exposedPorts(vararg ports: Int) {
        this.exposedPorts = ports.toList()
    }

    /**
     * Sets the list of ports to expose from the container using a List.
     *
     * Alternative to the varargs version for when you have ports in a collection.
     *
     * @param ports List of container ports to expose
     */
    fun exposedPorts(ports: List<Int>) {
        this.exposedPorts = ports
    }

    /**
     * Sets the environment variables to pass to the container using a Map.
     *
     * Environment variables are available inside the container and typically used to
     * configure application behavior, connection strings, credentials, etc.
     *
     * Example:
     * ```kotlin
     * env(mapOf(
     *     "REDIS_PORT" to "6379",
     *     "LOG_LEVEL" to "debug"
     * ))
     * ```
     *
     * @param env Map of environment variable names to values
     */
    fun env(env: Map<String, String>) {
        this.env = env
    }

    /**
     * Sets the environment variables to pass to the container using Pair arguments.
     *
     * More convenient than the Map version for a small number of variables.
     *
     * Example:
     * ```kotlin
     * env(
     *     "REDIS_PORT" to "6379",
     *     "LOG_LEVEL" to "debug",
     *     "ENABLE_PERSISTENCE" to "no"
     * )
     * ```
     *
     * @param pairs Variable number of name-to-value pairs
     */
    fun env(vararg pairs: Pair<String, String>) {
        this.env = pairs.toMap()
    }

    /**
     * Whether to keep the container instance running across separate build executions.
     *
     * When set to `true`, Testcontainers will reuse an existing container instead of
     * creating a new one on each build. This speeds up builds but means state persists
     * (data from previous runs may still exist).
     *
     * **Caution**: Reused containers retain state from previous builds!
     *
     * @param reuse `true` to reuse containers, `false` to always create fresh containers
     */
    fun reuse(reuse: Boolean) {
        this.reuse = reuse
    }

    /**
     * The maximum time in seconds to wait for the container to start before timing out.
     *
     * The wait timeout applies to the wait strategy (port listening, HTTP endpoint, log message).
     * If the container doesn't become ready within this timeout, the start task fails.
     *
     * Adjust based on how long your service typically takes to initialize.
     * Examples:
     * - 5 seconds for Redis or lightweight services
     * - 30+ seconds for heavy databases like Oracle or SQL Server
     *
     * Default: 60 seconds
     *
     * @param seconds Timeout duration in seconds
     */
    fun startupTimeoutSeconds(seconds: Long) {
        this.startupTimeoutSeconds = seconds
    }

    /**
     * Configure the wait strategy to listen for TCP connections on exposed ports.
     *
     * This is the default strategy for generic containers. Waits until all exposed ports
     * are listening for connections before considering the container ready.
     *
     * Suitable for most services (Redis, Kafka, databases, etc.).
     * Timeout is configured via [startupTimeoutSeconds].
     *
     * @see waitHttp for HTTP health checks
     * @see waitLog for log message matching
     */
    fun waitPort() {
        waitStrategy = WaitStrategySpec.ListeningPort
    }

    /**
     * Configure the wait strategy to check an HTTP endpoint for a specific status code.
     *
     * Waits until the HTTP endpoint returns the specified status code before
     * considering the container ready. Useful for services with HTTP health checks.
     *
     * Examples:
     * - `waitHttp("/health", 200)` - Spring Boot /health endpoint
     * - `waitHttp("/actuator/health", 200)` - Spring Boot actuator
     * - `waitHttp("/", 400)` - DynamoDB Local (returns 400 on /)
     * - `waitHttp("/status", 204)` - Services returning 204 No Content
     *
     * Timeout is configured via [startupTimeoutSeconds].
     *
     * @param path The HTTP path to probe (e.g., "/health", "/ready")
     * @param statusCode The expected HTTP status code (default 200)
     *
     * @see waitPort for port listening strategy
     * @see waitLog for log message matching
     */
    fun waitHttp(path: String, statusCode: Int = 200) {
        waitStrategy = WaitStrategySpec.Http(path, statusCode)
    }

    /**
     * Configure the wait strategy to detect a log message matching a regex pattern.
     *
     * Waits until the container logs contain a message matching the regex pattern
     * before considering the container ready. Useful for services that log startup events.
     *
     * Examples:
     * - `waitLog(".*Server started.*", 1)` - Server startup message
     * - `waitLog(".*Ready to accept connections.*", 1)` - PostgreSQL startup
     * - `waitLog(".*listening on port.*", 1)` - Generic service startup
     *
     * Timeout is configured via [startupTimeoutSeconds].
     *
     * @param regex The regex pattern to match in container logs
     * @param times The number of times the pattern must appear before considering ready (default 1)
     *
     * @see waitPort for port listening strategy
     * @see waitHttp for HTTP health checks
     */
    fun waitLog(regex: String, times: Int = 1) {
        waitStrategy = WaitStrategySpec.LogMessage(regex, times)
    }

    /**
     * Mounts a local file or directory into the container.
     *
     * Useful for:
     * - Passing configuration files to the container
     * - Sharing test data
     * - Enabling hot-reload scenarios
     * - Accessing container-generated files from the host
     *
     * Examples:
     * ```kotlin
     * // Mount configuration directory as read-only
     * mountVolume("./config", "/etc/app/config", readOnly = true)
     *
     * // Mount project directory for data sharing
     * mountVolume(".", "/data", readOnly = false)
     *
     * // Using Gradle file API
     * mountVolume(layout.projectDirectory.file("fixtures"), "/fixtures", readOnly = true)
     * ```
     *
     * @param hostPath The local source path (can be a File, Gradle Directory/RegularFile, or path string).
     *                  Relative paths are resolved from the project directory.
     * @param containerPath The mount point path inside the container
     * @param readOnly If `true`, the volume is mounted as read-only (default `false`)
     */
    fun mountVolume(hostPath: Any, containerPath: String, readOnly: Boolean = false) {
        volumeMounts.add(VolumeMountSpec(hostPath, containerPath, readOnly))
    }

    data class VolumeMountSpec(
        val hostPath: Any,
        val containerPath: String,
        val readOnly: Boolean
    ) : Serializable {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -5972957836880529540L
        }
    }

    /**
     * Wait strategy for detecting when a generic container is ready.
     *
     * The plugin supports three strategies:
     * - [ListeningPort]: Wait for exposed ports to start listening on TCP (default)
     * - [Http]: Wait for an HTTP endpoint to respond with a specific status code
     * - [LogMessage]: Wait for a log message matching a regex pattern
     *
     * @see GenericContainerSpec.waitPort for port-based waiting
     * @see GenericContainerSpec.waitHttp for HTTP-based waiting
     * @see GenericContainerSpec.waitLog for log-based waiting
     */
    sealed interface WaitStrategySpec : Serializable {
        /** Wait for exposed ports to start listening for TCP connections. */
        object ListeningPort : WaitStrategySpec {
            @Serial
            private const val serialVersionUID: Long = -5733784761358270496L
        }

        /**
         * Wait for an HTTP endpoint to respond with a specific status code.
         *
         * @param path The HTTP path to probe
         * @param statusCode The expected HTTP status code
         */
        data class Http(val path: String, val statusCode: Int) : WaitStrategySpec {
            companion object {
                @Serial
                private const val serialVersionUID: Long = 7124312244984591410L
            }
        }

        /**
         * Wait for a log message matching a regex pattern.
         *
         * @param regex The regex pattern to match in logs
         * @param times The number of occurrences needed to consider the container ready
         */
        data class LogMessage(val regex: String, val times: Int) : WaitStrategySpec {
            companion object {
                @Serial
                private const val serialVersionUID: Long = -5631894294024599878L
            }
        }
    }

    internal fun validate() {
        requireNotNull(dockerImageName) { "Generic container requires DockerImageName" }
    }
}
