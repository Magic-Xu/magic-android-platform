import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("magicAndroidApplication") {
            id = "io.github.magic-xu.magic-android-application"
            implementationClass = "com.magic.platform.gradle.MagicAndroidApplicationPlugin"
            displayName = "Magic Android Application Conventions"
            description = "Applies the shared Android application build baseline."
        }
        create("magicAndroidCompose") {
            id = "io.github.magic-xu.magic-android-compose"
            implementationClass = "com.magic.platform.gradle.MagicAndroidComposePlugin"
            displayName = "Magic Android Compose Conventions"
            description = "Applies Compose and its shared dependency baseline."
        }
        create("magicAndroidPulse") {
            id = "io.github.magic-xu.magic-android-pulse"
            implementationClass = "com.magic.platform.gradle.MagicAndroidPulsePlugin"
            displayName = "Magic Android Pulse Conventions"
            description = "Adds the shared Pulse Android and testing baseline."
        }
        create("magicAndroidQuality") {
            id = "io.github.magic-xu.magic-android-quality"
            implementationClass = "com.magic.platform.gradle.MagicAndroidQualityPlugin"
            displayName = "Magic Android Quality Gates"
            description = "Checks package, dependency, MVI, file-size, and locale boundaries."
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "magic-android-platform-gradle-plugin"
        }
    }
    repositories {
        maven {
            name = "verification"
            url = rootProject.layout.buildDirectory
                .dir("publication-verification-repository")
                .get()
                .asFile
                .toURI()
        }
    }
}

val cleanPublicationVerificationRepository by tasks.registering(Delete::class) {
    delete(rootProject.layout.buildDirectory.dir("publication-verification-repository"))
}

tasks.withType<PublishToMavenRepository>().configureEach {
    mustRunAfter(cleanPublicationVerificationRepository)
}
