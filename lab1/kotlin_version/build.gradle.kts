import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "ru.bmstu.neuro"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("BigDecimalMath.jar"))
    implementation("com.google.guava:guava:31.1-jre")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.useK2 = true
    kotlinOptions.freeCompilerArgs = kotlinOptions.freeCompilerArgs +
            "-XDump-directory=${buildDir}/ir/" +
            "-Xphases-to-dump-after=ValidateIrBeforeLowering"
}

application {
    mainClass.set("Main1Kt")
}