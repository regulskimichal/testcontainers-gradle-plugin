package org.testcontainers.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode.READ_ONLY
import org.testcontainers.containers.BindMode.READ_WRITE
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.JdbcDatabaseContainerProvider
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.gradle.ContainerDefinition.Compose
import org.testcontainers.gradle.ContainerDefinition.Generic
import org.testcontainers.gradle.ContainerDefinition.JdbcDatabase
import org.testcontainers.gradle.ContainerDefinition.WaitStrategy.Http
import org.testcontainers.gradle.ContainerDefinition.WaitStrategy.ListeningPort
import org.testcontainers.gradle.ContainerDefinition.WaitStrategy.LogMessage
import org.testcontainers.lifecycle.Startable
import java.io.File
import java.net.URLClassLoader
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Gradle [BuildService] that manages container lifecycles lazily on demand.
 *
 * This is the internal execution-time service responsible for:
 * - Creating and caching container instances based on [ContainerDefinition]
 * - Starting containers at build time
 * - Enforcing single-threaded access to prevent race conditions
 * - Automatically stopping containers when the build completes
 *
 * Access containers via the `testcontainers.service` provider:
 * ```kotlin
 * tasks.register("migrate") {
 *     dependsOn("startPostgresContainer")
 *     doLast {
 *         val postgres = testcontainers.service.map {
 *             it.getContainer<JdbcDatabaseContainer<*>>("postgres")
 *         }
 *         val url = postgres.get().jdbcUrl
 *     }
 * }
 * ```
 *
 * Enforces `maxParallelUsages = 1` to prevent concurrent access to shared containers,
 * ensuring that parallel test execution or code generation tasks don't interfere with
 * the shared container state.
 *
 * @see TestcontainersExtension.service for public access patterns
 * @see StartContainersTask for automatic startup
 * @see StopContainersTask for automatic shutdown
 */
abstract class TestcontainersBuildService : BuildService<TestcontainersBuildService.Parameters>, AutoCloseable {

    interface Parameters : BuildServiceParameters {
        /**
         * Dynamic classpath containing specialized Testcontainers database modules.
         *
         * Used to load JDBC container implementations via [ServiceLoader] when creating
         * database containers. Populated by the `testcontainersClasspath` configuration.
         */
        val classpathFiles: ConfigurableFileCollection
    }

    private val registeredContainers = mutableMapOf<String, Startable>()
    private val startedContainers = mutableSetOf<String>()

    /**
     * Marks a container as started in this build execution.
     * Called automatically by [StartContainersTask] after a successful container startup.
     *
     * Internal API - do not call from user build scripts.
     */
    internal fun markContainerStarted(name: String) = synchronized(this) { startedContainers.add(name) }

    /**
     * Returns `true` if the named container was actually started (not skipped) in this build.
     *
     * Use this to conditionally execute downstream tasks that depend on a fresh container:
     * ```kotlin
     * task("migrate") {
     *     val service = testcontainers.service
     *     onlyIf { service.wasContainerStarted("postgres") }
     * }
     * ```
     *
     * @param name The container name as specified in [TestcontainersConfig.jdbcContainer], etc.
     * @return `true` if the container was explicitly started; `false` if the start task was UP-TO-DATE
     *
     * @see StartContainersTask for when containers are marked as started
     */
    fun wasContainerStarted(name: String): Boolean = synchronized(this) { startedContainers.contains(name) }

    private val classLoader: ClassLoader by lazy {
        val urls = parameters.classpathFiles.files.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, javaClass.classLoader)
    }

    @PublishedApi
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
        classLoader: ClassLoader,
    ): T {
        return when (containerDefinition) {
            is JdbcDatabase -> createJdbcDatabaseContainer(containerDefinition, classLoader)
            is Generic -> createGenericContainer(containerDefinition)
            is Compose -> createComposeContainer(containerDefinition)
        } as T
    }

    private fun createJdbcDatabaseContainer(
        containerDefinition: JdbcDatabase,
        classLoader: ClassLoader,
    ): JdbcDatabaseContainer<*> {
        val serviceLoader = ServiceLoader.load(JdbcDatabaseContainerProvider::class.java, classLoader)
        val containerProvider = serviceLoader.firstOrNull {
            it.supports(containerDefinition.databaseType)
        } ?: throw MissingJdbcDatabaseContainerProviderException(containerDefinition.databaseType)

        val container = containerDefinition.dockerImageName?.let {
            containerProvider.newInstance(it.toDockerImageName().versionPart)
        } ?: containerProvider.newInstance()

        containerDefinition.dockerImageName?.let { customImage ->
            container.setImage(CompletableFuture.completedFuture(customImage.toDockerImageName().asCanonicalNameString()))
        }

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

        // Apply fixed port mappings: register the container port via withExposedPorts
        // (additive — preserves ports the JDBC container already declared internally)
        // and pin the host side via setPortBindings.
        if (containerDefinition.portMappings.isNotEmpty()) {
            container.setPortBindings(containerDefinition.portMappings.map { "${it.hostPort}:${it.containerPort}" })
        }

        // Attach slf4j logger
        val containerLogger = LoggerFactory.getLogger("testcontainers.${containerDefinition.name}")
        container.withLogConsumer(Slf4jLogConsumer(containerLogger).withPrefix(containerDefinition.name))

        return container
    }

    private fun createGenericContainer(containerDefinition: Generic): GenericContainer<*> {
        val container = GenericContainer(containerDefinition.dockerImageName.toDockerImageName())
        if (containerDefinition.exposedPorts.isNotEmpty()) {
            containerDefinition.exposedPorts.forEach { port ->
                container.withExposedPorts(port)
            }
        }
        if (containerDefinition.env.isNotEmpty()) {
            container.withEnv(containerDefinition.env)
        }

        container.withReuse(containerDefinition.reuse)

        // Set wait strategy
        val tcWait = with(containerDefinition.waitStrategy) {
            when (this) {
                is ListeningPort -> Wait.forListeningPort()
                is Http -> Wait.forHttp(this.path).forStatusCode(this.statusCode)
                is LogMessage -> Wait.forLogMessage(this.regex, this.times)
            }
        }

        container.waitingFor(tcWait.withStartupTimeout(Duration.ofSeconds(containerDefinition.startupTimeoutSeconds)))

        // Bind volumes
        for ((hostPath, containerPath, readOnly) in containerDefinition.volumeMounts) {
            val bindMode = if (readOnly) READ_ONLY else READ_WRITE
            container.withFileSystemBind(hostPath, containerPath, bindMode)
        }

        // Attach slf4j logger
        val containerLogger = LoggerFactory.getLogger("testcontainers.${containerDefinition.name}")
        container.withLogConsumer(Slf4jLogConsumer(containerLogger).withPrefix(containerDefinition.name))

        return container
    }

    private fun createComposeContainer(containerDefinition: Compose): ComposeContainer {
        val container = ComposeContainer(File(containerDefinition.composeFilePath)).withRemoveVolumes(true)

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

    /**
     * Stops all running containers and cleans up resources.
     *
     * Called automatically by Gradle at the end of the build. Ensures that even if errors occur,
     * all containers are properly shut down to prevent resource leaks or port conflicts on subsequent builds.
     *
     * Individual container stop failures are logged but do not abort the shutdown process.
     *
     * Internal API - do not call from user build scripts.
     */
    @Synchronized
    override fun close() {
        registeredContainers.values.forEach {
            runCatching { it.stop() }
        }
    }
}
