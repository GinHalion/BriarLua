plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.briarlua"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.briarlua"
        minSdk = 23
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    
    val keystoreFile = rootProject.file("BriarLua.keystore")
    val keystorePassword = System.getenv("BRIARLUA_KEYSTORE_PASSWORD") ?: ""
    val keystoreAlias = System.getenv("BRIARLUA_KEY_ALIAS") ?: "release"
    val hasReleaseKeystore = keystoreFile.exists() && keystorePassword.isNotEmpty()

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystorePassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.2"
    }

    buildFeatures {
        viewBinding = true
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("com.android.tools.build:apksig:8.13.0")
    
    implementation(project(":lemon-material-ui"))
    
    var lifecycle_version = "2.9.4"
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.collection:collection:1.4.4")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.customview:customview:1.2.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.gridlayout:gridlayout:1.1.0")
    implementation("androidx.legacy:legacy-support-core-ui:1.0.0")
    implementation("androidx.legacy:legacy-support-core-utils:1.0.0")
    implementation("androidx.lifecycle:lifecycle-common:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-runtime:$lifecycle_version")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.navigation:navigation-common:2.9.8")
    implementation("androidx.navigation:navigation-fragment:2.9.8")
    implementation("androidx.navigation:navigation-runtime:2.9.8")
    implementation("androidx.navigation:navigation-ui:2.9.8")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.slidingpanelayout:slidingpanelayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.transition:transition:1.5.1")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.browser:browser:1.8.0")
    
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.material:material:1.14.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")

    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    
    implementation("com.jakewharton:disklrucache:2.0.2")
    
    implementation("com.google.code.gson:gson:2.15.0")
    
}
