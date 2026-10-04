pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MnemoLink"
include(":core", ":desktop")
if (providers.gradleProperty("desktopOnly").getOrElse("false") != "true") {
    include(":app")
}
