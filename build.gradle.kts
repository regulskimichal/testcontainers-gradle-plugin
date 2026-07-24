plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.axion.release)
}

scmVersion {
    tag {
        prefix = "v"
    }
    useHighestVersion = true
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("config/detekt/detekt.yml")
}

group = "io.github.regulskimichal"
version = scmVersion.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
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

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val dokkaGeneratePublicationHtml = tasks.named("dokkaGeneratePublicationHtml")
tasks.named<Jar>("javadocJar") {
    dependsOn(dokkaGeneratePublicationHtml)
    from(layout.buildDirectory.dir("dokka/html/publication"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
