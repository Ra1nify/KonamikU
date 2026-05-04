plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace  = "org.cf0x.konamiku"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.cf0x.konamiku"
        minSdk        = 29
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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

    buildFeatures {
        compose = true
        buildConfig  = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.1.0"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.core.role)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.material.kolor)
    implementation(libs.androidx.appcompat)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

}


tasks.register<Exec>("buildRustArm64") {
    workingDir = file("src/main/jni/pmm-rust")
    commandLine("cargo", "ndk",
        "--target", "arm64-v8a",
        "--platform", "26",
        "--",
        "build", "--release"
    )
}

tasks.named("preBuild") {
    dependsOn("buildRustArm64")
}