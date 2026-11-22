plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":engine"))
    testImplementation(libs.junit)
    testImplementation(libs.json.schema.validator)
}
tasks.test {
    val sample = rootProject.file("content-schema/examples/mathgen-sample.v1.json")
    inputs.file(sample)
    systemProperty("mathgenSample", sample.absolutePath)
}
tasks.register<JavaExec>("samplePack") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("fi.skillit.mathgen.SamplePackKt")
    args(rootProject.file("content-schema/examples/mathgen-sample.v1.json").absolutePath)
}
