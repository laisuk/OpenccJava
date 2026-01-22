import java.net.HttpURLConnection
import java.net.URI
import java.util.*

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.laisuk"
version = "1.2.0"

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
    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Benchmarking
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile>().configureEach {
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    } else {
        // Fallback for Gradle running on Java 8
//        sourceCompatibility = "1.8"
//        targetCompatibility = "1.8"
    }
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

// Helper: turn "1.8"/"8"/"17" into major bytecode (52/61/etc.)
fun majorFromJavaVersion(vRaw: String): Int {
    val n = if (vRaw.startsWith("1.")) vRaw.substring(2) else vRaw
    return n.toInt() + 44
}

tasks.withType<Jar>().configureEach {
    doFirst {
        val cj = tasks.withType<JavaCompile>().findByName("compileJava")
        val rawVer = when {
            cj?.options?.release?.isPresent == true -> cj.options.release.get().toString()
            cj != null -> cj.targetCompatibility
            else -> JavaVersion.current().toString()
        }
        val bytecodeJava = if (rawVer == "8") "1.8" else rawVer
        val major = majorFromJavaVersion(bytecodeJava)

        manifest {
            attributes(
                "Automatic-Module-Name" to "io.github.laisuk.openccjava",
                "Implementation-Title" to "OpenccJava",
                "Implementation-Version" to project.version,
                "Major-Bytecode-Number" to major.toString(),
                "Bytecode-Java-Version" to bytecodeJava
            )
        }
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
                description.set("Pure Java implementation of Traditional and Simplified Chinese text conversion with dictionary support.")
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
                // Use Central Portal token credentials
                username = findProperty("centralUsername") as String? ?: System.getenv("CENTRAL_USERNAME")
                password = findProperty("centralPassword") as String? ?: System.getenv("CENTRAL_PASSWORD")
            }
        }
    }
}

//signing {
//    useGpgCmd()
//    sign(publishing.publications["mavenJava"])
//}

signing {
    useGpgCmd()
    // Only sign if we’re not publishing locally
    val isLocal =
        gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") || it.contains("LocalOutput") }
    if (!isLocal) {
        sign(publishing.publications["mavenJava"])
    }
}

val portalUser: String? = (findProperty("centralUsername") as String? ?: System.getenv("CENTRAL_USERNAME"))
val portalPass: String? = (findProperty("centralPassword") as String? ?: System.getenv("CENTRAL_PASSWORD"))
val portalAuth: String = Base64.getEncoder().encodeToString("${portalUser}:${portalPass}".toByteArray())

// Use your root namespace (groupId root), e.g. "io.github.laisuk"
val portalNamespace = "io.github.laisuk"

// Triggers the Portal to ingest the staging upload so it shows up in central.sonatype.com
tasks.register("uploadToPortal") {
    group = "publishing"
    description = "Notify Central Portal to ingest the last staging upload"
    doLast {
        val user = portalUser ?: ""
        val pass = portalPass ?: ""
        require(user.isNotEmpty() && pass.isNotEmpty()) {
            "Missing Central Portal credentials (CENTRAL_PORTAL_TOKEN_USER/_PASS or gradle.properties)."
        }

        val auth = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
        val urlStr = "https://ossrh-staging-api.central.sonatype.com/" +
                "manual/upload/defaultRepository/$portalNamespace?publishing_type=user_managed"

        val uri = URI(urlStr)
        val url = uri.toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $auth")
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        // Nobody to send; just open/close the stream to issue the request
        conn.outputStream.use { /* empty POST body */ }

        val code = conn.responseCode
        val body = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        } catch (_: Exception) {
            ""
        }

        conn.disconnect()

        if (code !in 200..299) {
            throw GradleException("Portal upload failed: $code ${body.take(500)}")
        } else {
            println("Portal upload ok: $code")
        }
    }
}

// Only wire the ingestion step for non-SNAPSHOT publishes
if (!version.toString().endsWith("SNAPSHOT")) {
    tasks.named("publish") { finalizedBy("uploadToPortal") }
}