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

    flatDir {
      dir(rootDir.resolve("libs"))
    }

  }
}

include(":seam-phone-sdk-android")
project(":seam-phone-sdk-android").projectDir = File("../seam-phone-android/seam-phone-sdk-android")
