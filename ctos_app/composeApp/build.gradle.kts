import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.buildkonfig)
}

// Which API the app talks to is chosen when it is built:
//
//   ./gradlew run                      local API (the default)
//   ./gradlew run -PappEnv=production  production API
//
// The commands in the root build.gradle.kts (runLocal, buildProduction, ...)
// just run gradle with the right -PappEnv for you.
val appEnv = (findProperty("appEnv") as String?) ?: "local"
if (appEnv != "local" && appEnv != "production") {
    throw GradleException("appEnv must be 'local' or 'production', not '$appEnv'")
}

val localApiUrl = "http://localhost:9090/api/v1"
// An Android emulator cannot reach the computer as "localhost"; 10.0.2.2 is the computer.
val localAndroidApiUrl = "http://10.0.2.2:9090/api/v1"
// Change this to the address the API is deployed at, or pass -PapiUrl=... when building.
val productionApiUrl = "https://api.example.com/api/v1"

// The website: where an organization is created, and where a department is shared and its members managed.
val websiteUrl = (findProperty("websiteUrl") as String?) ?: if (appEnv == "production") "https://example.com" else "http://localhost:8000"
val androidWebsiteUrl = (findProperty("websiteUrl") as String?) ?: if (appEnv == "production") "https://example.com" else "http://10.0.2.2:8000"

// The version of this build: the number the desktop installer is given. Raise it for every release.
val appVersion = "1.0.0"

// -PapiUrl=http://localhost:9191/api/v1 points a build at any other API, for
// example a second copy of the API running on another port.
val apiUrl = (findProperty("apiUrl") as String?) ?: if (appEnv == "production") productionApiUrl else localApiUrl
val androidApiUrl = (findProperty("apiUrl") as String?) ?: if (appEnv == "production") productionApiUrl else localAndroidApiUrl
logger.lifecycle("CTOS app is built for the $appEnv API ($apiUrl)")

buildkonfig {
    packageName = "com.ctos.client"

    defaultConfigs {
        buildConfigField(STRING, "APP_URL", apiUrl)
        buildConfigField(STRING, "WEBSITE_URL", websiteUrl)
        buildConfigField(STRING, "APP_VERSION", appVersion)
    }
    targetConfigs {
        create("android") {
            buildConfigField(STRING, "APP_URL", androidApiUrl)
            buildConfigField(STRING, "WEBSITE_URL", androidWebsiteUrl)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.ctos.client.db")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    // Code that is the same on the desktop and on Android (both run on the JVM) lives in
    // src/commonJvmMain: plain java.io file access, for example.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("commonJvm") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)

            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended) // For the full set of icons
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            implementation(libs.sqldelight.runtime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)

            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.ktor.client.cio)
            // Watches folders with each operating system's own change notifications
            // (inotify on Linux, FSEvents on macOS, ReadDirectoryChangesW on Windows).
            implementation(libs.directoryWatcher)
        }
    }
}

android {
    namespace = "com.ctos.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ctos.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = appVersion
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// The desktop tests draw screens off-screen, so they need no display.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "com.ctos.client.MainKt"

        nativeDistributions {
            includeAllModules = true

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CtosClient"
            packageVersion = appVersion

            macOS { iconFile.set(project.file("../icons/icon.icns")) }
            windows { iconFile.set(project.file("../icons/icon.ico")) }
            linux { iconFile.set(project.file("../icons/icon.png")) }
        }
    }
}
