package org.testcontainers.gradle.dsl

/**
 * DSL marker annotation for Testcontainers Gradle Plugin DSL elements.
 * Prevents scope leakage between nested DSL blocks.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestcontainersDslMarker
