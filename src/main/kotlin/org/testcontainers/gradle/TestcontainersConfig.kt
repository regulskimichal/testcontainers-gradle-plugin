package org.testcontainers.gradle

import org.gradle.api.file.ProjectLayout
import org.testcontainers.gradle.spec.ComposeContainerSpec
import org.testcontainers.gradle.spec.GenericContainerSpec
import org.testcontainers.gradle.spec.JdbcContainerSpec
import java.io.File

/**
 * Base configuration class for registering container definitions.
 *
 * This is the internal implementation of the `testcontainers { }` DSL and provides
 * methods for registering different container types:
 * - JDBC databases: [jdbcContainer] for type-safe database registration
 * - Generic containers: [genericContainer] for custom Docker images
 * - Docker Compose: [composeContainer] for multi-container setups
 *
 * All registered containers are started via auto-generated `start*Container` tasks
 * and stopped via auto-generated `stop*Container` tasks.
 *
 * @see TestcontainersExtension for the public DSL
 * @see ContainerDefinition for the serializable representation
 */
open class TestcontainersConfig(
    private val layout: ProjectLayout
) {
    @PublishedApi
    internal val definitions = mutableMapOf<String, ContainerDefinition>()

    /**
     * Registers a JDBC database container from a database type string identifier.
     *
     * This method supports arbitrary database type strings and resolves them to canonical Docker image names
     * (e.g., "postgresql" → "postgres:latest"). Use the type-safe overload [jdbcContainer(String, DatabaseType, Function)]
     * to prevent configuration errors.
     *
     * @param name The unique container identifier used for task names (e.g., "postgres" generates
     *             `startPostgresContainer` and `stopPostgresContainer` tasks).
     * @param databaseType The database type string (e.g., "postgresql", "mysql", "oracle").
     *                      Resolves to canonical image names via [resolveCanonicalImageName].
     * @param configure The configuration lambda for setting database credentials, ports, and image details.
     *
     * @throws IllegalArgumentException if the databaseType cannot be resolved to a canonical image.
     *
     * @see DatabaseType for available type-safe enum constants
     * @see resolveCanonicalImageName for supported type strings
     */
    fun jdbcContainer(
        name: String,
        databaseType: String,
        configure: JdbcContainerSpec.() -> Unit
    ) {
        val defaultSub = resolveCanonicalImageName(databaseType)
        val spec = JdbcContainerSpec(defaultSub).apply(configure)
        definitions[name] = ContainerDefinition.JdbcDatabase(
            name = name,
            databaseType = databaseType,
            dockerImageName = spec.dockerImageName,
            databaseName = spec.databaseName,
            username = spec.username,
            password = spec.password,
            reuse = spec.reuse,
            portMappings = spec.portMappings.map {
                ContainerDefinition.PortMapping(it.hostPort, it.containerPort)
            }
        )
    }

    /**
     * Registers a JDBC database container using a type-safe [DatabaseType] enum.
     *
     * **Preferred** over the string-based version to prevent typos and provide IDE autocomplete.
     * Type-safe enums ensure you're using a supported database type with the correct Docker image.
     *
     * Example:
     * ```kotlin
     * jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
     *     databaseName("testdb")
     *     username("user")
     *     password("pass")
     *     portMapping(5432)
     * }
     * ```
     *
     * @param name The unique container identifier used for task names (e.g., "postgres" generates
     *             `startPostgresContainer` and `stopPostgresContainer` tasks).
     * @param databaseType The [DatabaseType] enum constant (e.g., [DatabaseType.POSTGRESQL], [DatabaseType.MYSQL]).
     * @param configure The configuration lambda on [JdbcContainerSpec] for credentials, ports, and image overrides.
     *
     * @see DatabaseType for all supported databases
     * @see JdbcContainerSpec for available configuration options
     */
    fun jdbcContainer(
        name: String,
        databaseType: DatabaseType,
        configure: JdbcContainerSpec.() -> Unit
    ) {
        jdbcContainer(name, databaseType.id, configure)
    }

    /**
     * Registers a generic Docker container for any image not covered by specialized container types.
     *
     * Useful for services like Redis, Kafka, PostgreSQL (with custom image), or any public Docker image.
     * Supports environment variables, port mappings, volume mounts, and wait strategies.
     *
     * Example:
     * ```kotlin
     * genericContainer("redis") {
     *     image("redis:7-alpine")
     *     exposedPorts(6379)
     *     waitPort()
     * }
     *
     * genericContainer("dynamodb") {
     *     image("amazon/dynamodb-local:latest")
     *     exposedPorts(8000)
     *     env("AWS_ACCESS_KEY_ID" to "testing", "AWS_SECRET_ACCESS_KEY" to "testing")
     *     waitHttp("/", 400)  // DynamoDB returns 400 on /
     * }
     * ```
     *
     * @param name The unique container identifier used for task names (e.g., "redis" generates
     *             `startRedisContainer` and `stopRedisContainer` tasks).
     * @param configure The configuration lambda on [GenericContainerSpec] for image, ports, environment, and wait strategy.
     *
     * @throws IllegalArgumentException if no image is specified in the configuration.
     *
     * @see GenericContainerSpec for available configuration options
     */
    fun genericContainer(name: String, configure: GenericContainerSpec.() -> Unit) {
        val spec = GenericContainerSpec().apply(configure)
        val resolvedMounts = spec.volumeMounts.map { mountSpec ->
            val hostPath = mountSpec.hostPath
            val hostAbsolutePath = when (hostPath) {
                is File -> hostPath.absolutePath
                is org.gradle.api.file.Directory -> hostPath.asFile.absolutePath
                is org.gradle.api.file.RegularFile -> hostPath.asFile.absolutePath
                else -> {
                    val strPath = hostPath.toString()
                    val f = File(strPath)
                    if (f.isAbsolute) f.absolutePath
                    else layout.projectDirectory.file(strPath).asFile.absolutePath
                }
            }
            ContainerDefinition.VolumeMount(
                hostPath = hostAbsolutePath,
                containerPath = mountSpec.containerPath,
                readOnly = mountSpec.readOnly
            )
        }
        spec.validate()
        definitions[name] = ContainerDefinition.Generic(
            name = name,
            dockerImageName = requireNotNull(spec.dockerImageName),
            exposedPorts = spec.exposedPorts,
            env = spec.env,
            reuse = spec.reuse,
            waitStrategy = when (spec.waitStrategy) {
                is GenericContainerSpec.WaitStrategySpec.ListeningPort -> ContainerDefinition.WaitStrategy.ListeningPort
                is GenericContainerSpec.WaitStrategySpec.Http -> ContainerDefinition.WaitStrategy.Http(
                    (spec.waitStrategy as GenericContainerSpec.WaitStrategySpec.Http).path,
                    (spec.waitStrategy as GenericContainerSpec.WaitStrategySpec.Http).statusCode
                )
                is GenericContainerSpec.WaitStrategySpec.LogMessage -> ContainerDefinition.WaitStrategy.LogMessage(
                    (spec.waitStrategy as GenericContainerSpec.WaitStrategySpec.LogMessage).regex,
                    (spec.waitStrategy as GenericContainerSpec.WaitStrategySpec.LogMessage).times
                )
            },
            startupTimeoutSeconds = spec.startupTimeoutSeconds,
            volumeMounts = resolvedMounts
        )
    }

    /**
     * Registers a Docker Compose multi-container setup.
     *
     * Manages one or more services defined in a Docker Compose file. The plugin automatically
     * starts the entire stack before dependent tasks and stops all services after the build completes.
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
     *   redis:
     *     image: redis:7-alpine
     *     ports:
     *       - "6379:6379"
     * ```
     *
     * Configuration in build.gradle.kts:
     * ```kotlin
     * composeContainer("stack", "compose.yaml") {
     *     service("postgres", 5432)
     *     service("redis", 6379)
     *     startupTimeoutSeconds(30)
     * }
     * ```
     *
     * @param name The unique stack identifier (e.g., "stack" generates `startStackContainer` and `stopStackContainer`).
     * @param filePath The path to the Docker Compose file (can be a [File], path string, or Gradle file).
     *                  Relative paths are resolved from the project directory.
     * @param configure The configuration lambda on [ComposeContainerSpec] for exposing services and setting timeouts.
     *
     * @throws IllegalArgumentException if no services are exposed via [ComposeContainerSpec.service].
     *
     * @see ComposeContainerSpec for service exposure and timeout configuration
     */
    fun composeContainer(
        name: String,
        filePath: Any,
        configure: ComposeContainerSpec.() -> Unit = {}
    ) {
        val spec = ComposeContainerSpec().apply(configure)
        spec.validate(name)
        val absolutePath = when (filePath) {
            is File -> filePath.absolutePath
            else -> layout.projectDirectory.file(filePath.toString()).asFile.absolutePath
        }
        definitions[name] = ContainerDefinition.Compose(
            name = name,
            composeFilePath = absolutePath,
            exposedServices = spec.exposedServices,
            startupTimeoutSeconds = spec.startupTimeoutSeconds
        )
    }

    internal fun resolveDefinitions(): List<ContainerDefinition> {
        return definitions.values.toList()
    }
}
