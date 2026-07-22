# Testcontainers Gradle Plugin

A minimal, framework-agnostic Gradle plugin that manages container lifecycles for build-time tasks (such as code generation, database migration, or schema inspection) using Testcontainers.

---

## Features
* **Build-Time Lifecycles**: Automatically registers `start<Name>Container` and `stop<Name>Container` tasks for every container defined.
* **Isolated Environment**: Uses a Gradle `BuildService` configured with `maxParallelUsages = 1` to prevent parallel tasks from causing race conditions on shared container instances.
* **Flexible DSL**: Directly configure JDBC-compatible databases, generic containers (with custom volume mounts and wait strategies), or Docker Compose setups.
* **Reflection-Safe Classloading**: Allows injecting custom database drivers and Testcontainers modules via the dedicated `testcontainersClasspath` configuration.

---

## Getting Started

### 1. Apply the Plugin
Add the plugin to your `settings.gradle.kts` (or `build.gradle.kts` if published):

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
```

Then apply the plugin in your `build.gradle.kts` (assuming it is resolved from a plugin portal):

```kotlin
plugins {
    id("org.testcontainers") version "<VERSION>"
}
```

### 2. Configure Your Containers
Add container configurations inside the `testcontainers` extension block:

```kotlin
import org.testcontainers.gradle.DatabaseType

testcontainers {
    // 1. JDBC Database Container (Postgres example)
    // Preferred: use DatabaseType enum for type-safety
    jdbcContainer("db", DatabaseType.POSTGRESQL) {
        image("postgres:18-alpine")
    }

    // 2. Generic Container (Redis example)
    genericContainer("redis") {
        image("redis:7-alpine")
        exposedPorts(6379)
        env("REDIS_PASSWORD" to "secret")
        startupTimeoutSeconds(45)
        // Configure wait strategies: waitPort(), waitHttp(path, status), or waitLog(regex, times)
        waitPort()
    }

    // 3. Docker Compose Setup
    composeContainer("my-stack", "compose.yaml") {
        service("web", 8080)
        service("cache", 6379)
        startupTimeoutSeconds(120)
    }
}
```

### 3. Add Custom Testcontainers Modules
Because database drivers are loaded dynamically, declare them in the `testcontainersClasspath` configuration:

```kotlin
dependencies {
    "testcontainersClasspath"("org.testcontainers:postgresql:2.0.5")
}
```

---

## Configuring JDBC Containers

The `jdbcContainer` block sets up relational database containers. It is highly recommended to use the **`DatabaseType` enum** instead of raw strings to avoid typos and ensure the database engine is natively supported by Testcontainers.

### Example:
```kotlin
import org.testcontainers.gradle.DatabaseType

testcontainers {
    jdbcContainer("db", DatabaseType.POSTGRESQL) {
        // Optional configuration:
        image("custom-postgres:18") // Compatibility is automatically determined ("postgres") based on the DatabaseType!
        databaseName("testdb")
        username("testuser")
        password("testpassword")
        reuse(false)
    }
}
```

### Configuration Options:
* **`image(name, compatibleSubstituteFor)`** *(Optional)*: Sets the Docker image name. The compatibility substitute is automatically resolved based on the database type (e.g. `"postgres"` for `DatabaseType.POSTGRESQL`). You can explicitly provide it as a second argument (e.g. `image("custom-postgres", "postgres")`) if you are using an unrecognized database type/fork. If omitted, the default image from the Testcontainers provider is used.
* **`databaseName(name)`** *(Optional)*: Sets the name of the database. If omitted, falls back to the default database name from the provider.
* **`username(name)`** *(Optional)*: Sets the database administrator username. If omitted, falls back to the default username from the provider.
* **`password(name)`** *(Optional)*: Sets the database administrator password. If omitted, falls back to the default password from the provider.
* **`reuse(boolean)`** *(Optional)*: Enables Testcontainers' reuse mode to keep container instances alive across build executions (defaults to `false`).

---

## Using the Containers in Custom Tasks

Every registered container receives automatic `start<Name>Container` and `stop<Name>Container` tasks (e.g. `startDbContainer`, `stopDbContainer`). 

To access the running container properties in your custom build tasks:

```kotlin
import org.testcontainers.gradle.getJdbcDatabaseContainer

tasks.register("runMigration") {
    // 1. Explicitly depend on the container start task
    dependsOn("startDbContainer")
    
    // 2. Register the build service to follow Gradle's build lifecycle
    usesService(testcontainers.service)
    
    // 3. Resolve the provider inside the task configuration (not global script scope) 
    // to ensure Configuration Cache compliance.
    val dbProvider = testcontainers.getJdbcDatabaseContainer("db")
    
    doFirst {
        // 4. Fetch the container instance lazily on-demand from the captured provider
        val db = dbProvider.get()
        
        println("Connecting to database at: ${db.jdbcUrl}")
        // Run database migration/code-generation tool here...
    }
}
```

### Container Retrieval Helpers
The following extension helpers are available on `testcontainers` for retrieval:
* `testcontainers.getJdbcDatabaseContainer("name")` -> `Provider<JdbcDatabaseContainer<*>>`
* `testcontainers.getGenericContainer("name")` -> `Provider<GenericContainer<*>>`
* `testcontainers.getComposeContainer("name")` -> `Provider<ComposeContainer>`

---

## Local Development

To make changes to this plugin locally and verify them:

### 1. Build and Run Tests
Run the test suite using Gradle:
```bash
./gradlew test
```
*Note: Docker-dependent integration tests will be skipped automatically if no Docker daemon is running on your machine.*

### 2. Publish to Maven Local
To use your local snapshot of the plugin, publish it to your local Maven repository:
```bash
./gradlew publishToMavenLocal
```

### 3. Try the Example Project
An independent example project resides in the `example/` directory.

To run the example task using your locally published plugin:
1. Ensure you published the plugin to `mavenLocal()` first.
2. Navigate to the `example/` directory and run:
   ```bash
   ../gradlew printDbInfo
   ```

---

## Caveats & Gradle Daemon Reusability

### The Gradle Daemon & Ryuk Persistence
When running builds, Testcontainers starts **Ryuk** (a sidecar "moby-ryuk" container) to manage resource reaping. Because Gradle uses a long-running Daemon JVM to keep builds fast, the Gradle `BuildService` managing our containers stays alive in memory across builds. 

As a result, you will notice that the Ryuk container and your defined databases/services remain running in Docker even after your Gradle task finishes.

### Why this is OK (and actually a feature)
1. **Reusability and Performance**: Keeping containers alive in the background allows subsequent Gradle tasks to instantly reuse the running containers. Instead of waiting 10–30 seconds for a database to download, initialize, and run on every build, the next build connects to the running container in milliseconds.
2. **Guaranteed Cleanup**: Once the Gradle Daemon eventually exits (when you run `gradle --stop` or after the 3-hour idle timeout), the JVM shutdown hooks run, and Ryuk immediately reaps and destroys all container resources, volumes, and networks. No orphaned containers are leaked.
3. **Manual Control**: If you want to force-stop the containers immediately to free up ports or memory, you can simply run the generated lifecycle task:
   ```bash
   ./gradlew stopDbContainer
   ```
