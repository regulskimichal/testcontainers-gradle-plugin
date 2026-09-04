package org.testcontainers.gradle

import java.io.Serial

class MissingJdbcDatabaseContainerProviderException(databaseType: String) :
    RuntimeException("No JdbcDatabaseContainerProvider found for database type '$databaseType'. Make sure to add the corresponding dependency to 'testcontainersClasspath'.") {

    companion object {
        @Serial
        private const val serialVersionUID: Long = 3945507646202758093L
    }
}
