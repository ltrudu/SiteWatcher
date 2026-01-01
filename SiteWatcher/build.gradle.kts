plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.ltrudu.sitewatcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ltrudu.sitewatcher"
        minSdk = 34
        targetSdk = 36
        versionCode = 7
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null && File(keystoreFile).exists()) {
                storeFile = File(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            signingConfig = signingConfigs.getByName("release")
            if (keystoreFile != null && File(keystoreFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }

    applicationVariants.all {
        val variant = this
        val buildType = variant.buildType.name
        val versionName = variant.versionName

        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "SiteWatcher-${versionName}-${buildType}.apk"
        }
    }

    // Configure base archive name for bundles
    base.archivesName.set("SiteWatcher-${defaultConfig.versionName}")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // OkHttp
    implementation(libs.okhttp)

    // Jsoup
    implementation(libs.jsoup)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}