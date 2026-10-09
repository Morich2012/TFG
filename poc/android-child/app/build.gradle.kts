plugins {
    id("com.android.application")
}

android {
    namespace = "es.upm.tfg.poc.child"
    compileSdk = 36

    defaultConfig {
        applicationId = "es.upm.tfg.poc.child"
        // Android 11+: BiometricPrompt con CryptoObject y setUserAuthenticationParameters
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Sin librerías: Keystore, BiometricPrompt, HttpURLConnection y org.json vienen con Android.
    testImplementation("junit:junit:4.13.2")
}
