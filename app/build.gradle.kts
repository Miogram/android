import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

fun secret(property: String, env: String): String? =
    localProperties.getProperty(property)?.ifBlank { null } ?: System.getenv(env)?.ifBlank { null }

fun quoted(value: String?): String = "\"${value.orEmpty()}\""

val releaseStore = secret("signing.storeFile", "MIOGRAM_STORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }

val gitCommit = git("rev-parse", "--short=7", "HEAD") ?: "local"
val gitDirty = git("status", "--porcelain") != null
val buildNumber = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
    namespace = "app.miogram"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.miogram"
        minSdk = 31
        targetSdk = 37
        versionCode = buildNumber
        versionName = "0.1.0-$gitCommit" + if (gitDirty) "-dirty" else ""
        buildConfigField("String", "FIREBASE_API_KEY", quoted(localProperties.getProperty("firebase.apiKey")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(localProperties.getProperty("firebase.projectId")))
        buildConfigField("String", "FIREBASE_SENDER_ID", quoted(localProperties.getProperty("firebase.senderId")))
        buildConfigField("String", "MAPS_API_KEY", quoted(localProperties.getProperty("maps.apiKey")))
        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("maps.apiKey").orEmpty()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    flavorDimensions += "servers"
    productFlavors {
        create("sandbox") {
            dimension = "servers"
            buildConfigField("boolean", "TEST_MODE", "true")
            buildConfigField("int", "TELEGRAM_API_ID", localProperties.getProperty("telegram.apiId") ?: "0")
            buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("telegram.apiHash") ?: ""}\"")
            buildConfigField("String", "FIREBASE_APP_ID", quoted(localProperties.getProperty("firebase.appId")))
        }
        create("prod") {
            dimension = "servers"
            applicationIdSuffix = ".prod"
            buildConfigField("boolean", "TEST_MODE", "false")
            buildConfigField("int", "TELEGRAM_API_ID", localProperties.getProperty("telegram.prod.apiId") ?: localProperties.getProperty("telegram.apiId") ?: "0")
            buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("telegram.prod.apiHash") ?: localProperties.getProperty("telegram.apiHash") ?: ""}\"")
            buildConfigField("String", "FIREBASE_APP_ID", quoted(localProperties.getProperty("firebase.prod.appId") ?: localProperties.getProperty("firebase.appId")))
        }
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = secret("signing.storePassword", "MIOGRAM_STORE_PASSWORD")
                keyAlias = secret("signing.keyAlias", "MIOGRAM_KEY_ALIAS")
                keyPassword = secret("signing.keyPassword", "MIOGRAM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("-XX:ReservedCodeCacheSize=128m")
            it.maxHeapSize = "1g"
            it.forkEvery = 40
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        optIn.addAll("androidx.compose.material3.ExperimentalMaterial3Api", "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    implementation(libs.ktproto.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.google.fonts)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.fragment)
    implementation(libs.biometric)
    implementation(libs.browser)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.concurrent.futures)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.maps.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.ktproto.testing)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.work.testing)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
