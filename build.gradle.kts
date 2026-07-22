plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.testcontainers"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("testcontainers") {
            id = "org.testcontainers"
            implementationClass = "org.testcontainers.gradle.TestcontainersPlugin"
        }
    }
}

dependencies {
    implementation(platform(libs.testcontainers.bom))
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.jdbc)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
