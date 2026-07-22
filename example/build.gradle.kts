import org.testcontainers.gradle.getJdbcDatabaseContainer

plugins {
    id("org.testcontainers") version "1.0.0-SNAPSHOT"
}

testcontainers {
    jdbcContainer("db", "postgresql") {
        image = "postgres:17-alpine"
        databaseName = "demo-db"
        username = "demo-user"
        password = "demo-password"
    }
}

dependencies {
    "testcontainersClasspath"("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.register("printDbInfo") {
    dependsOn("startDbContainer")
    usesService(testcontainers.service)
    
    val dbProvider = testcontainers.getJdbcDatabaseContainer("db")
    
    doFirst {
        val db = dbProvider.get()
        println("SUCCESSFULLY CONFIGURED POSTGRES CONTAINER!")
        println("JDBC URL: " + db.jdbcUrl)
        println("Username: " + db.username)
        println("Password: " + db.password)
    }
}
