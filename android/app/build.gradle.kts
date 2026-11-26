plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "org.littletonrobotics.advantagetune"
  compileSdk {
    version = release(37) { minorApiLevel = 2 }
  }

  defaultConfig {
    applicationId = "org.littletonrobotics.advantagetune"
    minSdk = 26
    targetSdk = 36

    // Keep in step with the version of the iOS app
    versionCode = 1
    versionName = "1.1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  androidResources {
    // Lets the language be chosen per app in the system settings
    generateLocaleConfig = true
  }

  lint {
    // The strings are generated from the iOS string catalog and kept as they are there. Strings
    // without a translation fall back to English, as on iOS.
    disable += listOf("MissingTranslation", "TypographyEllipsis", "PluralsCandidate")
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.okhttp)
  implementation(libs.protobuf.java)

  testImplementation(libs.junit)
}
