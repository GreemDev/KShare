import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


group = "kshare"
version = "1.0"

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
}

dependencies {

    fun exposed(module: String) = "org.jetbrains.exposed:exposed-$module:0.34.2"

    implementation("ch.qos.logback", "logback-classic", "1.2.3")
    implementation("org.slf4j", "slf4j-jdk14", "1.7.32")
    implementation("com.h2database", "h2", "1.4.200")
    implementation("com.sparkjava", "spark-kotlin", "1.0.0-alpha")
    implementation("com.google.code.gson", "gson", "2.8.8")
    implementation(exposed("core"))
    implementation(exposed("dao"))
    implementation(exposed("jdbc"))
    implementation("com.github.daggerok", "kotlin-html-dsl", "1.0.DOM")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<ShadowJar> {
    archiveFileName.set("kshare.jar")
}

application {
    mainClass.set("kshare.KShare")
}
