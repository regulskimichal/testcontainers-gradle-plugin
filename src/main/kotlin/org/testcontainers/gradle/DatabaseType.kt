package org.testcontainers.gradle

/**
 * Supported database types for type-safe Testcontainers JDBC configuration.
 */
enum class DatabaseType(val id: String) {
    CLICKHOUSE("clickhouse"),
    COCKROACHDB("cockroach"),
    CRATEDB("cratedb"),
    DB2("db2"),
    MARIADB("mariadb"),
    MYSQL("mysql"),
    MSSQL("sqlserver"),
    OCEANBASE("oceanbasece"),
    ORACLE("oracle"),
    POSTGIS("postgis"),
    POSTGRESQL("postgresql"),
    QUESTDB("questdb"),
    TIMESCALEDB("timescaledb"),
    PGVECTOR("pgvector"),
    TIDB("tidb"),
    TIMEPLUS("timeplus"),
    TRINO("trino"),
    YUGABYTEDB("yugabyte")
}
