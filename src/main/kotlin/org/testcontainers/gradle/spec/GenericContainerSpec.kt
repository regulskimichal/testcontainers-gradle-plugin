package org.testcontainers.gradle.spec

import org.testcontainers.gradle.SerializableDockerImageName
import java.io.Serializable

class GenericContainerSpec {
    internal var dockerImageName: SerializableDockerImageName? = null
    internal var exposedPorts: List<Int> = emptyList()
    internal var env: Map<String, String> = emptyMap()
    internal var reuse: Boolean = false
    internal var startupTimeoutSeconds: Long = 60
    internal var waitStrategy: WaitStrategySpec = WaitStrategySpec.ListeningPort
    internal val volumeMounts = mutableListOf<VolumeMountSpec>()

    /**
     * Sets the Docker image name.
     */
    fun image(name: String, compatibleSubstituteFor: String? = null) {
        this.dockerImageName = SerializableDockerImageName(name, compatibleSubstituteFor)
    }

    /**
     * Sets the list of ports to expose from the container (e.g. `exposedPorts(6379, 80)`).
     */
    fun exposedPorts(vararg ports: Int) {
        this.exposedPorts = ports.toList()
    }

    /**
     * Sets the list of ports to expose from the container as a List.
     */
    fun exposedPorts(ports: List<Int>) {
        this.exposedPorts = ports
    }

    /**
     * Sets the environment variables to pass to the container.
     */
    fun env(env: Map<String, String>) {
        this.env = env
    }

    /**
     * Sets the environment variables to pass to the container using Pair arguments.
     */
    fun env(vararg pairs: Pair<String, String>) {
        this.env = pairs.toMap()
    }

    /**
     * Whether to keep the container instance running across build executions.
     */
    fun reuse(reuse: Boolean) {
        this.reuse = reuse
    }

    /**
     * The maximum time in seconds to wait for the container to start before failing.
     */
    fun startupTimeoutSeconds(seconds: Long) {
        this.startupTimeoutSeconds = seconds
    }

    /** Wait for the container's exposed ports to start listening for TCP connections. */
    fun waitPort() {
        waitStrategy = WaitStrategySpec.ListeningPort
    }

    /** Wait for an HTTP endpoint on the container to return a specific status code. */
    fun waitHttp(path: String, statusCode: Int = 200) {
        waitStrategy = WaitStrategySpec.Http(path, statusCode)
    }

    /** Wait for a specific log message matching a regex to be outputted by the container. */
    fun waitLog(regex: String, times: Int = 1) {
        waitStrategy = WaitStrategySpec.LogMessage(regex, times)
    }

    /**
     * Mounts a local file or directory into the container.
     *
     * @param hostPath The local source path (can be a [File], Gradle directory/file, or path string).
     * @param containerPath The destination path inside the container.
     * @param readOnly If `true`, the volume will be mounted as read-only.
     */
    fun mountVolume(hostPath: Any, containerPath: String, readOnly: Boolean = false) {
        volumeMounts.add(VolumeMountSpec(hostPath, containerPath, readOnly))
    }

    data class VolumeMountSpec(
        val hostPath: Any,
        val containerPath: String,
        val readOnly: Boolean
    ) : Serializable

    sealed interface WaitStrategySpec : Serializable {
        object ListeningPort : WaitStrategySpec
        data class Http(val path: String, val statusCode: Int) : WaitStrategySpec
        data class LogMessage(val regex: String, val times: Int) : WaitStrategySpec
    }

    internal fun validate() {
        requireNotNull(dockerImageName) { "Generic container requires DockerImageName" }
    }
}
