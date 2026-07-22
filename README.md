# Testcontainers Gradle Plugin

A minimal, framework-agnostic Gradle plugin that manages container lifecycles for build-time tasks (such as code generation, database migrations, schema inspection, or integration testing) using [Testcontainers](https://testcontainers.com/).

---

## Features

- **Build-Time Lifecycles**: Dynamically registers `start<Name>Container` and `stop<Name>Container` tasks for every container defined.
- **Single-Threaded Build Service Isolation**: Uses a Gradle `BuildService` configured with `maxParallelUsages = 1` to prevent parallel tasks (e.g. concurrent tests or code generation steps) from causing race conditions on shared container instances.
- **Flexible & Type-Safe DSL**:
  - **JDBC Databases**: Type-safe relational database configuration via the [DatabaseType](file:///c:/Users/Michal/IdeaProjects/testcontainers-gradle-plugin/src/main/kotlin/org/testcontainers/gradle/DatabaseType.kt) enum (18+ supported databases) or flexible string-based resolution.
  - **Generic Containers**: Support for arbitrary Docker images (Redis, Kafka, DynamoDB, etc.) with custom environment variables, port exposures, volume mounts, and wait strategies (`waitPort()`, `waitHttp()`, `waitLog()`).
  - **Docker Compose Stacks**: Multi-container Docker Compose environments with service port exposure and startup timeouts.
- **Incremental Build Integration & Task Skipping**: Built-in support for Gradle's UP-TO-DATE checks using `trackedFiles` and automatic marker files (`build/testcontainers/start<Name>.marker`). Skips starting containers and running downstream tasks when input files (e.g. migration scripts) are unchanged.
- **Configuration Cache & Build Cache Ready**: Built with serializable container definitions and lazy Gradle `Provider` APIs for full Gradle Configuration Cache and Build Cache compatibility.
- **Reflection-Safe Classloading**: Isolated dependency resolution via the `testcontainersClasspath` configuration allows loading custom database drivers and Testcontainers modules via `ServiceLoader` without polluting project buildscript classloaders.

---

## Getting Started

### 1. Apply the Plugin

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.regulskimichal.testcontainers") version "<VERSION>"
}
```

### 2. Declare Dynamic Dependencies

Dynamic Testcontainers database modules and JDBC drivers are loaded at runtime. Add required Testcontainers modules to the `testcontainersClasspath` configuration:

```kotlin
dependencies {
    "testcontainersClasspath"("org.testcontainers:postgresql:1.20.4")
}
```

### 3. Configure Your Containers

Configure containers inside the `testcontainers { }` extension block:

```kotlin
import org.testcontainers.gradle.DatabaseType

testcontainers {
    // 1. JDBC Database Container (Postgres example)
    jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
        image("postgres:18-alpine")
        databaseName("testdb")
        username("user")
        password("pass")
        portMapping(5432)
    }

    // 2. Generic Container (Redis example)
    genericContainer("redis") {
        image("redis:7-alpine")
        exposedPorts(6379)
        env("REDIS_PASSWORD" to "secret")
        startupTimeoutSeconds(45)
        waitPort()
    }

    // 3. Docker Compose Stack
    composeContainer("stack", "compose.yaml") {
        service("postgres", 5432)
        service("redis", 6379)
        startupTimeoutSeconds(60)
    }
}
```

---

## Container Configuration DSL Reference

### 1. JDBC Database Containers (`jdbcContainer`)

The `jdbcContainer` block registers relational database containers. It is recommended to use the [DatabaseType](file:///c:/Users/Michal/IdeaProjects/testcontainers-gradle-plugin/src/main/kotlin/org/testcontainers/gradle/DatabaseType.kt) enum for type safety and IDE autocompletion.

#### Type-Safe Overload (Preferred):

```kotlin
import org.testcontainers.gradle.DatabaseType

