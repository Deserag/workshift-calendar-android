plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.workshift"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.workshift"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // A private release certificate is supplied only through GitHub Actions secrets
    // or local environment variables. Never commit signing materials to this repo.
    val keyStore = System.getenv("WORKSHIFT_RELEASE_KEYSTORE")
    if (!keyStore.isNullOrBlank()) {
        signingConfigs {
            create("production") {
                storeFile = file(keyStore)
                storePassword = System.getenv("WORKSHIFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WORKSHIFT_KEY_ALIAS")
                keyPassword = System.getenv("WORKSHIFT_KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("production")
            }
        }
    }
}


dependencies {
    testImplementation("junit:junit:4.13.2")
}
