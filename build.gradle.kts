plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("config/detekt/detekt.yml")
}

group = "io.github.regulskimichal"
version = findProperty("version")?.toString() ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    website.set("https://github.com/regulskimichal/testcontainers-gradle-plugin")
    vcsUrl.set("https://github.com/regulskimichal/testcontainers-gradle-plugin.git")
    plugins {
        create("testcontainers") {
            id = "io.github.regulskimichal.testcontainers"
            displayName = "Testcontainers Gradle Plugin"
            description =
                "A minimal, framework-agnostic Gradle plugin that manages container lifecycles for build-time tasks (such as code generation, database migrations, schema inspection, etc.) using Testcontainers."
            tags.set(listOf("testcontainers", "docker", "compose", "containers", "jdbc", "database", "migration"))
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
val dokkaGeneratePublicationHtml = tasks.named("dokkaGeneratePublicationHtml")

val javadocJar = tasks.register<Jar>("javadocJar") {
    description = "Assembles a jar archive containing the Javadoc (KDoc) API documentation."
    dependsOn(dokkaGeneratePublicationHtml)
    archiveClassifier.set("javadoc")
    // Dokka V2 generates HTML docs to build/dokka/html/publication
    from(layout.buildDirectory.dir("dokka/html/publication"))
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

