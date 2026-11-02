import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
}

group = "com.pelypets.todolist"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    val bom = platform(SpringBootPlugin.BOM_COORDINATES)
    implementation(bom)
    annotationProcessor(bom)
    developmentOnly(bom)
    testImplementation(bom)

    annotationProcessor(libs.spring.boot.configuration.processor)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.micrometer.metrics)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.flyway.postgresql)
    implementation(libs.micrometer.prometheus)
    implementation(libs.caffeine)
    implementation(libs.bucket4j)
    implementation(libs.bcrypt)
    implementation(libs.google.api.client)
    runtimeOnly(libs.postgresql)

    // `./gradlew bootRun` starts PostgreSQL from compose.yaml.
    developmentOnly(libs.spring.boot.docker.compose)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:all,-processing,-serial")
}

tasks.bootJar {
    archiveFileName = "application.jar"
}

tasks.test {
    useJUnitPlatform()
    // Colima / Docker Desktop: Testcontainers needs the host socket path to mount it into Ryuk.
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    // Colima does not create /var/run/docker.sock; point Testcontainers at its socket unless DOCKER_HOST is set.
    val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}
