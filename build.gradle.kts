plugins {
    java
    application
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.laisuk"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Core CLI parser
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")

    // JSON serialization/deserialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.1")

    // Unit testing
    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
    mainClass.set("openccjavacli.Main")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "openccjavacli.Main"
    }
}

tasks.test {
    useJUnitPlatform()
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
}

distributions {
    main {
        distributionBaseName.set("opencccli")
        contents {
            // Include README.md into docs/
            from("README.md") {
                into("docs")
            }

            // Include dicts/ from project root into dist/dicts/
            from("dicts") {
                into("bin/dicts")
            }
        }
    }
}

val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat JAR including dependencies."

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    archiveClassifier.set("all")
}
