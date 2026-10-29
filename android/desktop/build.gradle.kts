import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Radon Player on the PC, in Compose like the phone's app, on the shared code. It grows screen by screen
// until it does everything the Python app (omarchy-music) does; until then that one stays the PC app.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui-common"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.jthink:jaudiotagger:3.0.1")   // reading songs' tags, as mutagen does on the Python app
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "org.omarchy.music.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Radon Player Next"
            packageVersion = "2.1.0"
            description = "Radon Player, rebuilt on one codebase with the phone"
            windows {
                menuGroup = "Radon Player"
                upgradeUuid = "8f3b3a52-6a8e-4b8f-9a55-2f1d0d6c3a71"
            }
        }
    }
}
