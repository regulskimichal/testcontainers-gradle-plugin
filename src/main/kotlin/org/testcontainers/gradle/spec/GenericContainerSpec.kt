package org.testcontainers.gradle.spec

import java.io.Serializable

class GenericContainerSpec {
    var image: String = ""
    var exposedPorts: List<Int> = emptyList()
    var env: Map<String, String> = emptyMap()
    var reuse: Boolean = false
    var startupTimeoutSeconds: Long = 60

    internal var waitStrategy: WaitStrategySpec = WaitStrategySpec.ListeningPort

    fun waitPort() {
        waitStrategy = WaitStrategySpec.ListeningPort
    }

    fun waitHttp(path: String, statusCode: Int = 200) {
        waitStrategy = WaitStrategySpec.Http(path, statusCode)
    }

    fun waitLog(regex: String, times: Int = 1) {
        waitStrategy = WaitStrategySpec.LogMessage(regex, times)
    }

    internal val volumeMounts = mutableListOf<VolumeMountSpec>()

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

    internal fun validate(name: String) {
        require(image.isNotEmpty()) { "Container '$name' error: 'image' must be explicitly specified." }
    }
}
