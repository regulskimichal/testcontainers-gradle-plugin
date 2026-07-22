package org.testcontainers.gradle

/**
 * Supported database types for type-safe Testcontainers JDBC configuration.
 */
enum class DatabaseType(val id: String, val canonicalImageName: String) {
    CLICKHOUSE("clickhouse", "clickhouse/clickhouse-server"),
    COCKROACHDB("cockroach", "cockroachdb/cockroach"),
    CRATEDB("cratedb", "crate"),
    DB2("db2", "ibmcom/db2"),
    MARIADB("mariadb", "mariadb"),
    MYSQL("mysql", "mysql"),
    MSSQL("sqlserver", "mcr.microsoft.com/mssql/server"),
    OCEANBASE("oceanbasece", "oceanbase/oceanbase-ce"),
    ORACLE("oracle", "gvenzl/oracle-free"),
    POSTGIS("postgis", "postgis/postgis"),
    POSTGRESQL("postgresql", "postgres"),
    QUESTDB("questdb", "questdb/questdb"),
    TIMESCALEDB("timescaledb", "timescale/timescaledb"),
    PGVECTOR("pgvector", "pgvector/pgvector"),
    TIDB("tidb", "pingcap/tidb"),
    TIMEPLUS("timeplus", "timeplus/timeplus"),
    TRINO("trino", "trinodb/trino"),
    YUGABYTEDB("yugabyte", "yugabytedb/yugabyte")
}

/**
 * Resolves the canonical Testcontainers Docker image name for a given database type ID or string.
 * This handles string variations, abbreviations (e.g. "postgres"), and forks.
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
