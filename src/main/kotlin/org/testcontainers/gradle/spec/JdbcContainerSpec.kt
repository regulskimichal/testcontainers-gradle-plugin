package org.testcontainers.gradle.spec

import org.testcontainers.gradle.SerializableDockerImageName
import java.io.Serializable

class JdbcContainerSpec(internal val defaultCompatibleSubstitute: String? = null) {
    internal var dockerImageName: SerializableDockerImageName? = null
    internal var databaseName: String? = null
    internal var username: String? = null
    internal var password: String? = null
    internal var reuse: Boolean = false
    internal val portMappings: MutableList<PortMappingSpec> = mutableListOf()

    /**
     * Sets the Docker image name and optionally its compatible substitute.
     */
    fun image(name: String) {
        this.dockerImageName = SerializableDockerImageName(name, defaultCompatibleSubstitute)
    }

    /**
     * Sets the name of the database to create inside the container.
     */
    fun databaseName(name: String) {
        this.databaseName = name
    }

    /**
     * Sets the database administrator username.
     */
    fun username(name: String) {
        this.username = name
    }

    /**
     * Sets the database administrator password.
     */
    fun password(name: String) {
        this.password = name
    }

    /**
     * Whether to keep the container instance running across build executions.
     */
    fun reuse(reuse: Boolean) {
        this.reuse = reuse
    }

    /**
     * Binds a container port to a fixed host port.
     *
     * @param containerPort The port the database process listens on inside the container.
     * @param hostPort      The port to bind on the host machine. Defaults to the same
     *                      value as [containerPort].
     */
    fun portMapping(containerPort: Int, hostPort: Int = containerPort) {
        portMappings.add(PortMappingSpec(hostPort, containerPort))
    }

    data class PortMappingSpec(
        val hostPort: Int,
        val containerPort: Int
    ) : Serializable
}
