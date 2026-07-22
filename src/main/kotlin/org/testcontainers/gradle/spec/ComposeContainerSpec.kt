package org.testcontainers.gradle.spec

class ComposeContainerSpec {
    private val _exposedServices = mutableMapOf<String, List<Int>>()
    val exposedServices: Map<String, List<Int>> get() = _exposedServices

    var startupTimeoutSeconds: Long = 60

    fun service(serviceName: String, vararg ports: Int) {
        _exposedServices[serviceName] = ports.toList()
    }

    internal fun validate(name: String) {
        require(_exposedServices.isNotEmpty()) { "Container '$name' error: at least one service must be exposed via exposeService()." }
    }
}
