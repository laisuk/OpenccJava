// --- OS helpers ---
import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    // GraalVM Native Build Tools (AOT compile to a single exe)
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group = "io.github.laisuk"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // core library (nodeps)
    implementation(project(":openccjava"))

    // CLI parser
    implementation("info.picocli:picocli:4.7.7")

    // Generate GraalVM reflection config for picocli automatically
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
}

// Application entrypoint (used by `run`, Jar manifest, etc.)
application {
    mainClass.set("openccjavacli.Main")
}

// Make sure the CLI JAR is runnable (even though we ship a native exe as well)
tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to "OpenccJavaCli",
            "Implementation-Version" to project.version
            // Note: we intentionally do NOT set Automatic-Module-Name for CLI (no need)
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// JVM distribution ZIP (convenience): binary scripts + docs + external dicts
distributions {
    main {
        distributionBaseName.set("OpenccJavaCli")
        contents {
            // Include README.md into docs/
            from(rootProject.file("README.md")) {
                into("docs")
            }

            // Include external dicts for JVM users who want editable files.
            // Prefer keeping the authoritative copies under src/main/resources/dicts
            // so both JAR and Native image embed them reliably.
            from("dicts") {
                into("dicts")
            }
        }
    }
}

// Picocli annotation processor flags → generate META-INF/native-image config
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

// --- Optional: a “fat JAR” for JVM-only users (kept, micro-tuned & documented) ---
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a runnable fat JAR (CLI + deps)."

    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    archiveClassifier.set("all")
}

// Sign the fat JAR with GPG (optional; requires gpg in PATH)
tasks.register<Exec>("signFatJar") {
    group = "signing"
    description = "Signs the fat JAR using GPG (armored .asc)."
    dependsOn(fatJar)

    val fat = fatJar.get().archiveFile.get().asFile
    commandLine(
        "gpg", "--armor", "--batch", "--yes", "--detach-sign",
        "--output", "${fat.absolutePath}.asc",
        fat.absolutePath
    )
}

// Verify the fat JAR signature if both files exist
tasks.register<Exec>("verifyFatJarSig") {
    group = "signing"
    description = "Verifies the fat JAR signature (skips if files missing)."

    val jarFile = layout.buildDirectory.file("libs/openccjavacli-${project.version}-all.jar")
    val ascFile = jarFile.map { rf -> rf.asFile.resolveSibling("${rf.asFile.name}.asc") }

    onlyIf {
        val j = jarFile.get().asFile
        val a = ascFile.get()
        if (!j.exists() || !a.exists()) {
            logger.lifecycle("Skipping verify: missing ${if (!j.exists()) j else a}")
            false
        } else true
    }

    doFirst {
        commandLine("gpg", "--verify", ascFile.get().absolutePath, jarFile.get().asFile.absolutePath)
    }
}

// --- GraalVM Native Image (AOT) configuration ---
graalvmNative {
    // Let the plugin find GraalVM if Gradle JVM is set to GraalVM; we also hard-pin a launcher below.
    toolchainDetection.set(true)

    binaries {
        named("main") {
            imageName.set("openccjavacli")
            // Build args: fast startup, useful stacktraces; add -Ob during dev for quicker builds.
            buildArgs.addAll("--no-fallback", "-H:+ReportExceptionStackTraces", "-H:+AddAllCharsets")

            // Embed resources: autodetect everything under src/main/resources,
            // and explicitly include our dicts (regex, not glob).
            resources {
                autodetect()
                includedPatterns.add("dicts/.*")
            }

            // Optional: speed dev builds → uncomment for quicker iterations
            // buildArgs.add("-Ob")

            // Optional: prep for future strictness / add metadata
            // buildArgs.addAll("--strict-image-heap", "--enable-sbom", "-H:+BuildReport")
        }
    }
}

// Native distribution ZIP: ship only the native binary + docs (no .args/.json/etc.)
tasks.register<Zip>("nativeDistZip") {
    dependsOn(tasks.named("nativeCompile"))

    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase().let {
        when {
            it.contains("aarch64") || it.contains("arm64") -> "aarch64"
            else -> "x86_64"
        }
    }
    val exeName = if (os.isWindows) "openccjavacli.exe" else "openccjavacli"

    archiveBaseName.set("OpenccJavaCli")
    archiveClassifier.set(
        "native-${
            when {
                os.isWindows -> "windows"
                os.isLinux -> "linux"
                else -> "macos"
            }
        }-$arch"
    )

    // include only the exe, nothing else from nativeCompile dir
    from(layout.buildDirectory.file("native/nativeCompile/$exeName")) {
        into("bin")
        if (!os.isWindows) {
            // Gradle 8+: use the new permissions API (octal/symbolic both okay)
            filePermissions { unix("755") }
            dirPermissions { unix("755") }
        }
    }

    // docs
    from(rootProject.file("README.md")) { into("docs") }

    from("dicts") {
        into("dicts")
    }
}
