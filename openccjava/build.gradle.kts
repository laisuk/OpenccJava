plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.laisuk"
version = "1.0.1"

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON
//    api("com.fasterxml.jackson.core:jackson-databind:2.19.1")
//    api("com.fasterxml.jackson.core:jackson-core:2.19.1")
//    api("com.fasterxml.jackson.core:jackson-annotations:2.19.1")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Benchmarking
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile> {
    options.release.set(11)
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.laisuk.openccjava",
            "Implementation-Title" to "OpenccJava",
            "Implementation-Version" to project.version
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "io.github.laisuk"
            artifactId = "openccjava"
            version = project.version.toString()

            pom {
                name.set("OpenccJava")
                description.set("Java implementation of OpenCC conversion and dictionary support.")
                url.set("https://github.com/laisuk/OpenccJava")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("laisuk")
                        name.set("Laisuk Lai")
                        email.set("laisuk@yahoo.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/laisuk/OpenccJava.git")
                    developerConnection.set("scm:git:ssh://github.com:laisuk/OpenccJava.git")
                    url.set("https://github.com/laisuk/OpenccJava")
                }
            }
        }
    }

    // Optional: publish to local repo directory (for testing)
    /*
    repositories {
        maven {
            name = "localOutput"
            url = layout.buildDirectory.dir("repo").get().asFile.toURI()
        }
    }
    */
}

// Optional: Copy to ~/.m2/repository after publishing
/*
tasks.register<Copy>("copyToM2") {
    dependsOn("publishMavenJavaPublicationToLocalOutputRepository")
    from(layout.buildDirectory.dir("repo"))
    into("${System.getProperty("user.home")}/.m2/repository")
}
*/

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

// Fat JAR generation
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat JAR containing main classes and all dependencies."

    archiveBaseName.set("openccjava-fat")
    archiveClassifier.set("") // keep as main artifact name
    archiveVersion.set(project.version.toString())

    // Reproducible builds
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    // Include our own compiled outputs
    from(sourceSets.main.get().output)

    // Unpack all runtime deps into the fat jar
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    // Prevent JPMS hijack + remove broken signatures from shaded deps
    // - Strip module descriptors (ours and from deps, including MRJAR)
    // - Strip signature files that become invalid when shading
    exclude(
        "module-info.class",
        "META-INF/versions/**/module-info.class",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA"
    )

    // Optional: if you don't need multi-release class variants, keep this.
    // If you *do* need them, remove the next line.
    exclude("META-INF/versions/**")

    // Services: if you rely on ServiceLoader, consider merging service files.
    // With plain Jar we usually just keep first occurrence:
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "OpenccJava",
            "Implementation-Version" to project.version.toString(),
            "Automatic-Module-Name" to "io.github.laisuk.openccjava"
        )
    }
}

// (Optional) make `gradlew build` produce the fat jar by default:
// tasks.named("build") { dependsOn("fatJar") }

// Sign fatjar
val signFatJar by tasks.register<Exec>("signFatJar") {
    group = "signing"
    description = "Signs the existing fat JAR with GPG (does not build it)."

    // This is just a path to the file; task won't try to build it.
    val jarFile = layout.buildDirectory.file("libs/openccjava-fat-${project.version}.jar")

    // Up-to-date tracking
    inputs.file(jarFile)
    outputs.file(jarFile.map { file(it.asFile.absolutePath + ".asc") })

    commandLine(
        "gpg",
        "--armor",
        "--batch",
        "--yes",
        "--detach-sign",
        "--output", jarFile.map { it.asFile.absolutePath + ".asc" }.get(),
        jarFile.get().asFile.absolutePath
    )
}

tasks.register<Exec>("verifyFatJarSig") {
    group = "signing"
    description = "Verifies the fat JAR signature (skips if files missing)."

    val jarFile: Provider<RegularFile> =
        layout.buildDirectory.file("libs/openccjava-fat-${project.version}.jar")

    val ascFile: Provider<RegularFile> = jarFile.flatMap { rf ->
        // wrap File -> Provider<File> before layout.file(...)
        layout.file(provider { rf.asFile.resolveSibling(rf.asFile.name + ".asc") })
    }

    onlyIf {
        val jar = jarFile.get().asFile
        val asc = ascFile.get().asFile
        if (!jar.exists() || !asc.exists()) {
            logger.lifecycle("Skipping verify: missing ${if (!jar.exists()) jar else asc}")
            false
        } else true
    }

    doFirst {
        commandLine(
            "gpg", "--verify",
            ascFile.get().asFile.absolutePath,
            jarFile.get().asFile.absolutePath
        )
    }
}
