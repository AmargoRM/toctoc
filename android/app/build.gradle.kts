import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// ---- App version. Bump these two on every release. ----
// versionCode MUST strictly increase; the in-app updater compares it.
val appVersionCode = 8
val appVersionName = "1.0.7"

android {
    namespace = "app.toctoc.timbre"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.toctoc.timbre"
        minSdk = 24          // Android 7.0 -> maximiza el rango de dispositivos
        targetSdk = 35       // Android 15
        versionCode = appVersionCode
        versionName = appVersionName

        // URLs configurables por build (se pueden sobreescribir sin tocar código)
        buildConfigField("String", "DEFAULT_NTFY_SERVER", "\"https://ntfy.sh\"")
        // Página que abre el visitante (GitHub Pages). Repo: amargorm/toctoc.
        buildConfigField(
            "String",
            "RING_PAGE_BASE",
            "\"https://amargorm.github.io/toctoc/timbre/\""
        )
        // Página para recibir el timbre en otro teléfono (iPhone) vía ntfy.
        buildConfigField(
            "String",
            "RECIBIR_PAGE_BASE",
            "\"https://amargorm.github.io/toctoc/timbre/recibir/\""
        )
        // Apunta al último Release: 'releases/latest/download/<asset>' siempre
        // resuelve a la versión más reciente. No requiere GitHub Pages.
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://github.com/AmargoRM/toctoc/releases/latest/download/latest.json\""
        )
    }

    // Firma estable y compartida por TODOS los builds (CI y local) para que las
    // actualizaciones se instalen ENCIMA sin tener que desinstalar la app.
    // No es una firma de Play Store: es una clave propia versionada en el repo.
    signingConfigs {
        create("shared") {
            storeFile = file("../keystore/toctoc.keystore")
            storePassword = "toctoc123"
            keyAlias = "toctoc"
            keyPassword = "toctoc123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Firebase Cloud Messaging (push que llega con la app cerrada / dormida)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
