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

rootProject.name = "HeadShaker"

include(":app")
include(":sceneform")
include(":sceneformux")

project(":sceneform").projectDir = File("sceneformsrc/sceneform")
project(":sceneformux").projectDir = File("sceneformux/ux")