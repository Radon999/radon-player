import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

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
    // The shared core (common to the phone and the PC) and the PC's side of it
    sourceSets["main"].kotlin.srcDirs("../core/common", "../core/desktop")
}

// What the installed app needs to know about its build: its version (the update check compares it with GitHub's
// releases) and Tidal's app keys (tools/tidal-keys.sh writes them to ../tidal.properties, as for the phone's build).
val appVersion = "2.1.2"
val buildInfo = layout.buildDirectory.dir("generated/build-info")
val writeBuildInfo by tasks.registering {
    val keys = rootProject.file("tidal.properties")
    inputs.property("version", appVersion)
    if (keys.exists()) inputs.file(keys)
    outputs.dir(buildInfo)
    doLast {
        val p = Properties()
        if (keys.exists()) keys.inputStream().use { p.load(it) }
        val f = buildInfo.get().file("radon-build.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$appVersion\nclient_id=${p.getProperty("client_id", "")}\nclient_secret=${p.getProperty("client_secret", "")}\n")
    }
}
sourceSets["main"].resources.srcDir(buildInfo)
tasks.named("processResources") { dependsOn(writeBuildInfo) }

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui-common"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.jthink:jaudiotagger:3.0.1")   // reading songs' tags, as mutagen does on the Python app
    implementation("net.sf.kxml:kxml2:2.3.0")
    implementation("org.jmdns:jmdns:3.5.9")   
    implementation("net.java.dev.jna:jna-platform:5.14.0")   // Windows: the media keys (RegisterHotKey), as the Python app   // the phone finds the PC on the network (the phone remote)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")   // YouTube's audio, for songs Tidal doesn't have   // XML (the history imports), as Android has it
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "org.omarchy.music.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Radon Player Next"
            packageVersion = appVersion
            description = "Radon Player, rebuilt on one codebase with the phone"
            // mpv and ffmpeg, which the Windows build puts in resources/windows (see Bins): the installer brings its own
            project.layout.projectDirectory.dir("resources").takeIf { it.asFile.isDirectory }?.let { appResourcesRootDir.set(it) }
            windows {
                menuGroup = "Radon Player"
                upgradeUuid = "8f3b3a52-6a8e-4b8f-9a55-2f1d0d6c3a71"
                shortcut = true; menu = true
            }
        }
    }
}
