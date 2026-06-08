import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.autka"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autka"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Override per environment. 10.0.2.2 = host loopback from the Android emulator,
        // so `wrangler dev` (localhost:8787) is reachable in debug builds.
        buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8787/\"")
    }

    buildTypes {
        release {
            // TODO: set to your deployed Worker URL, e.g.
            //   https://cargate-backend.<your-subdomain>.workers.dev/
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://cargate-backend.example.workers.dev/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// AGP 9 removed the android.kotlinOptions DSL; Kotlin compiler options now live in the
// Kotlin Gradle plugin's top-level kotlin {} block.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Hilt 2.59.2's aggregating javac step (hiltJavaCompileDebug) reads Kotlin class metadata
// via org.jetbrains.kotlin:kotlin-metadata-jvm, whose bundled version only parses up to
// metadata 2.3.0. Kotlin 2.4.0 emits metadata 2.4.0, so Hilt fails to read the classes.
// The reader sits on the (non-shaded) classpath, so force it to match the Kotlin version.
// Remove once Hilt ships a release that bundles kotlin-metadata-jvm >= 2.3.0.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.osmdroid.android)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
