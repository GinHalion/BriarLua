
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "lemon.material"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = false
    }

}

dependencies {

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.google.android.material:material:1.14.0")

}
