@file:Suppress("UnstableApiUsage")

include(":mobileapp-api-models")


rootProject.name = "MobileApp"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("android")
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")

            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("android")
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core")

include(":app:shared")
include(":app:ui-components")
include(":app:androidApp")
