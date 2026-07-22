package org.testcontainers.gradle

/**
 * Supported database types for type-safe Testcontainers JDBC configuration.
 *
 * Use these enum constants with [TestcontainersConfig.jdbcContainer] for IDE autocomplete
 * and to prevent typos in database type identifiers. Each entry maps to a canonical
 * Docker image name used by Testcontainers.
 *
 * Example:
 * ```kotlin
 * testcontainers {
 *     jdbcContainer("postgres", DatabaseType.POSTGRESQL) {
 *         databaseName("mydb")
 *         portMapping(5432)
 *     }
 * }
 * ```
 *
 * For custom image names, use [TestcontainersConfig.jdbcContainer(String, String, Function)] instead.
 *
 * @see TestcontainersConfig.jdbcContainer for registration
 * @see resolveCanonicalImageName for string-based type resolution
 */
enum class DatabaseType(
    /**
     * The type identifier string used to match database type configurations.
     * Can be used with the string-based [TestcontainersConfig.jdbcContainer] overload.
     */
    val id: String,
    /**
     * The canonical Docker image name maintained by Testcontainers for this database.
     * Automatically used unless overridden in the container configuration.
     */
    val canonicalImageName: String,
    /**
     * Common alternative aliases for string-based lookup (e.g. "postgres", "mssql").
     */
    val aliases: List<String> = emptyList()
) {
    /** ClickHouse OLAP database. */
    CLICKHOUSE("clickhouse", "clickhouse/clickhouse-server"),
    /** CockroachDB distributed SQL database. */
    COCKROACHDB("cockroach", "cockroachdb/cockroach"),
    /** CrateDB distributed search and analytics database. */
    CRATEDB("cratedb", "crate"),
    /** IBM Db2 relational database. */
    DB2("db2", "ibmcom/db2"),
    /** MariaDB open-source relational database (MySQL compatible). */
    MARIADB("mariadb", "mariadb"),
    /** Microsoft SQL Server database. */
    MSSQL("sqlserver", "mcr.microsoft.com/mssql/server", listOf("mssql")),
    /** MySQL relational database. */
    MYSQL("mysql", "mysql"),
    /** OceanBase distributed database compatible with MySQL/Oracle. */
    OCEANBASE("oceanbasece", "oceanbase/oceanbase-ce"),
    /** Oracle Database (free edition). */
    ORACLE("oracle", "gvenzl/oracle-free"),
    /** pgvector - PostgreSQL with vector support for AI/ML. */
    PGVECTOR("pgvector", "pgvector/pgvector"),
    /** PostGIS - PostgreSQL with geographic extensions. */
    POSTGIS("postgis", "postgis/postgis"),
    /** PostgreSQL relational database. */
    POSTGRESQL("postgresql", "postgres", listOf("postgres")),
    /** QuestDB time-series database. */
    QUESTDB("questdb", "questdb/questdb"),
    /** TiDB distributed NewSQL database compatible with MySQL. */
    TIDB("tidb", "pingcap/tidb"),
    /** TimescaleDB time-series database (PostgreSQL extension). */
    TIMESCALEDB("timescaledb", "timescale/timescaledb", listOf("timescale")),
    /** Timeplus streaming database. */
    TIMEPLUS("timeplus", "timeplus/timeplus"),
    /** Trino distributed SQL query engine. */
    TRINO("trino", "trinodb/trino"),
    /** YugabyteDB distributed SQL database compatible with PostgreSQL. */
    YUGABYTEDB("yugabyte", "yugabytedb/yugabyte")
}

/**
 * Resolves a database type string to its canonical Testcontainers Docker image name.
 *
 * This function enables flexible string-based database type specification, supporting:
 * - Enum names: "POSTGRESQL" → "postgres"
 * - Enum IDs: "postgresql" → "postgres"
 * - Common shortcuts: "postgres" → "postgres"
 * - Partial matching: "sql" → "mcr.microsoft.com/mssql/server"
 *
 * Used internally by [TestcontainersConfig.jdbcContainer(String, String, Function)]
 * to resolve database type strings to image names.
 *
 * For most use cases, prefer the type-safe [TestcontainersConfig.jdbcContainer(String, DatabaseType, Function)]
 * overload with [DatabaseType] enum constants to get IDE autocomplete and prevent typos.
 *
 * @param databaseType The database type string to resolve (case-insensitive)
 * @return The canonical Docker image name, or `null` if no match is found
 *
 * @see DatabaseType for all supported database types
 * @see TestcontainersConfig.jdbcContainer for registration
 *
 * Example matching:
 * - "postgresql" → "postgres"
 * - "POSTGRESQL" → "postgres"
 * - "postgres" → "postgres"
 * - "postgis" → "postgis/postgis" (more specific match wins)
 * - "oracle" → "gvenzl/oracle-free"
 * - "invalid-db" → null
 */
fun resolveCanonicalImageName(databaseType: String): String? {
    val lower = databaseType.lowercase()
    return DatabaseType.entries.firstOrNull { type ->
        type.id.equals(databaseType, ignoreCase = true) ||
        type.name.equals(databaseType, ignoreCase = true) ||
        lower.contains(type.id) ||
        lower.contains(type.name.lowercase()) ||
        type.aliases.any { lower.contains(it) }
    }?.canonicalImageName
}
