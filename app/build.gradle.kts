plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val googleDriveEnabled = providers.gradleProperty("sehatiGoogleDriveEnabled").map(String::toBoolean).orElse(false)

val sourceCommit = providers.environmentVariable("GITHUB_SHA")
    .orElse(providers.exec {
        workingDir(rootProject.projectDir)
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    .map { it.trim().takeIf { sha -> sha.matches(Regex("[0-9a-f]{40}")) }.orEmpty() }

android {
    namespace = "com.mkassis37.sehati"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mkassis37.sehati"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.4"
        buildConfigField("String", "SOURCE_COMMIT", "\"${sourceCommit.get()}\"")
        buildConfigField("boolean", "GOOGLE_DRIVE_ENABLED", googleDriveEnabled.get().toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("sehatiStoreFile").orNull
            val storePass = providers.gradleProperty("sehatiStorePassword").orNull
            val keyAliasValue = providers.gradleProperty("sehatiKeyAlias").orNull
            val keyPass = providers.gradleProperty("sehatiKeyPassword").orNull
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
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

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.activity:activity-compose:1.9.3")
    val cameraXVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("org.opencv:opencv:4.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("net.zetetic:sqlcipher-android:4.9.0")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
