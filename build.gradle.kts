plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("org.jetbrains.dokka") version "1.8.20"
}

group = "org.testcontainers"
version = findProperty("version")?.toString() ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    website.set("https://github.com/org/testcontainers-gradle-plugin")
    vcsUrl.set("https://github.com/org/testcontainers-gradle-plugin")
    plugins {
        create("testcontainers") {
            id = "org.testcontainers"
            displayName = "Testcontainers Gradle Plugin"
            description = "Testcontainers Gradle plugin for managing container lifecycles during builds"
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

// Dokka + packaging of sources and javadoc (KDoc) jars
val dokkaHtml = tasks.named("dokkaHtml")

val javadocJar = tasks.register<Jar>("javadocJar") {
    description = "Assembles a jar archive containing the Javadoc (KDoc) API documentation."
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    // Dokka generates HTML docs to build/dokka/html
    from(layout.buildDirectory.dir("dokka/html"))
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    description = "Assembles a jar archive containing the source code."
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            pom {
                name.set("testcontainers-gradle-plugin")
                description.set("Testcontainers Gradle plugin")
            }
        }
    }
}

// If a plugin marker publication (pluginMaven) exists, attach artifacts to it as well
afterEvaluate {
    publishing.publications.findByName("pluginMaven")?.let { pub ->
        (pub as MavenPublication).apply {
            artifact(sourcesJar)
            artifact(javadocJar)
        }
    }
}

