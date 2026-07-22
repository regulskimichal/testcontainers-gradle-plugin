package org.testcontainers.gradle.spec

import org.testcontainers.gradle.SerializableDockerImageName

class JdbcContainerSpec(internal val defaultCompatibleSubstitute: String? = null) {
    internal var dockerImageName: SerializableDockerImageName? = null
    internal var databaseName: String? = null
    internal var username: String? = null
    internal var password: String? = null
    internal var reuse: Boolean = false

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
}
