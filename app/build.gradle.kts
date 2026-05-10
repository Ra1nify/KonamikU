
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties()
val propFile = project.rootProject.file("version.properties")
if (propFile.exists()) {
    propFile.inputStream().use { versionProps.load(it) }
}

val vMajor = (project.findProperty("verMajor") ?: versionProps.getProperty("ver_Major") ?: "0").toString()
val vMinor = (project.findProperty("verMinor") ?: versionProps.getProperty("ver_Minor") ?: "0").toString()

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