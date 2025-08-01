plugins {
    java
    application
}

group = "com.github.laisuk"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":openccjava"))

    // Core CLI parser
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
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

distributions {
    main {
        distributionBaseName.set("OpenccJavaCli")
        contents {
            // Include README.md into docs/
            from("../../README.md") {
                into("docs")
            }

            // Include dicts/ from project root into dist/dicts/
            from("dicts") {
                into("dicts")
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
