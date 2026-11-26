pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
  }

  // The versions of the libraries that both apps use are defined once, in the Android project
  versionCatalogs { create("libs") { from(files("../android/gradle/libs.versions.toml")) } }
}

rootProject.name = "AdvantageTuneDesktop"
