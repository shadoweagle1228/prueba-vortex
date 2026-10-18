plugins {
    kotlin("jvm") version "1.9.23" apply false
    kotlin("plugin.spring") version "1.9.23" apply false
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("jacoco")
}

allprojects {
    group = "com.andesretail"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
