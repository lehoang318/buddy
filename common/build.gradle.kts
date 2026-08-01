plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnit()
    inputs.dir("$rootDir/app/src/main/res/values")
    doFirst {
        layout.projectDirectory.dir("logs").asFile.mkdirs()
    }
}
