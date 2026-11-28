pluginManagement {
  repositories {
    // Google's read-only mirror of Maven Central, resolved FIRST: repo.maven.apache.org
    // rate-limits CI runners and some networks with HTTP 429 (observed on GitHub Actions),
    // which fails otherwise-green builds. Groups hosted only on Google Maven 404 here and
    // fall through to google() below.
    maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // Maven Central mirror first (see comment above); identical artifact set.
    maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.mozilla.org/maven2/")
    }
  }
}

rootProject.name = "Simple AI Browser"

include(":app")
