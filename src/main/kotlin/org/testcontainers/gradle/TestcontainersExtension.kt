package org.testcontainers.gradle

import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import javax.inject.Inject

/**
 * Public extension for configuring container strategies.
 * Exposed as `testcontainers` in build scripts.
 */
abstract class TestcontainersExtension @Inject constructor(
    layout: ProjectLayout
) : TestcontainersConfig(layout) {

    internal val config: TestcontainersConfig get() = this

    /** Service provider representing the running container lifecycle. */
    lateinit var service: Provider<out BuildService<*>>
}