testcontainers {
    jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
        image("postgres:18-alpine")
        databaseName("testdb")
        username("user")
        password("password")
        reuse(false)
        portMapping(5432)
    }
}
```

#### String-Based Overload:

You can also pass a database type string identifier (e.g. `"postgresql"`, `"mysql"`, `"oracle"`). Canonical image names are resolved automatically via `resolveCanonicalImageName`.

```kotlin
testcontainers {
    jdbcContainer("my-db", "postgresql") {
        databaseName("testdb")
    }
}
```

#### Supported Database Types (`DatabaseType`):

| Enum Constant | Identifier (`id`) | Canonical Image Name             |
|:--------------|:------------------|:---------------------------------|
| `CLICKHOUSE`  | `"clickhouse"`    | `clickhouse/clickhouse-server`   |
| `COCKROACHDB` | `"cockroach"`     | `cockroachdb/cockroach`          |
| `CRATEDB`     | `"cratedb"`       | `crate`                          |
| `DB2`         | `"db2"`           | `ibmcom/db2`                     |
| `MARIADB`     | `"mariadb"`       | `mariadb`                        |
| `MYSQL`       | `"mysql"`         | `mysql`                          |
| `MSSQL`       | `"sqlserver"`     | `mcr.microsoft.com/mssql/server` |
| `OCEANBASE`   | `"oceanbasece"`   | `oceanbase/oceanbase-ce`         |
| `ORACLE`      | `"oracle"`        | `gvenzl/oracle-free`             |
| `POSTGIS`     | `"postgis"`       | `postgis/postgis`                |
| `POSTGRESQL`  | `"postgresql"`    | `postgres`                       |
| `QUESTDB`     | `"questdb"`       | `questdb/questdb`                |
| `TIMESCALEDB` | `"timescaledb"`   | `timescale/timescaledb`          |
| `PGVECTOR`    | `"pgvector"`      | `pgvector/pgvector`              |
| `TIDB`        | `"tidb"`          | `pingcap/tidb`                   |
| `TIMEPLUS`    | `"timeplus"`      | `timeplus/timeplus`              |
| `TRINO`       | `"trino"`         | `trinodb/trino`                  |
| `YUGABYTEDB`  | `"yugabyte"`      | `yugabytedb/yugabyte`            |

#### `JdbcContainerSpec` Options:

- **`image(name)`**: Sets a custom Docker image reference (e.g., `image("postgres:18-alpine")`). Overrides the default image for the database type.
- **`databaseName(name)`**: Sets the initial database name created on container startup.
- **`username(name)`**: Sets the database administrator username.
- **`password(name)`**: Sets the database administrator password.
- **`reuse(boolean)`**: Enables Testcontainers container reuse mode across build executions (default `false`).
- **`portMapping(containerPort, hostPort = containerPort)`**: Binds a container port to a fixed host port. If omitted, Testcontainers assigns a dynamic available host port.

---

### 2. Generic Containers (`genericContainer`)

The `genericContainer` block configures any public or private Docker image not covered by specialized container types (e.g., Redis, Kafka, DynamoDB, Elasticsearch, or custom microservices).

```kotlin
testcontainers {
    genericContainer("redis") {
        image("redis:7-alpine")
        exposedPorts(6379)
        env("REDIS_PASSWORD" to "secret", "LOG_LEVEL" to "info")
        mountVolume("./config", "/etc/redis/config", readOnly = true)
        startupTimeoutSeconds(30)
        waitPort()
    }
}
```

#### `GenericContainerSpec` Options:

- **`image(name, compatibleSubstituteFor = null)`**: Sets the Docker image reference.
- **`exposedPorts(vararg ports)` / `exposedPorts(list)`**: Sets container ports to expose to the host.
- **`env(vararg pairs)` / `env(map)`**: Sets environment variables passed to the container.
- **`reuse(boolean)`**: Enables Testcontainers container reuse across builds.
- **`startupTimeoutSeconds(seconds)`**: Maximum time in seconds to wait for container readiness (default `60`).
- **Wait Strategies**:
  - **`waitPort()`**: Waits for exposed container ports to listen on TCP (default).
  - **`waitHttp(path, statusCode = 200)`**: Waits for an HTTP endpoint to respond with the expected status code (e.g., `waitHttp("/health", 200)`).
  - **`waitLog(regex, times = 1)`**: Waits for container logs to match a regular expression pattern.
- **`mountVolume(hostPath, containerPath, readOnly = false)`**: Binds a host file or directory into the container. `hostPath` accepts a `File`, Gradle `Directory`, `RegularFile`, or path string (relative paths are resolved from the project root).

---

### 3. Docker Compose Stacks (`composeContainer`)

The `composeContainer` block manages multi-container stacks defined in a `docker-compose.yaml` file.

```kotlin
testcontainers {
    composeContainer("stack", "compose.yaml") {
        service("postgres", 5432)
        service("web", 8080)
        startupTimeoutSeconds(60)
    }
}
```

#### `ComposeContainerSpec` Options:

- **`service(serviceName, vararg ports)`**: Exposes specific container ports for a service defined in the compose file and waits for TCP readiness. At least one service must be explicitly exposed.
- **`startupTimeoutSeconds(seconds)`**: Maximum time in seconds to wait for all exposed services to become ready (default `60`).

---

## Accessing Containers in Custom Tasks

Every registered container dynamically generates matching `start<SanitizedName>Container` and `stop<SanitizedName>Container` tasks (e.g., `startPostgresContainer` and `stopPostgresContainer`).

To lazily retrieve running container instances inside custom build tasks:

```kotlin
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.gradle.getContainer

