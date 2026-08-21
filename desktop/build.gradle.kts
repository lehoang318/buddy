import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.setSrcDirs(listOf("$rootDir/src/cli/main/kotlin", "$rootDir/src/common/main/kotlin"))
            resources.setSrcDirs(listOf("$rootDir/src/cli/main/resources", "$rootDir/src/common/main/resources"))
        }
        test {
            kotlin.setSrcDirs(listOf("$rootDir/src/common/test/kotlin"))
            resources.setSrcDirs(listOf("$rootDir/src/common/test/resources"))
        }
    }
}

application {
    mainClass = "com.example.buddy.cli.MainKt"
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.jline)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnit()
    inputs.dir("$rootDir/src/android/main/res/values")
    doFirst {
        layout.projectDirectory.dir("logs").asFile.mkdirs()
    }
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
}
