// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

plugins {
    application
    jacoco
    kotlin("jvm") version "1.5.30"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("io.gitlab.arturbosch.detekt") version "1.18.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(platform("software.amazon.awssdk:bom:2.17.38"))
    implementation(platform("io.projectreactor:reactor-bom:2020.0.11"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:macie2")

    implementation("info.picocli:picocli:4.6.1")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.10")

    implementation("org.slf4j:slf4j-log4j12:1.7.32")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("io.projectreactor:reactor-core")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("com.jayway.jsonpath:json-path:2.6.0")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta2")
    implementation("org.apache.avro:avro:1.10.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")

    implementation("org.apache.parquet:parquet-hadoop:1.12.0")
    implementation("org.apache.parquet:parquet-avro:1.12.0")
    implementation("org.apache.hadoop:hadoop-client:3.3.1") {
        exclude("org.slf4j")
    }
    implementation("org.apache.poi:poi:5.0.0")
    implementation("org.apache.poi:poi-ooxml:5.0.0")

    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("executable")
    archiveVersion.set("")
    isZip64 = true
}

subprojects {
    detekt {
        baseline = file("${rootProject.projectDir}/detekt-baseline.xml")
    }
}

application {
    // Define the main class for the application.
    mainClass.set("com.amazon.macie.samples.reveal.AppKt")
}
