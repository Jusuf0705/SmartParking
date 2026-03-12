// build.gradle (app nivo) — usklađen sa tvojim postojećim projektom
// Nije potrebno ništa mijenjati osim provjere da su svi blokovi prisutni.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.smartparking"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartparking"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        viewBinding = true   // ← potrebno za LoginActivity.java (ActivityLoginBinding)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Firebase BOM — upravlja verzijama svih Firebase libova
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")

    // Material 3
    implementation("com.google.android.material:material:1.12.0")

    // CardView (logo badge u activity_login.xml)
    implementation("androidx.cardview:cardview:1.0.0")

    // Standardni Android libovi
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}