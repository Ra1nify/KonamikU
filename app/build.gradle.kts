
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version components live in local.properties (git-ignored) as:
//   ver.major = 1
//   ver.minor = 0
// Reading local.properties keeps them out of the repository while still
// letting every local build agree on the same numbers.
val localProps = Properties().apply {
    val f = project.rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val vMajor = localProps.getProperty("ver.major")?.trim().orEmpty().ifBlank { "0" }
val vMinor = localProps.getProperty("ver.minor")?.trim().orEmpty().ifBlank { "0" }

val dateTag = SimpleDateFormat("yyMM").format(Date())
val dateDotTag = SimpleDateFormat("yy.MM").format(Date())

val finalCode = "${dateTag}${vMajor}${vMinor}"
val finalName = "${dateDotTag}.${vMajor}.${vMinor}"

android {
    namespace  = "org.cf0x.konamiku"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.cf0x.konamiku"
        minSdk        = 29
        targetSdk     = 37
        versionCode = finalCode.toInt()
        versionName = finalName

        ndk {
            abiFilters.add("arm64-v8a")
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
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/kotlin/**"
            excludes += "**/kotlinx/**"
            excludes += "**/META-INF/*.kotlin_module"
        }
    }

    buildFeatures {
        compose = true
        buildConfig  = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.core.role)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.material.kolor)
    implementation(libs.androidx.appcompat)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.hiddenapibypass)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}