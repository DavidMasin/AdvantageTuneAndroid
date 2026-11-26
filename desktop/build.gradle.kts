import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  id("org.jetbrains.kotlin.jvm") version libs.versions.kotlin
  alias(libs.plugins.kotlin.compose)
  id("org.jetbrains.compose") version "1.12.2"
}

// The desktop app is built from the sources and resources of the Android app, apart from the
// files below, which use Android APIs. Each has a counterpart here named `*.desktop.kt`.
val androidApp = layout.projectDirectory.dir("../android/app")
val androidOnlySources =
  listOf(
    "**/AdvantageTuneApp.kt",
    "**/LicensesActivity.kt",
    "**/models/Localized.kt",
    "**/models/UserDefaults.kt",
    "**/network/MainQueue.kt",
    "**/views/components/Haptics.kt",
    "**/views/components/Platform.kt",
  )

/** Generates the `R` object that the shared sources use to refer to resources. */
abstract class GenerateResourceIds : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val resourceDirectory: DirectoryProperty

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val res = resourceDirectory.get().asFile
    val stringName = Regex("<string name=\"([^\"]+)\"")
    val strings =
      res.resolve("values").listFiles()!!
        .filter { it.name.startsWith("strings") && it.extension == "xml" }
        .flatMap { file -> stringName.findAll(file.readText()).map { it.groupValues[1] } }
        .toSortedSet()
    val drawables =
      res.resolve("drawable").listFiles()!!
        .filter { it.extension == "xml" && !it.name.startsWith("ic_launcher") }
        .map { it.nameWithoutExtension }
        .sorted()
    val raws = res.resolve("raw").listFiles()!!.map { it.name }.sorted()
    val locales =
      res.listFiles()!!
        .filter { it.name.startsWith("values-") && it.resolve("strings.xml").exists() }
        .map { it.name.removePrefix("values-") }
        .sorted()

    fun ids(names: Iterable<String>) =
      names.withIndex().joinToString("\n") { (index, name) ->
        "    const val ${name.substringBeforeLast('.')} = $index"
      }
    fun array(names: Iterable<String>) = names.joinToString(", ") { "\"$it\"" }

    val file = outputDirectory.get().asFile.resolve("org/littletonrobotics/advantagetune/R.kt")
    file.parentFile.mkdirs()
    file.writeText(
      """
      |// Generated from the resources of the Android app. Do not edit.
      |
      |package org.littletonrobotics.advantagetune
      |
      |/** Identifiers of the resources, with the same names as on Android. */
      |object R {
      |  object string {
      |${ids(strings)}
      |  }
      |
      |  object drawable {
      |${ids(drawables)}
      |  }
      |
      |  object raw {
      |${ids(raws)}
      |  }
      |}
      |
      |/** Names of the resource files that the identifiers stand for. */
      |internal object ResourceNames {
      |  val strings = arrayOf(${array(strings)})
      |  val drawables = arrayOf(${array(drawables)})
      |  val raws = arrayOf(${array(raws)})
      |  val locales = arrayOf(${array(locales)})
      |}
      |"""
        .trimMargin()
    )
  }
}

val generateResourceIds =
  tasks.register<GenerateResourceIds>("generateResourceIds") {
    resourceDirectory.set(androidApp.dir("src/main/res"))
    outputDirectory.set(layout.buildDirectory.dir("generated/resourceIds"))
  }

/** Copies the resources of the Android app to where the desktop app loads them from. */
val syncSharedResources =
  tasks.register<Sync>("syncSharedResources") {
    from(androidApp.dir("src/main/res")) {
      include("values*/strings*.xml", "drawable/*.xml", "raw/*")
      exclude("drawable/ic_launcher*")
    }
    into(layout.buildDirectory.dir("generated/sharedResources/res"))
  }

/** Collects the license texts that are installed with the app. */
val syncLicenses =
  tasks.register<Sync>("syncLicenses") {
    from(androidApp.dir("src/main/res/raw")) { include("license_*.txt") }
    from(layout.projectDirectory.file("THIRD-PARTY-NOTICES.txt"))
    into(layout.buildDirectory.dir("generated/appResources/common/licenses"))
  }

kotlin {
  sourceSets {
    main {
      kotlin.srcDir(androidApp.dir("src/main/java"))
      kotlin.srcDir(generateResourceIds)
      kotlin.exclude(androidOnlySources)
      resources.srcDir(syncSharedResources.map { it.destinationDir.parentFile })
    }
    test { kotlin.srcDir(androidApp.dir("src/test/java")) }
  }
}

val composeVersion = "1.12.2"

dependencies {
  implementation(compose.desktop.currentOs)
  implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
  implementation("org.jetbrains.compose.material3:material3:1.9.0")
  implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
  implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
  implementation(libs.okhttp)
  implementation(libs.protobuf.java)
  implementation("org.json:json:20260814")
  implementation("net.java.dev.jna:jna-platform:5.19.1")

  testImplementation(libs.junit)
  testImplementation("org.jetbrains.compose.ui:ui-test-junit4:$composeVersion")
}

compose.desktop {
  application {
    mainClass = "org.littletonrobotics.advantagetune.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Msi)
      packageName = "AdvantageTune"
      // Keep in step with the version of the iOS app
      packageVersion = "1.1.0"
      description = "NetworkTables viewer and tuning application"
      copyright = "Copyright (c) 2026 Littleton Robotics. All rights reserved."
      licenseFile.set(layout.projectDirectory.file("../LICENSE"))
      appResourcesRootDir.set(layout.buildDirectory.dir("generated/appResources"))
      modules("java.instrument", "jdk.unsupported")

      windows {
        iconFile.set(layout.projectDirectory.file("icons/AdvantageTune.ico"))
        menuGroup = "AdvantageTune"
        shortcut = true
        perUserInstall = true
        dirChooser = true
        upgradeUuid = "5b0c2f0a-7d0e-4a57-9d55-6a1f4a2c8e31"
      }
    }
  }
}

// The license texts have to be collected before they are packaged
tasks
  .matching { it.name.startsWith("prepareAppResources") }
  .configureEach { dependsOn(syncLicenses) }
