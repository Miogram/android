plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cn.ellerotta.core.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
}

kotlin {
    compilerOptions {
        optIn.addAll("androidx.compose.material3.ExperimentalMaterial3Api", "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:lottie"))
    implementation(project(":core:media"))
    implementation(libs.libphonenumber)
    implementation(libs.zxing.core)
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    api(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
