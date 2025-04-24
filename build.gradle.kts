import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


group = "kshare"
version = "2.0.3"

val ver = version

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
}

dependencies {

    fun exposed(module: String) = "org.jetbrains.exposed:exposed-$module:0.42.0"

    implementation("ch.qos.logback", "logback-classic", "1.2.3")
    implementation("org.slf4j", "slf4j-jdk14", "1.7.32")
    implementation("com.h2database", "h2", "1.4.200")
    implementation("com.sparkjava", "spark-kotlin", "1.0.0-alpha")
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.6.0-RC")
    implementation(exposed("core"))
    implementation(exposed("dao"))
    implementation(exposed("jdbc"))
    implementation("com.github.daggerok", "kotlin-html-dsl", "1.0.DOM")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<ShadowJar> {
    archiveFileName.set("kshare-$ver.jar")
}

application {
    mainClass.set("kshare.KShareServer")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}