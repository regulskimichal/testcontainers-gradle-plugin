package org.testcontainers.gradle.spec

class ComposeContainerSpec {
    private val _exposedServices = mutableMapOf<String, List<Int>>()
    /** Exposed service mapping (serviceName -> list of ports). */
    val exposedServices: Map<String, List<Int>> get() = _exposedServices

    internal var startupTimeoutSeconds: Long = 60

    /**
     * Declares a service to expose from the Docker Compose stack.
     *
     * @param serviceName The name of the service as defined in the compose YAML file.
     * @param ports The list of ports to expose and wait for on the service container.
     */
    fun service(serviceName: String, vararg ports: Int) {
        _exposedServices[serviceName] = ports.toList()
    }

    /**
     * The maximum time in seconds to wait for compose services to start before failing.
     */
    fun startupTimeoutSeconds(seconds: Long) {
        this.startupTimeoutSeconds = seconds
    }

    internal fun validate(name: String) {
        require(_exposedServices.isNotEmpty()) { "Container '$name' error: at least one service must be exposed via service()." }
    }
}
