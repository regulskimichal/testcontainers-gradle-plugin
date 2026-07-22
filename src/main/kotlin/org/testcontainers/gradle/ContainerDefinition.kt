package org.testcontainers.gradle

import org.testcontainers.utility.DockerImageName
import java.io.Serial
import java.io.Serializable

/**
 * Serializable definition of a single container to be managed by the plugin.
 *
 * This sealed class hierarchy represents the three supported container types:
 * - [JdbcDatabase]: JDBC-compatible database containers (PostgreSQL, MySQL, Oracle, etc.)
 * - [Generic]: Any Docker image for services like Redis, Kafka, or custom applications
 * - [Compose]: Multi-container Docker Compose setups
 *
 * Container definitions are created during the configuration phase via [TestcontainersConfig]
 * and serialized for use in Gradle tasks and build services. This enables configuration cache
 * support and distributed builds.
 *
 * @see TestcontainersConfig for creating definitions
 * @see StartContainersTask for container startup
 * @see TestcontainersBuildService for runtime access
 */
sealed class ContainerDefinition : Serializable {

    abstract val name: String

    /**
     * Wait strategy for detecting when a container is ready to accept requests.
     *
     * The plugin supports three strategies:
     * - [ListeningPort]: Wait for exposed ports to start listening on TCP
     * - [Http]: Wait for an HTTP endpoint to respond with a specific status code
     * - [LogMessage]: Wait for a log message matching a regex pattern
     */
    sealed class WaitStrategy : Serializable {
        /** Wait for exposed ports to start listening for TCP connections. */
        object ListeningPort : WaitStrategy() {
            @Serial
            private const val serialVersionUID: Long = 8051104056948544114L
        }

        /**
         * Wait for an HTTP endpoint to respond.
         *
         * @param path The HTTP path to probe (e.g., "/health", "/actuator/health")
         * @param statusCode The expected HTTP status code (default 200)
         */
        data class Http(val path: String, val statusCode: Int) : WaitStrategy() {
            companion object {
                @Serial
                private const val serialVersionUID: Long = 4249900795560152804L
            }
        }

        /**
         * Wait for a log message matching a regex pattern.
         *
         * @param regex The regex pattern to match in container logs
         * @param times The number of times the pattern must appear before considering the container ready
         */
        data class LogMessage(val regex: String, val times: Int) : WaitStrategy() {
            companion object {
                @Serial
                private const val serialVersionUID: Long = 8713384407870223556L
            }
        }

        companion object {
            @Serial
            private const val serialVersionUID: Long = -1836453596581705910L
        }
    }

    /**
     * Binds a container port to a specific host port.
     *
     * @param hostPort The port on the host machine to bind to
     * @param containerPort The port inside the container the service listens on
     */
    data class PortMapping(
        val hostPort: Int,
        val containerPort: Int
    ) : Serializable {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 7294809365842840308L
        }
    }

    /**
     * JDBC database container definition.
     *
     * @param name The container identifier
     * @param databaseType The database type (e.g., "postgresql", "mysql")
     * @param dockerImageName The Docker image to use (or null for type-safe defaults)
     * @param databaseName The initial database name to create
     * @param username Database administrator username
     * @param password Database administrator password
     * @param reuse Whether to reuse the container across builds
     * @param portMappings Port bindings (host → container)
     */
    data class JdbcDatabase(
        override val name: String,
        val databaseType: String,
        val dockerImageName: SerializableDockerImageName?,
        val databaseName: String?,
        val username: String?,
        val password: String?,
        val reuse: Boolean = false,
        val portMappings: List<PortMapping> = emptyList()
    ) : ContainerDefinition() {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -8432040085634158273L
        }
    }

    /**
     * Volume mount binding a host path to a container path.
     *
     * @param hostPath The absolute path on the host system
     * @param containerPath The mount point inside the container
     * @param readOnly Whether the volume is mounted as read-only
     */
    data class VolumeMount(
        val hostPath: String,
        val containerPath: String,
        val readOnly: Boolean
    ) : Serializable {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 8746998764599472554L
        }
    }

    /**
     * Generic Docker container definition.
     *
     * @param name The container identifier
     * @param dockerImageName The Docker image to use
     * @param exposedPorts List of container ports to expose
     * @param env Environment variables to pass to the container
     * @param reuse Whether to reuse the container across builds
     * @param waitStrategy The strategy for detecting container readiness
     * @param startupTimeoutSeconds Maximum time to wait for container startup
     * @param volumeMounts Volume mounts to bind from the host
     */
    data class Generic(
        override val name: String,
        val dockerImageName: SerializableDockerImageName,
        val exposedPorts: List<Int>,
        val env: Map<String, String>,
        val reuse: Boolean = false,
        val waitStrategy: WaitStrategy = WaitStrategy.ListeningPort,
        val startupTimeoutSeconds: Long = 60,
        val volumeMounts: List<VolumeMount> = emptyList()
    ) : ContainerDefinition() {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 8813209377780308846L
        }
    }

    /**
     * Docker Compose stack definition.
     *
     * @param name The stack identifier
     * @param composeFilePath The absolute path to the docker-compose.yaml file
     * @param exposedServices Map of service name → exposed ports
     * @param startupTimeoutSeconds Maximum time to wait for services to start
     */
    data class Compose(
        override val name: String,
        val composeFilePath: String,
        val exposedServices: Map<String, List<Int>>, // serviceName -> list of ports
        val startupTimeoutSeconds: Long
    ) : ContainerDefinition() {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -378979055865126054L
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -6152446509977392623L
    }
}

/**
 * Serializable wrapper around Testcontainers [DockerImageName] that can cross serialization boundaries.
 *
 * Enables configuration cache and build cache support by making Docker image names serializable.
 *
 * @param image The full Docker image reference (e.g., "postgres:15-alpine")
 * @param compatibleSubstituteFor Optional Testcontainers compatibility label for image substitution
 */
class SerializableDockerImageName(
    val image: String,
    val compatibleSubstituteFor: String? = null
) : Serializable {
    /**
     * Converts to a Testcontainers [DockerImageName] for use at runtime.
     *
     * @return A [DockerImageName] with optional compatibility substitute applied
     */
    fun toDockerImageName(): DockerImageName {
        val parsed = DockerImageName.parse(image)
        return if (compatibleSubstituteFor != null) {
            parsed.asCompatibleSubstituteFor(compatibleSubstituteFor)
        } else {
            parsed
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -7586492537001558457L
    }
}
