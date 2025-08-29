import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

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

// Restrict the sources JAR to Java sources only
tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // allow only actual Java sources & descriptors
    include("**/*.java", "**/module-info.java", "**/package-info.java")
    // hard-exclude everything JNI/native-ish living under src/main/java/opencc
    exclude(
        "**/dicts/**"
    )
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
                description.set("Java implementation of Traditional and Simplified Chinese text conversion with dictionary support.")
                url.set("https://github.com/laisuk/OpenccJava")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://raw.githubusercontent.com/laisuk/OpenccJava/master/LICENSE")
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
                    connection.set("scm:git:https://github.com/laisuk/OpenccJava.git")
                    developerConnection.set("scm:git:ssh://git@github.com/laisuk/OpenccJava.git")
                    url.set("https://github.com/laisuk/OpenccJava")
                }
            }
        }
    }

    repositories {
        maven {
            name = "ossrh-staging-api"
            // Staging for releases:
            url = uri(
                if (version.toString().endsWith("SNAPSHOT"))
                    "https://central.sonatype.com/repository/maven-snapshots/"
                else
                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?
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

//signing {
//    useGpgCmd()
//    sign(publishing.publications["mavenJava"])
//}

signing {
    useGpgCmd()
    // Only sign if we’re not publishing locally
    val isLocal = gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") || it.contains("LocalOutput") }
    if (!isLocal) {
        sign(publishing.publications["mavenJava"])
    }
}

val portalUser = (System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?)
val portalPass = (System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?)
val portalAuth: String = Base64.getEncoder().encodeToString("${portalUser}:${portalPass}".toByteArray())

// Use your root namespace (groupId root), e.g. "io.github.laisuk"
val portalNamespace = "io.github.laisuk"

// Triggers the Portal to ingest the staging upload so it shows up in central.sonatype.com
tasks.register("uploadToPortal") {
    group = "publishing"
    description = "Notify Central Portal to ingest the last staging upload"
    doLast {
        require(!portalUser.isNullOrBlank() && !portalPass.isNullOrBlank()) {
            "Missing OSSRH portal credentials (OSSRH_USERNAME/OSSRH_PASSWORD or ossrhUsername/ossrhPassword)."
        }
        val url = "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$portalNamespace"
        val client = HttpClient.newHttpClient()
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $portalAuth")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Portal upload failed: ${resp.statusCode()} ${resp.body()}")
        } else {
            println("Portal upload ok: ${resp.statusCode()}")
        }
    }
}

// Typical CI sequence: publish → uploadToPortal
tasks.named("publish") { finalizedBy("uploadToPortal") }