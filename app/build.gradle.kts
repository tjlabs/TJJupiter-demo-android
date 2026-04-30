import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun String.escapeForBuildConfig(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val authAccessKey: String = providers.gradleProperty("AUTH_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_ACCESS_KEY", "")
val authSecretAccessKey: String = providers.gradleProperty("AUTH_SECRET_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_SECRET_ACCESS_KEY", "")
val jupiterSdkVersion: String = providers.gradleProperty("JUPITER_SDK_VERSION").orNull
    ?: "2.0.7"

android {
    namespace = "com.tjlabs.tjjupiterdemo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tjlabs.tjjupiterdemo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "AUTH_ACCESS_KEY",
            "\"${authAccessKey.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "AUTH_SECRET_ACCESS_KEY",
            "\"${authSecretAccessKey.escapeForBuildConfig()}\""
        )

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:$jupiterSdkVersion")
}

val syncReadmeJupiterVersion by tasks.registering {
    group = "documentation"
    description = "Sync Jupiter SDK version in README.md with JUPITER_SDK_VERSION."

    doLast {
        val readmeFile = rootProject.file("README.md")
        if (!readmeFile.exists()) return@doLast

        val current = readmeFile.readText()
        val updated = current
            .replace(
                Regex("""This demo app uses \*\*TJLabs Jupiter SDK [^*]+\*\*\."""),
                "This demo app uses **TJLabs Jupiter SDK $jupiterSdkVersion**."
            )
            .replace(
                Regex("""implementation\("com\.github\.tjlabs:TJLabsJupiter-sdk-android:[^"]+"\)"""),
                """implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:$jupiterSdkVersion")"""
            )

        if (updated != current) {
            readmeFile.writeText(updated)
            println("README.md synced to Jupiter SDK version: $jupiterSdkVersion")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncReadmeJupiterVersion)
}
