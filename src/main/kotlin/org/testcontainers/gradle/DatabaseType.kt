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
    val canonicalImageName: String
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
    /** MySQL relational database. */
    MYSQL("mysql", "mysql"),
    /** Microsoft SQL Server database. */
    MSSQL("sqlserver", "mcr.microsoft.com/mssql/server"),
    /** OceanBase distributed database compatible with MySQL/Oracle. */
    OCEANBASE("oceanbasece", "oceanbase/oceanbase-ce"),
    /** Oracle Database (free edition). */
    ORACLE("oracle", "gvenzl/oracle-free"),
    /** PostGIS - PostgreSQL with geographic extensions. */
    POSTGIS("postgis", "postgis/postgis"),
    /** PostgreSQL relational database. */
    POSTGRESQL("postgresql", "postgres"),
    /** QuestDB time-series database. */
    QUESTDB("questdb", "questdb/questdb"),
    /** TimescaleDB time-series database (PostgreSQL extension). */
    TIMESCALEDB("timescaledb", "timescale/timescaledb"),
    /** pgvector - PostgreSQL with vector support for AI/ML. */
    PGVECTOR("pgvector", "pgvector/pgvector"),
    /** TiDB distributed NewSQL database compatible with MySQL. */
    TIDB("tidb", "pingcap/tidb"),
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
 * - Type-safe enum lookup: [DatabaseType.POSTGRESQL.canonicalImageName]
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
    val matchedEnum = DatabaseType.entries.firstOrNull {
        it.id.equals(databaseType, ignoreCase = true) || it.name.equals(databaseType, ignoreCase = true)
    }
    if (matchedEnum != null) {
        return matchedEnum.canonicalImageName
    }
    val lower = databaseType.lowercase()
    return when {
        lower.contains("clickhouse") -> "clickhouse/clickhouse-server"
        lower.contains("cockroach") -> "cockroachdb/cockroach"
        lower.contains("db2") -> "ibmcom/db2"
        lower.contains("mariadb") -> "mariadb"
        lower.contains("mysql") -> "mysql"
        lower.contains("oracle") -> "gvenzl/oracle-free"
        lower.contains("pgvector") -> "pgvector/pgvector"
        lower.contains("postgis") -> "postgis/postgis"
        lower.contains("postgres") -> "postgres"
        lower.contains("sqlserver") || lower.contains("mssql") -> "mcr.microsoft.com/mssql/server"
        lower.contains("tidb") -> "pingcap/tidb"
        lower.contains("timescale") -> "timescale/timescaledb"
        lower.contains("trino") -> "trinodb/trino"
        lower.contains("yugabyte") -> "yugabytedb/yugabyte"
        else -> null
    }
}
