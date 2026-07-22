package org.testcontainers.gradle

import org.gradle.api.file.ProjectLayout
import org.testcontainers.gradle.spec.ComposeContainerSpec
import org.testcontainers.gradle.spec.GenericContainerSpec
import org.testcontainers.gradle.spec.JdbcContainerSpec
import java.io.File

open class TestcontainersConfig(
    private val layout: ProjectLayout
) {
    internal val definitions = mutableMapOf<String, ContainerDefinition>()

    /**
     * Registers a JDBC database container.
     *
     * @param name The unique name of the container definition.
     * @param databaseType The string identifier of the database (e.g., "postgresql", "mysql"). See more at: https://java.testcontainers.org/modules/databases/jdbc/
     * @param configure The configuration block for setting container properties on [JdbcContainerSpec].
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
            reuse = spec.reuse
        )
    }

    /**
     * Registers a JDBC database container using a type-safe [DatabaseType] enum.
     * **Preferred** over the string-based version to prevent configuration typos.
     *
     * @param name The unique name of the container definition.
     * @param databaseType The supported [DatabaseType] enum (e.g., [DatabaseType.POSTGRESQL]).
     * @param configure The configuration block for setting container properties on [JdbcContainerSpec].
     */
    fun jdbcContainer(
        name: String,
        databaseType: DatabaseType,
        configure: JdbcContainerSpec.() -> Unit
    ) {
        jdbcContainer(name, databaseType.id, configure)
    }

    /**
     * Registers a generic single-container definition.
     *
     * @param name The unique name of the container definition.
     * @param configure The configuration block for setting generic container properties on [GenericContainerSpec].
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
     * @param name The unique name of the compose stack definition.
     * @param filePath The path to the Docker Compose configuration file (can be a [File], path string, etc.).
     * @param configure The configuration block for exposing compose services on [ComposeContainerSpec].
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
