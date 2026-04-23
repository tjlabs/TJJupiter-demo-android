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
    implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:2.0.6")
}
