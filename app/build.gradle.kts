plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.weatherappproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.weatherappproject"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.okhttp3)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android's copy is only a stub there).
    testImplementation(libs.org.json)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
