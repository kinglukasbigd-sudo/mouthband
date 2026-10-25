import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
}

/**
 * Release ad unit IDs come from local.properties (git-ignored). Debug builds always use
 * Google's official test IDs, so a fresh clone builds and never serves real ads in debug.
 */
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { load(it.reader()) }
}

fun localValue(key: String, fallback: String): String =
    localProperties.getProperty(key)?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() } ?: fallback

// Google's published test IDs (https://developers.google.com/admob/android/test-ads).
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

/**
 * Release builds never fall back to the test IDs silently: a release with test ads earns nothing.
 * Each ID must be present, a real AdMob ID and not one of Google's test IDs. A deliberate local QA
 * build of the release variant can opt in to the test IDs with `-PallowTestAdIds=true`.
 */
val adIdPatterns = mapOf(
    "ADMOB_APP_ID" to Regex("ca-app-pub-\\d+~\\d+"),
    "ADMOB_BANNER_ID" to Regex("ca-app-pub-\\d+/\\d+"),
    "ADMOB_INTERSTITIAL_ID" to Regex("ca-app-pub-\\d+/\\d+"),
)
val releaseAdIdProblems: List<String> = adIdPatterns.mapNotNull { (key, pattern) ->
    val value = localValue(key, "")
    when {
        value.isEmpty() -> "$key is missing"
        value in setOf(testAppId, testBannerId, testInterstitialId) -> "$key is Google's test ID"
        !pattern.matches(value) -> "$key is not an AdMob ID"
        else -> null
    }
}
val allowTestAdIds = providers.gradleProperty("allowTestAdIds").map { it.toBoolean() }.getOrElse(false)

/**
 * `-PsideloadPhone=true`: one APK for 64-bit ARM phones with compressed native libraries, small
 * enough to send to a phone and install directly. Play builds never use it: the bundle is split per
 * ABI by Play and keeps the libraries uncompressed and 16 KB aligned.
 */
val sideloadPhone = providers.gradleProperty("sideloadPhone").map { it.toBoolean() }.getOrElse(false)

val checkReleaseAdIds = tasks.register("checkReleaseAdIds") {
    description = "Stops release builds that would ship without real AdMob IDs."
    val problems = releaseAdIdProblems
    val allowed = allowTestAdIds
    doLast {
        if (problems.isNotEmpty() && !allowed) {
            throw GradleException(
                "Release build stopped: ${problems.joinToString("; ")}. Put the real IDs in " +
                    "local.properties (see MANUAL_STEPS.md), or pass -PallowTestAdIds=true for a " +
                    "local test build that shows Google's test ads.",
            )
        }
    }
}

/**
 * Release signing: keystore.properties (git-ignored) in the project root. Without it the
 * release build is signed with the debug key so it still installs for testing; Play will
 * refuse that file (see MANUAL_STEPS.md).
 */
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull
        ?.let { load(it.reader()) }
}
val hasUploadKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.mouthband"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mouthband.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ONNX Runtime is ~35 MB per ABI; 32-bit x86 has no phones (emulators use x86_64).
        ndk {
            abiFilters += if (sideloadPhone) listOf("arm64-v8a") else listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["admobAppId"] = localValue("ADMOB_APP_ID", testAppId)
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${localValue("ADMOB_BANNER_ID", testBannerId)}\"")
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_ID",
                "\"${localValue("ADMOB_INTERSTITIAL_ID", testInterstitialId)}\"",
            )
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
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
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Native libraries stay uncompressed and 16 KB aligned (Play's 16 KB page-size rule).
        jniLibs {
            useLegacyPackaging = sideloadPhone
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // GradleDependency/NewerVersionAvailable need live repository metadata; they are
        // checked by hand when bumping libs.versions.toml instead of failing offline builds.
        checkDependencies = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// detekt 1.23.8 runs on the Kotlin compiler it was built with. Gradle's Kotlin alignment would
// otherwise upgrade its classpath to the project's Kotlin and detekt refuses to start.
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseAdIds) }

configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.detektKotlin.get())
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.onnxruntime.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
