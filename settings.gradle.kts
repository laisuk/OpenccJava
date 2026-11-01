rootProject.name = "OpenccJava"

include("openccjava", "openccjavacli")

// TeaVM WASM requires JDK 17 or newer.
// Keep the Java 8 modules independent of the WASM module.
if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    include("openccjavawasm")
}