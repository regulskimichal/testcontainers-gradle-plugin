package org.testcontainers.gradle.spec

/**
 * Configuration specification for Docker Compose multi-container stacks.
 *
 * Used within [org.testcontainers.gradle.TestcontainersConfig.composeContainer] DSL blocks to expose services from
 * a Docker Compose file and configure their startup behavior.
 *
 * Example `compose.yaml`:
 * ```yaml
 * services:
 *   postgres:
 *     image: postgres:15
 *     environment:
 *       POSTGRES_PASSWORD: password
 *     ports:
 *       - "5432:5432"
 *   app:
 *     image: myapp:latest
 *     depends_on:
 *       - postgres
 *     ports:
 *       - "8080:8080"
 * ```
 *
 * Configuration in build.gradle.kts:
 * ```kotlin
 * composeContainer("stack", "compose.yaml") {
 *     service("postgres", 5432)
 *     service("app", 8080)
 *     startupTimeoutSeconds(30)
 * }
 * ```
 *
 * Access services at runtime:
 * ```kotlin
 * task("test") {
 *     dependsOn("startStackContainer")
 *     doLast {
 *         val compose = testcontainers.getContainer<ComposeContainer>("stack").get()
 *         val postgres = compose.getServiceHost("postgres", 5432)
 *         val postgresPort = compose.getServicePort("postgres", 5432)
 *     }
 * }
 * ```
 *
 * @see org.testcontainers.gradle.TestcontainersConfig.composeContainer for registration
 */
class ComposeContainerSpec {
    private val _exposedServices = mutableMapOf<String, List<Int>>()
    /**
     * Exposed service mapping (serviceName -> list of ports).
     *
     * This map contains all services declared via [service] method.
     * Read-only for consumers; add services via the [service] method.
     */
    val exposedServices: Map<String, List<Int>> get() = _exposedServices

    internal var startupTimeoutSeconds: Long = 60

    /**
     * Declares a service to expose from the Docker Compose stack.
     *
     * Each service must be defined in the Docker Compose file and needs its port(s) exposed
     * for the plugin to wait for readiness and for consumers to access the service.
     *
     * Examples:
     * - `service("postgres", 5432)` - Expose PostgreSQL port
     * - `service("app", 8080, 8443)` - Expose app with HTTP and HTTPS ports
     * - `service("kafka", 9092)` - Expose Kafka broker port
     *
     * **Note**: All services must be explicitly exposed. The plugin does not automatically
     * expose all services in the compose file — only those declared here.
     *
     * @param serviceName The name of the service as defined in the compose YAML file (matches `services.{serviceName}`)
     * @param ports Variable number of container ports on the service to expose and wait for
     *
     * @throws IllegalArgumentException if called during build with service names not in the compose file
     */
    fun service(serviceName: String, vararg ports: Int) {
        _exposedServices[serviceName] = ports.toList()
    }

    /**
     * The maximum time in seconds to wait for all compose services to start before timing out.
     *
     * This timeout applies to waiting for all exposed service ports to become available.
     * If any service doesn't become ready within this timeout, the start task fails.
     *
     * Adjust based on how long your services take to initialize. Complex stacks with
     * database migrations may need 60+ seconds.
     *
     * Default: 60 seconds
     *
     * @param seconds Timeout duration in seconds
     */
    fun startupTimeoutSeconds(seconds: Long) {
        this.startupTimeoutSeconds = seconds
    }

    internal fun validate(name: String) {
        require(_exposedServices.isNotEmpty()) { "Container '$name' error: at least one service must be exposed via service()." }
    }
}
