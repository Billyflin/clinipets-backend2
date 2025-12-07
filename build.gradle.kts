plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("jacoco")
    id("org.sonarqube") version "7.1.0.6387"
}

group = "cl.clinipets"
version = "0.0.1-SNAPSHOT"
description = "backend"

sonar {
    properties {
        property("sonar.projectKey", "clinipets-backend")
        property("sonar.host.url", "http://homeserver.local:9000")
        property("sonar.login", System.getProperty("sonar.token"))
        }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories { mavenCentral() }

dependencies {
    // Google GenAI SDK reciente, no cambiar
    implementation("com.google.genai:google-genai:1.29.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Google ID Token verification
    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.http-client:google-http-client-jackson2:1.43.3")
    implementation("com.google.firebase:firebase-admin:9.2.0")

    implementation("com.google.guava:guava:32.0.1-jre")

    implementation("com.mercadopago:sdk-java:2.8.0") {
        exclude(group = "org.apache.maven.plugins", module = "maven-javadoc-plugin")
    }

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // springdoc: actualizar a versión compatible con Spring Boot 3.5
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    // H2 local y pruebas
    runtimeOnly("com.h2database:h2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Usar mockito-core (sin inline)
    testImplementation("org.mockito:mockito-core:5.12.0")
    // Matchers seguros para Kotlin
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Logging JSON
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
}

kotlin {
    compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
    annotation("org.springframework.stereotype.Service")
    annotation("org.springframework.stereotype.Component")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Djdk.attach.allowAttachSelf=true")
    // Mostrar más contexto al ejecutar tests
    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/Q*",
                        "**/*Application*",
                        "**/package-info.class"
                    )
                }
            }
        )
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(files("build/jacoco/test.exec"))
}

// Regla opcional de cobertura mínima
// tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
//     dependsOn(tasks.test)
//     violationRules {
//         rule { limit { minimum = BigDecimal("0.90") } }
//     }
// }
