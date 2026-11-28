import com.android.build.api.artifact.SingleArtifact
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.simpleaibrowser.sab"
    minSdk = 29
    targetSdk = 36
    versionCode = 10
    versionName = "1.6.0"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

  // One APK per CPU architecture plus one universal APK. GeckoView ships a ~150-170 MB native
  // library per ABI, so a per-ABI APK is roughly half the size of the universal one.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "x86_64")
      isUniversalApk = true
    }
  }
}

// Distributable APK names. AGP writes its default names (app-arm64-v8a-debug.apk,
// app-universal-release.apk, ...); this task mirrors every built APK into
// build/outputs/sab/<variant>/ under the public release names:
//   sab-<variant>-arm64-v8a.apk, sab-<variant>-x86_64.apk, sab-<variant>-arm64-x86_64.apk (universal)
// It runs automatically after assembleDebug / assembleRelease and is what CI verifies and publishes.
// Only strings and Providers are captured here: the project enables the Gradle configuration cache.
androidComponents {
  onVariants(selector().all()) { variant ->
    val variantName = variant.name
    val capitalized = variantName.replaceFirstChar { it.uppercase() }
    val apkDir = variant.artifacts.get(SingleArtifact.APK)
    val targetDir = layout.buildDirectory.dir("outputs/sab/$variantName")
    val packageTask = tasks.register<Copy>("packageSab${capitalized}Apks") {
      group = "build"
      description = "Copies the $variantName APKs to build/outputs/sab/$variantName under their release names."
      from(apkDir)
      include("*.apk")
      exclude("app-$variantName.apk")
      into(targetDir)
      rename("^app-arm64-v8a-$variantName\\.apk$", "sab-$variantName-arm64-v8a.apk")
      rename("^app-x86_64-$variantName\\.apk$", "sab-$variantName-x86_64.apk")
      rename("^app-universal-$variantName\\.apk$", "sab-$variantName-arm64-x86_64.apk")
      // Drop APKs of earlier builds so the folder never mixes versions (Task-receiver code only).
      doFirst { destinationDir?.takeIf { it.isDirectory }?.deleteRecursively() }
      doLast {
        val unknown = destinationDir.listFiles()?.filterNot { it.name.startsWith("sab-") }.orEmpty()
        if (unknown.isNotEmpty()) {
          throw GradleException(
            "APK output names changed unexpectedly: ${unknown.joinToString { it.name }}. " +
              "Update the rename() patterns in app/build.gradle.kts."
          )
        }
      }
    }
    tasks.matching { it.name == "assemble$capitalized" }.configureEach { finalizedBy(packageTask) }

    if (variantName == "debug") {
      val universalApkFile = apkDir.map { it.file("app-universal-debug.apk") }
      val standardApkFile = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
      val mirrorDebugTask = tasks.register("mirrorStandardDebugApk") {
        group = "build"
        description = "Mirrors app-universal-debug.apk to app-debug.apk for standard APK installers and emulators."
        mustRunAfter(packageTask)
        inputs.file(universalApkFile)
        outputs.file(standardApkFile)
        doLast {
          val src = universalApkFile.get().asFile
          if (src.isFile) {
            src.copyTo(standardApkFile.get().asFile, overwrite = true)
          }
        }
      }
      tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(mirrorDebugTask) }
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// GeckoView 159+/161 AARs declare minCompileSdk=37 in metadata while maintaining Android 10+ (API 29) runtime compatibility.
// Allow clean builds on SDK 36.1 environments without downgrading GeckoView below 159+.
tasks.configureEach {
  if (name.endsWith("AarMetadata")) {
    enabled = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // Mozilla GeckoView 161 (omni, arm64-v8a + x86_64, Android 10+)
  implementation(libs.mozilla.geckoview)
  // QR code generation for "Share page as QR"
  implementation(libs.zxing.core)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.libtorrent4j)
  implementation(libs.libtorrent4j.android.arm64)
  implementation(libs.libtorrent4j.android.x8664)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
