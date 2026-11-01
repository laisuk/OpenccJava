import org.teavm.gradle.api.JSModuleType

plugins {
    java
    id("org.teavm") version "0.16.0"
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":openccjava"))
    implementation(teavm.libs.jso)
    compileOnly("org.teavm:teavm-core:0.16.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

teavm {
    all { mainClass.set("openccjavawasm.ConversionBridge") }
    js {
        targetFileName.set("openccjava.js")
        moduleType.set(JSModuleType.ES2015)
    }
    wasmGC { targetFileName.set("openccjava.wasm") }
}

tasks.register<Copy>("browserJavaScript") {
    group = "build"
    description = "Build the TeaVM JavaScript backend and copy the browser frontend into the distribution directory."
    dependsOn("generateJavaScript")
    from("src/main/web")
    into(layout.buildDirectory.dir("generated/teavm"))
}

tasks.register<Copy>("browserWasmGC") {
    group = "build"
    description =
        "Build the TeaVM WebAssembly GC backend, include its runtime loader, and copy the browser frontend into the distribution directory."
    dependsOn("generateWasmGC", "copyWasmGCRuntime")
    from("src/main/web")
    into(layout.buildDirectory.dir("generated/teavm"))
}

tasks.register("browserIntegration") {
    group = "verification"
    description = "Builds both backends and prepares the browser integration harness."
    dependsOn("browserJavaScript", "browserWasmGC", "officeReferences", "entityReferences")
    doLast {
        val output = layout.buildDirectory.dir("generated/teavm").get().asFile
        copy { from("src/test/web"); into(output) }
        copy { from(layout.buildDirectory.dir("office-fixtures")); into(output.resolve("office-fixtures")) }
        output.resolve("integration.html").writeText(
            file("src/main/web/index.html").readText().replace(
                "</body>",
                "<script type=\"module\" src=\"integration.js\"></script></body>"
            )
        )
    }
}

tasks.register<JavaExec>("officeReferences") {
    group = "verification"
    description = "Generate JVM OfficeHelper references after running src/test/generate-office-fixtures.py."
    dependsOn("testClasses", "prepareOfficeFixtures")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("openccjavawasm.OfficeReference")
    args(layout.buildDirectory.dir("office-fixtures").get().asFile.absolutePath)
}

tasks.register<Copy>("prepareOfficeFixtures") {
    from("src/test/fixtures")
    into(layout.buildDirectory.dir("office-fixtures"))
}

tasks.register<JavaExec>("epubHangProbe") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("openccjavawasm.EpubHangProbe")
    args(providers.gradleProperty("probeBook").getOrElse(""))
}

tasks.register<JavaExec>("entityReferences") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("openccjavawasm.EntityReference")
    args(file("src/test/fixtures/entities").absolutePath,
        layout.buildDirectory.dir("entity-jvm").get().asFile.absolutePath)
}
