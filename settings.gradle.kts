pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.google.com") }
        maven {
            url =
                uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        }
        maven {
            url = uri("https://artifact.bytedance.com/repository/pangle/")
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.google.com") }
        maven {
            url =
                uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        }
        maven {
            url = uri("https://artifact.bytedance.com/repository/pangle/")
        }
        maven {
            url = uri("https://maven.pkg.github.com/cscappstudio/csc_ads_lib")
            credentials {
                val localProperties = java.util.Properties()
                val localFile = java.io.File(rootDir, "local.properties")
                if (localFile.exists()) {
                    localFile.inputStream().use { localProperties.load(it) }
                }
                username = localProperties.getProperty("gpr.user") ?: System.getenv("GITHUB_USER") ?: "csccuongnv"
                password = localProperties.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "a506_livescore"
include(":app")
