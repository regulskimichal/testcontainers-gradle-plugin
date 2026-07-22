package org.testcontainers.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.slf4j.LoggerFactory
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.JdbcDatabaseContainerProvider
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URLClassLoader
import java.time.Duration
import java.util.*

/**
 * Gradle [BuildService] that manages container lifecycles lazily on demand.
 */
abstract class TestcontainersBuildService
    : BuildService<TestcontainersBuildService.Parameters>, AutoCloseable {

    interface Parameters : BuildServiceParameters {
        /** Dynamic classpath containing specialized database modules. */
        val classpathFiles: ConfigurableFileCollection
    }

    private val registeredContainers = mutableMapOf<String, Startable>()

    private val classLoader: ClassLoader by lazy {
        val urls = parameters.classpathFiles.files.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, javaClass.classLoader)
    }

    /**
     * Lazily get or start the container instance.
     */
    @Suppress("UNCHECKED_CAST")
    @Synchronized
    internal fun <T : Startable> getContainer(definition: ContainerDefinition): T {
        return registeredContainers.getOrPut(definition.name) {
            createContainer<T>(definition, classLoader)
        } as T
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Startable> createContainer(
        containerDefinition: ContainerDefinition,
        classLoader: ClassLoader
    ): T {
        return when (containerDefinition) {
            is ContainerDefinition.JdbcDatabase -> createJdbcDatabaseContainer(containerDefinition, classLoader)
            is ContainerDefinition.Generic -> createGenericContainer(containerDefinition)
            is ContainerDefinition.Compose -> createComposeContainer(containerDefinition)
        } as T
    }

    private fun createJdbcDatabaseContainer(
        containerDefinition: ContainerDefinition.JdbcDatabase,
        classLoader: ClassLoader
    ): JdbcDatabaseContainer<*> {
        val serviceLoader = ServiceLoader.load(JdbcDatabaseContainerProvider::class.java, classLoader)
        val containerProvider = serviceLoader.firstOrNull {
            it.supports(containerDefinition.databaseType)
        }
            ?: error("No JdbcDatabaseContainerProvider found for database type '${containerDefinition.databaseType}'. Make sure to add the corresponding dependency to 'testcontainersClasspath'.")

        val defaultInstance = containerProvider.newInstance()
        val containerClazz = defaultInstance.javaClass
        val defaultImageName = defaultInstance.dockerImageName

        val image = containerDefinition.dockerImageName?.toDockerImageName() ?: DockerImageName.parse(defaultImageName)

        val constructor = containerClazz.getConstructor(DockerImageName::class.java)
        val container = constructor.newInstance(image) as JdbcDatabaseContainer<*>

        if (containerDefinition.databaseName != null) {
            container.withDatabaseName(containerDefinition.databaseName)
        }
        if (containerDefinition.username != null) {
            container.withUsername(containerDefinition.username)
        }
        if (containerDefinition.password != null) {
            container.withPassword(containerDefinition.password)
        }
        container.withReuse(containerDefinition.reuse)

        // Attach slf4j logger
        val containerLogger = LoggerFactory.getLogger("testcontainers.${containerDefinition.name}")
        container.withLogConsumer(Slf4jLogConsumer(containerLogger).withPrefix(containerDefinition.name))

        return container
    }

    private fun createGenericContainer(containerDefinition: ContainerDefinition.Generic): GenericContainer<*> {
        val container = GenericContainer(containerDefinition.dockerImageName.toDockerImageName())
        if (containerDefinition.exposedPorts.isNotEmpty()) {
            container.withExposedPorts(*containerDefinition.exposedPorts.toTypedArray())
        }
        if (containerDefinition.env.isNotEmpty()) {
            container.withEnv(containerDefinition.env)
        }

        container.withReuse(containerDefinition.reuse)

        // Set wait strategy
        val tcWait = when (containerDefinition.waitStrategy) {
            is ContainerDefinition.WaitStrategy.ListeningPort -> Wait.forListeningPort()
            is ContainerDefinition.WaitStrategy.Http -> Wait.forHttp(containerDefinition.waitStrategy.path)
                .forStatusCode(containerDefinition.waitStrategy.statusCode)
            is ContainerDefinition.WaitStrategy.LogMessage -> Wait.forLogMessage(
                containerDefinition.waitStrategy.regex,
                containerDefinition.waitStrategy.times
            )
        }.withStartupTimeout(Duration.ofSeconds(containerDefinition.startupTimeoutSeconds))
        container.waitingFor(tcWait)

        // Bind volumes
        for (mount in containerDefinition.volumeMounts) {
            val bindMode = if (mount.readOnly) org.testcontainers.containers.BindMode.READ_ONLY else org.testcontainers.containers.BindMode.READ_WRITE
            container.withFileSystemBind(mount.hostPath, mount.containerPath, bindMode)
        }

        // Attach slf4j logger
        val containerLogger = LoggerFactory.getLogger("testcontainers.${containerDefinition.name}")
        container.withLogConsumer(Slf4jLogConsumer(containerLogger).withPrefix(containerDefinition.name))

        return container
    }

    private fun createComposeContainer(containerDefinition: ContainerDefinition.Compose): ComposeContainer {
        val container = ComposeContainer(File(containerDefinition.composeFilePath))
            .withRemoveVolumes(true)

        val containerLogger = LoggerFactory.getLogger("testcontainers.${containerDefinition.name}")
        val logConsumer = Slf4jLogConsumer(containerLogger)

        for ((serviceName, ports) in containerDefinition.exposedServices) {
            container.withLogConsumer(serviceName, logConsumer)
            for (port in ports) {
                container.withExposedService(
                    serviceName,
                    port,
                    Wait.forListeningPort()
                        .withStartupTimeout(Duration.ofSeconds(containerDefinition.startupTimeoutSeconds)),
                )
            }
        }

        return container
    }

    /** Called by Gradle automatically at build end — stops all containers. */
    @Synchronized
    override fun close() {
        registeredContainers.values.forEach {
            runCatching { it.stop() }
        }
    }
}
