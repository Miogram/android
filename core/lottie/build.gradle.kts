plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val rustDir = layout.projectDirectory.dir("src/main/rust/tlottie")
val cargoDir = layout.buildDirectory.dir("cargo")
val hostDir = layout.buildDirectory.dir("host")
val rustTargets = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
    "x86_64" to "x86_64-linux-android",
    "x86" to "i686-linux-android",
)
val cargo = listOf(
    providers.gradleProperty("cargo").orNull,
    System.getenv("CARGO"),
    File(System.getProperty("user.home"), ".cargo/bin/cargo").takeIf { it.exists() }?.path,
).firstOrNull { !it.isNullOrBlank() } ?: "cargo"

android {
    namespace = "cn.ellerotta.core.lottie"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 31
        ndk {
            abiFilters += rustTargets.keys
        }
        externalNativeBuild {
            cmake {
                arguments += "-DTLOTTIE_TARGET_DIR=${cargoDir.get().asFile.path}"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun cargoArgs(target: String?, targetDir: String) = listOfNotNull(
    cargo, "rustc", "--offline", "--locked", "--release", "--features", "c-api", "--lib", "--crate-type", "staticlib",
    target?.let { "--target=$it" }, "--target-dir", targetDir, "--manifest-path", rustDir.file("Cargo.toml").asFile.path,
)

fun run(command: List<String>, hint: String = "") {
    val output = providers.exec {
        commandLine(command)
        isIgnoreExitValue = true
    }
    val exit = output.result.get().exitValue
    if (exit != 0) {
        throw GradleException("${command.first()} exited with $exit$hint\n${output.standardError.asText.get().trim()}")
    }
}

val cargoAndroid = tasks.register("cargoAndroid") {
    inputs.dir(rustDir)
    outputs.dir(cargoDir)
    doLast {
        rustTargets.values.forEach { target ->
            run(cargoArgs(target, cargoDir.get().asFile.path), " for $target, is it installed? rustup target add ${rustTargets.values.joinToString(" ")}")
        }
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(cargoAndroid)
}

val cargoHost = tasks.register<Exec>("cargoHost") {
    inputs.dir(rustDir)
    outputs.dir(hostDir.map { it.dir("cargo") })
    commandLine(cargoArgs(null, hostDir.get().dir("cargo").asFile.path))
}

val buildHostLottie = tasks.register<Exec>("buildHostLottie") {
    dependsOn(cargoHost)
    inputs.dir("src/main/cpp")
    outputs.file(hostDir.map { it.file("cmake/" + System.mapLibraryName("ktlottie")) })
    doFirst {
        run(
            listOf(
                "cmake", "-S", file("src/main/cpp").path, "-B", hostDir.get().dir("cmake").asFile.path, "-DCMAKE_BUILD_TYPE=Release",
                "-DTLOTTIE_TARGET_DIR=${hostDir.get().dir("cargo").asFile.path}",
            ),
        )
    }
    commandLine("cmake", "--build", hostDir.get().dir("cmake").asFile.path)
}

tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("lottie.host").isPresent) {
        dependsOn(buildHostLottie)
        systemProperty("ktlottie.library", hostDir.get().file("cmake/" + System.mapLibraryName("ktlottie")).asFile.path)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
