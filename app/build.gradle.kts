
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.colheitagps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.colheitagps.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "2.2.3"
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // WorkManager's durable enqueue acknowledgement exposes ListenableFuture.
    implementation("com.google.guava:guava:33.3.1-android")
}
