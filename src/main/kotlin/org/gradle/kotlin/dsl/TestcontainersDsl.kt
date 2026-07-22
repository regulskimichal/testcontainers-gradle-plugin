package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.testcontainers.gradle.TestcontainersExtension
import org.testcontainers.gradle.TestcontainersConfig

/**
 * Custom Kotlin DSL extension function that automatically overrides the default Gradle-generated
 * accessor. It redirects the configuration lambda receiver to TestcontainersConfig,
 * hiding runtime query/get methods from the testcontainers { ... } configuration block.
 */
fun Project.testcontainers(configure: TestcontainersConfig.() -> Unit) {
    val ext = extensions.getByType(TestcontainersExtension::class.java)
    ext.config.apply(configure)
}
