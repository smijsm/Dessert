plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.example.dessert.plugin"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.3")
        pluginVerifier()
        zipSigner()
        bundledPlugin("com.intellij.java")
    }

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
    implementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    implementation("org.mockito:mockito-core:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}