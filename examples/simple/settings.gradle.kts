import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("../..")
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "example-simple"
