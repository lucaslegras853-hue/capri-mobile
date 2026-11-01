plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.lucas.capri"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.lucas.capri"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4"
    }

    // La même signature pour toutes les versions : sinon Android refuse de mettre l'appli à jour.
    // (Clé perso pour une appli perso, pas pour le Play Store.)
    signingConfigs {
        create("capri") {
            storeFile = file("capri.keystore")
            storePassword = "caprimobile"
            keyAlias = "capri"
            keyPassword = "caprimobile"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("capri")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("capri")
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
}
