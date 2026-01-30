import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

application {
    mainClass.set("org.example.MainKt")
}

tasks.withType<JavaExec> {
    val apiKey = localProperties.getProperty("openai.api.key")
    if (apiKey != null) {
        environment("OPENAI_API_KEY", apiKey)
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.openai:openai-java:0.12.0")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.4")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
