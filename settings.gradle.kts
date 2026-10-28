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

rootProject.name = "Miogram"

includeBuild("../ktproto")
include(":app")
include(":core:model")
include(":core:media")
include(":core:ui")
include(":core:lottie")
include(":core:database")
