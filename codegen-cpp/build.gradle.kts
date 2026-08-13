plugins {
    // Keep the generator itself publishable; the consumer-facing
    // `buildsrc.convention.codegen-cpp` convention is intentionally separate
    // because it wires a Ktgen consumer to this artifact.
    id("buildsrc.convention.published-module")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.ktgenApi)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.20")
}

kotlin {
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
}
