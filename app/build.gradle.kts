plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val runNumber = project.findProperty("versionCode") as? String
val computedVersionCode = runNumber?.toIntOrNull() ?: 1
val runName = project.findProperty("versionName") as? String
val isCiBuild = !runName.isNullOrBlank()
val computedVersionName = if (isCiBuild) runName else "0.0.$computedVersionCode"

base {
  archivesName.set("OcularNode-v$computedVersionName")
}

android {
  namespace = "io.github.iokkai.ocularnode"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.iokkai.ocularnode"
    minSdk = 26
    targetSdk = 36
    versionCode = computedVersionCode
    versionName = computedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // 剔除第三方套件中未使用的多國語言資源，只保留正體中文與英文

    // Default configuration for OTA updates and Tailscale download (can be overridden by .env)
    buildConfigField("String", "GITHUB_OWNER", "\"iokkai\"")
    buildConfigField("String", "GITHUB_REPO", "\"OcularNode\"")
    buildConfigField("String", "TAILSCALE_APK_URL", "\"https://pkgs.tailscale.com/stable/tailscale-android-universal-1.102.2.apk\"")
  }

  flavorDimensions += "abi"
  productFlavors {
    create("arm") {
      dimension = "abi"
      ndk {
        abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
      }
    }
    create("x86") {
      dimension = "abi"
      ndk {
        abiFilters.addAll(listOf("x86_64", "x86"))
      }
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: "ocularnode_key"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    debug {
      versionNameSuffix = "-debug"
      buildConfigField("String", "BUILD_CHANNEL", "\"Debug\"")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("String", "BUILD_CHANNEL", if (isCiBuild) "\"Release\"" else "\"Local-Release\"")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  androidResources {
    localeFilters += listOf("zh-rTW", "en")
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
        it.systemProperty("robolectric.dependency.repo.id", "central")
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.video)
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
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.mlkit.detection)
  implementation(libs.mlkit.labeling)
  implementation("com.google.zxing:core:3.5.4")
  implementation("androidx.security:security-crypto:1.1.0")
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xannotation-default-target=param-property")
  }
}
