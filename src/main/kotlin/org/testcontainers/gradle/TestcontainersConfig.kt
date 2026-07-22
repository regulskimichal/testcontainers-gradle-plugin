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

    fun jdbcContainer(
        name: String,
        databaseType: String,
        configure: JdbcContainerSpec.() -> Unit
    ) {
        val spec = JdbcContainerSpec().apply(configure)
        spec.validate(name)
        definitions[name] = ContainerDefinition.JdbcDatabase(
            name = name,
            databaseType = databaseType,
            image = spec.image,
            databaseName = spec.databaseName,
            username = spec.username,
            password = spec.password,
            compatibleSubstituteFor = spec.compatibleSubstituteFor,
            reuse = spec.reuse
        )
    }

    fun jdbcContainer(
        name: String,
        databaseType: DatabaseType,
        configure: JdbcContainerSpec.() -> Unit
    ) {
        jdbcContainer(name, databaseType.id, configure)
    }

    fun genericContainer(name: String, configure: GenericContainerSpec.() -> Unit) {
        val spec = GenericContainerSpec().apply(configure)
        spec.validate(name)
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
        definitions[name] = ContainerDefinition.Generic(
            name = name,
            image = spec.image,
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