tasks.register("runMigrations") {
    // 1. Depend on the auto-generated container start task
    dependsOn("startPostgresContainer")

    // 2. Register the build service to enforce maxParallelUsages = 1
    usesService(testcontainers.service)

    // 3. Obtain a lazy, Configuration Cache-safe Provider<T>
    val postgresProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")

    doLast {
        // 4. Fetch the running container instance lazily at execution time
        val db = postgresProvider.get()
        println("Connecting to JDBC URL: ${db.jdbcUrl}")
        println("User: ${db.username}")
    }
}
```

### Container Retrieval Extensions

The `getContainer<T>("name")` extension function retrieves a `Provider<T>` for any container type:

- **JDBC Databases**: `testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")`
- **Generic Containers**: `testcontainers.getContainer<GenericContainer<*>>("redis")`
- **Docker Compose Stacks**: `testcontainers.getContainer<ComposeContainer>("stack")`
- **Any Container**: `testcontainers.getContainer<Startable>("my-container")`

---

## Incremental Builds & Task Skipping

The plugin supports incremental builds by integrating `StartContainersTask` with Gradle's UP-TO-DATE checks.

When inputs (such as Flyway/Liquibase `.sql` migration files) have not changed, `StartContainersTask` is marked UP-TO-DATE and skipped. Downstream tasks can check `testcontainers.service.wasContainerStarted("name")` in their `onlyIf` block to skip execution when no container startup occurred.

### Complete Example (Flyway + jOOQ Codegen):

```kotlin
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.gradle.DatabaseType
import org.testcontainers.gradle.StartContainersTask
import org.testcontainers.gradle.getContainer
import org.testcontainers.gradle.wasContainerStarted

testcontainers {
    jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
        image("postgres:18-alpine")
        databaseName("testdb")
        username("postgres")
        password("postgres")
    }
}

val dbMigrationDir = provider { layout.projectDirectory.dir("src/main/resources/db/migration") }

// Configure trackedFiles on the start task inside afterEvaluate
afterEvaluate {
    tasks.named<StartContainersTask>("startPostgresContainer") {
        trackedFiles.from(dbMigrationDir)
    }
}

val flywayMigrate = tasks.register("flywayMigrate") {
    dependsOn("startPostgresContainer")

    val service = testcontainers.service
    usesService(service)

    val dbProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")

    // Skip migration if the database container was not started (migration files unchanged)
    onlyIf { service.wasContainerStarted("postgres") }

    doLast {
        val db = dbProvider.get()
        // Execute Flyway migration against db.jdbcUrl...
    }
}

val jooqCodegen = tasks.register("jooqCodegen") {
    dependsOn(flywayMigrate)
    finalizedBy("stopPostgresContainer")

    val service = testcontainers.service
    usesService(service)

    val dbProvider = testcontainers.getContainer<JdbcDatabaseContainer<*>>("postgres")

    // Skip codegen if migrations did not run
    onlyIf { service.wasContainerStarted("postgres") }

    doLast {
        val db = dbProvider.get()
        // Generate jOOQ code from db.jdbcUrl...
    }
}
```

---

## Auto-Generated Tasks

For every registered container (e.g. `"postgres"`), the plugin automatically registers:

- **`startPostgresContainer`** (`StartContainersTask`):
  - Starts the container via `TestcontainersBuildService`.
  - Configured to run after `clean` (`mustRunAfter(clean)`).
  - Automatically manages output marker file at `build/testcontainers/startPostgres.marker`.
  - Supports `trackedFiles` for input-based UP-TO-DATE evaluation.
- **`stopPostgresContainer`** (`StopContainersTask`):
  - Stops the container via `TestcontainersBuildService`.
  - Includes an `onlyIf { service.wasContainerStarted("postgres") }` condition so containers that were never started in the current build execution are not stopped unnecessarily.

---

## Gradle Daemon Lifecycle & Ryuk Sidecar

### Container Persistence Across Builds

When running builds, Testcontainers starts **Ryuk** (`moby-ryuk`) to monitor and reap container resources. Because Gradle uses a long-running Daemon JVM to optimize build speeds, the `TestcontainersBuildService` managing running containers remains active in daemon memory across builds.

As a result, containers remain running in Docker after individual Gradle tasks complete.

### Advantages of Daemon-Managed Lifecycles

1. **High Performance**: Subsequent `./gradlew` executions instantly reuse running container instances, turning multi-second container initialization into millisecond connections.
2. **Automated Resource Reclamation**: When the Gradle Daemon stops (via `./gradlew --stop` or idle timeout), JVM shutdown hooks run and Ryuk automatically destroys all container resources, networks, and volumes.
3. **Manual Stop Task**: To force container termination at any time, execute the generated stop task:
   ```bash
   ./gradlew stopPostgresContainer
   ```

---

## Local Development & Verification

### 1. Run Unit and Integration Tests

```bash
./gradlew test
```

*Note: Integration tests requiring Docker are automatically skipped if no Docker daemon is accessible.*

### 2. Publish to Maven Local

```bash
./gradlew publishToMavenLocal
```

### 3. Run Included Examples

Explore functional sample projects in the `examples/` directory:

- **Simple Example**: `examples/simple/`
  ```bash
  ./gradlew printDbInfo --project-dir examples/simple
  ```
- **jOOQ + Flyway Example**: `examples/jooq-flyway/`
  ```bash
  ./gradlew jooq --project-dir examples/jooq-flyway
  ```
