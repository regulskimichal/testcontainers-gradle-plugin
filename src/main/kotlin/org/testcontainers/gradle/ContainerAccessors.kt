package org.testcontainers.gradle

import org.gradle.api.provider.Provider
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.JdbcDatabaseContainer

// Extension helper functions on TestcontainersExtension for lazy execution-time retrieval
fun TestcontainersExtension.getJdbcDatabaseContainer(name: String): Provider<JdbcDatabaseContainer<*>> {
    val definition = this.config.definitions[name] ?: error("No container registered with name '$name'.")
    return this.service.map { service ->
        (service as TestcontainersBuildService).getContainer<JdbcDatabaseContainer<*>>(definition)
    }
}

fun TestcontainersExtension.getGenericContainer(name: String): Provider<GenericContainer<*>> {
    val definition = this.config.definitions[name] ?: error("No container registered with name '$name'.")
    return this.service.map { service ->
        (service as TestcontainersBuildService).getContainer<GenericContainer<*>>(definition)
    }
}

fun TestcontainersExtension.getComposeContainer(name: String): Provider<ComposeContainer> {
    val definition = this.config.definitions[name] ?: error("No container registered with name '$name'.")
    return this.service.map { service ->
        (service as TestcontainersBuildService).getContainer<ComposeContainer>(definition)
    }
}
