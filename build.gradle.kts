import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    application
}

group = "kshare"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback", "logback-classic", "1.2.3")
    implementation("org.slf4j", "slf4j-jdk14", "1.7.25")
    implementation("com.h2database", "h2", "1.4.200")
    implementation("com.sparkjava", "spark-kotlin", "1.0.0-alpha")
    implementation("com.google.code.gson", "gson", "2.8.8")
    implementation("org.jetbrains.exposed", "exposed-core", "0.34.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.34.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.34.1")
    implementation("com.github.daggerok", "kotlin-html-dsl", "1.0.DOM")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<ShadowJar> {
    archiveFileName.set("kshare.jar")
}

application {
    mainClass.set("kshare.Main")
    mainClassName = "kshare.Main"
}