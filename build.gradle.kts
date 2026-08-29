plugins {
    base
}

val publicationRepository = layout.buildDirectory.dir("publication-verification-repository")
val platformGroup = providers.gradleProperty("GROUP")
val platformVersion = providers.gradleProperty("VERSION_NAME")

allprojects {
    group = platformGroup.get()
    version = platformVersion.get()
}

tasks.register("platformCheck") {
    group = "verification"
    description = "Runs platform plugin tests."
    dependsOn(":gradle-plugin:check")
}

val verifyPublicationShape by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies that all plugin markers resolve to one implementation artifact."
    dependsOn(":gradle-plugin:cleanPublicationVerificationRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToVerificationRepository")
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/verify-publication-shape.sh").asFile.absolutePath,
        publicationRepository.get().asFile.absolutePath,
        platformGroup.get(),
        platformVersion.get(),
    )
}

tasks.register("publicationCheck") {
    group = "verification"
    description = "Publishes and verifies the single-artifact platform release shape."
    dependsOn(verifyPublicationShape)
}
