package org.testcontainers.gradle.internal

import org.slf4j.LoggerFactory
import org.testcontainers.DockerClientFactory
import org.testcontainers.dockerclient.DockerClientProviderStrategy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Testcontainers' internal fail-fast circuit breaker state.
 *
 * Testcontainers caches Docker discovery failures in static fields
 * (`DockerClientProviderStrategy.FAIL_FAST_ALWAYS` and `DockerClientFactory.instance`),
 * permanently locking the long-lived Gradle Daemon into a failed state when started without Docker.
 *
 * This resets that state so subsequent task executions can retry connecting to Docker.
 */
internal object TestcontainersCircuitBreaker {

    private val logger = LoggerFactory.getLogger(TestcontainersCircuitBreaker::class.java)

    internal val failFast: AtomicBoolean? = runCatching {
        DockerClientProviderStrategy::class.java.getDeclaredField("FAIL_FAST_ALWAYS").run {
            isAccessible = true
            get(null) as? AtomicBoolean
        }
    }.onFailure { e ->
        logger.debug("Could not resolve DockerClientProviderStrategy.FAIL_FAST_ALWAYS: {}", e.message)
    }.getOrNull()

    internal val factoryInstanceField = runCatching {
        DockerClientFactory::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
    }.onFailure { e ->
        logger.debug("Could not resolve DockerClientFactory.instance: {}", e.message)
    }.getOrNull()

    fun reset() {
        failFast?.set(false)
        runCatching {
            factoryInstanceField?.set(null, null)
        }.onFailure { e ->
            logger.debug("Could not reset DockerClientFactory.instance: {}", e.message)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun <T> withReset(action: () -> T): T {
        reset()
        return try {
            action()
        } catch (e: Throwable) {
            reset()
            throw e
        }
    }
}
