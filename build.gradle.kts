plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.example.compare"
version = "1.0.0"

// Align compileJava (Java 25 JBR) and compileKotlin to JVM 21 target
kotlin {
    jvmToolchain(21)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("net.java.dev.jna:jna:5.14.0")
}

// -------------------------------------------------------------
// Rust Native Library Ingestion Task
// -------------------------------------------------------------
val copyRustLib by tasks.registering(Copy::class) {
    group = "build"
    description = "Copies the compiled Rust library into target resource folders for JNA"

    val osName = System.getProperty("os.name").lowercase()
    val isWin = osName.contains("win")
    val isMac = osName.contains("mac")

    val (srcFile, destDir) = when {
        isWin -> Pair("rust_core/target/release/compare_core.dll", "src/main/resources/win32-x86-64")
        isMac -> {
            val arch = System.getProperty("os.arch").lowercase()
            val folder = if (arch.contains("aarch64") || arch.contains("arm64")) "darwin-aarch64" else "darwin-x86-64"
            Pair("rust_core/target/release/libcompare_core.dylib", "src/main/resources/$folder")
        }
        else -> Pair("rust_core/target/release/libcompare_core.so", "src/main/resources/linux-x86-64")
    }

    from(file(srcFile))
    // Copy to resource folder (classpath) and also to project root (JNA working-dir search)
    into(file(destDir))
    doLast {
        copy {
            from(file(srcFile))
            into(file("."))
        }
    }
}

// Bind resources processing to copy the Rust library beforehand
tasks.processResources {
    dependsOn(copyRustLib)
}

compose.desktop {
    application {
        mainClass = "AppUIKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "theCompare"
            packageVersion = "1.0.0"
        }
    }
}
