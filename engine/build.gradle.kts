plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(21) }
dependencies {
    // Pure Kotlin/JVM only: the backend (#22) reuses this module, so no Android dependencies here.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.json.schema.validator)
}
tasks.test {
    // Tests validate the shared schema and example packs that live outside the module.
    val schemaDir = rootProject.file("content-schema")
    inputs.dir(schemaDir)
    systemProperty("contentSchemaDir", schemaDir.absolutePath)
}
