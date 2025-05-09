plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "1.9.0"
}

android {
    namespace = "com.h3110w0r1d.clipboardsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.h3110w0r1d.clipboardsync"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        if (variant.buildType.name == "release")
            variant.outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"
                    val apkName = "cs-${defaultConfig.versionName}-${abi}-${variant.buildType.name}.apk"
                    output.outputFileName = apkName
                }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)

    implementation(libs.mmkv)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.org.eclipse.paho.mqttv5.client)

    implementation(platform(libs.androidx.compose.bom))

    compileOnly(files("libs/api-82.jar"))
}