plugins {
    id("java-library")
    id("maven-publish")
    id("me.champeau.jmh") version "0.7.3"
}

java {
    withJavadocJar()
    withSourcesJar()
}


group = "com.github.laisuk"
version = "1.0.0"

repositories {
    mavenCentral()
}

//java {
//    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
//}

tasks.withType<JavaCompile> {
    options.release = 11 // This implies sourceCompatibility and targetCompatibility
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.github.laisuk"
            artifactId = "openccjava"
            version = "1.0.0"
        }
    }
//    repositories {
//        maven {
//            name = "localOutput"
//            url = layout.buildDirectory.dir("repo").get().asFile.toURI()
//        }
//    }
}

//tasks.register<Copy>("copyToM2") {
//    dependsOn("publishMavenJavaPublicationToLocalOutputRepository")
//    from(layout.buildDirectory.dir("repo"))
//    into("${System.getProperty("user.home")}/.m2/repository")
//}

dependencies {
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

tasks.test {
    useJUnitPlatform()
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
}

// Fat JAR generation using plain Jar task (manual approach)
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat JAR containing the main classes and all dependencies."

    archiveBaseName.set("openccjava-fat")
    archiveClassifier.set("") // Optional: "" or "all"
    archiveVersion.set(project.version.toString())

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    manifest {
        attributes["Implementation-Title"] = "OpenccJava"
        attributes["Implementation-Version"] = version
        attributes["Automatic-Module-Name"] = "openccjava"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
