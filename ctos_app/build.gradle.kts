plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.buildkonfig) apply false
}

// ── Commands for building the CTOS app against the local or the production API ──
//
//   ./gradlew runLocal               run the desktop app against http://localhost:9090
//   ./gradlew runProduction          run the desktop app against the production API
//   ./gradlew buildLocal             desktop installer + Android debug APK, for the local API
//   ./gradlew buildProduction        desktop installer + Android release APK, for the production API
//   ./gradlew buildExecutable        runnable desktop app (no installer, no Android SDK), production API
//   ./gradlew buildExecutableLocal   the same, for the local API
//
// Each one runs gradle again with -PappEnv=local or -PappEnv=production, which
// composeApp/build.gradle.kts turns into the API address inside the app.
val gradleCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "gradlew.bat" else "./gradlew"

tasks.register<Exec>("runLocal") {
    group = "ctos"
    description = "Runs the desktop app against the local API (http://localhost:9090)."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:run", "-PappEnv=local")
}

tasks.register<Exec>("runProduction") {
    group = "ctos"
    description = "Runs the desktop app against the production API."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:run", "-PappEnv=production")
}

tasks.register<Exec>("buildLocal") {
    group = "ctos"
    description = "Builds the desktop installer and the Android debug APK for the local API."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:packageDistributionForCurrentOS", ":composeApp:assembleDebug", "-PappEnv=local")
}

tasks.register<Exec>("buildProduction") {
    group = "ctos"
    description = "Builds the desktop installer and the Android release APK for the production API."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:packageDistributionForCurrentOS", ":composeApp:assembleRelease", "-PappEnv=production")
}

// The runnable desktop app: a folder holding the launcher and a bundled Java runtime, built for
// the operating system gradle runs on. It needs no installer tooling (WiX, rpm...) and no Android SDK.
val executablePath = "composeApp/build/compose/binaries/main/app/CtosClient/" +
    (if (System.getProperty("os.name").lowercase().contains("windows")) "CtosClient.exe" else "bin/CtosClient")

tasks.register<Exec>("buildExecutable") {
    group = "ctos"
    description = "Builds the runnable desktop app for this operating system, against the production API."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:createDistributable", "-PappEnv=production")
    val message = "Executable: ${rootDir.resolve(executablePath)}"
    doLast { println(message) }
}

tasks.register<Exec>("buildExecutableLocal") {
    group = "ctos"
    description = "Builds the runnable desktop app for this operating system, against the local API."
    workingDir = rootDir
    commandLine(gradleCommand, ":composeApp:createDistributable", "-PappEnv=local")
    val message = "Executable: ${rootDir.resolve(executablePath)}"
    doLast { println(message) }
}
