package org.testcontainers.gradle

import java.io.Serializable

/**
 * Serializable definition of a single container to be managed by the plugin.
 */
sealed class ContainerDefinition : Serializable {

    abstract val name: String

    sealed class WaitStrategy : Serializable {
        object ListeningPort : WaitStrategy()
        data class Http(val path: String, val statusCode: Int) : WaitStrategy()
        data class LogMessage(val regex: String, val times: Int) : WaitStrategy()
    }

    data class JdbcDatabase(
        override val name: String,
        val databaseType: String,
        val image: String,
        val databaseName: String,
        val username: String,
        val password: String,
        val compatibleSubstituteFor: String? = null,
        val reuse: Boolean = false
    ) : ContainerDefinition()

    data class VolumeMount(
        val hostPath: String,
        val containerPath: String,
        val readOnly: Boolean
    ) : Serializable

    data class Generic(
        override val name: String,
        val image: String,
        val exposedPorts: List<Int>,
        val env: Map<String, String>,
        val reuse: Boolean = false,
        val waitStrategy: WaitStrategy = WaitStrategy.ListeningPort,
        val startupTimeoutSeconds: Long = 60,
        val volumeMounts: List<VolumeMount> = emptyList()
    ) : ContainerDefinition()

    data class Compose(
        override val name: String,
        val composeFilePath: String,
        val exposedServices: Map<String, List<Int>>, // serviceName -> list of ports
        val startupTimeoutSeconds: Long
    ) : ContainerDefinition()
}
