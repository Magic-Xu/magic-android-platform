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

tasks.register("verifyVersionConsistency") {
    group = "verification"
    description = "Checks the platform version and an optional stable release tag."
    inputs.property("expectedVersion", platformVersion)
    inputs.property(
        "projectVersions",
        allprojects.associate { project -> project.path to project.version.toString() },
    )
    inputs.property(
        "releaseTag",
        providers.environmentVariable("GITHUB_REF_NAME").orElse(""),
    )
    doLast {
        val expected = inputs.properties.getValue("expectedVersion").toString()
        check(Regex("\\d+\\.\\d+\\.\\d+(?:-SNAPSHOT)?").matches(expected)) {
            "VERSION_NAME must be a semantic release or SNAPSHOT version: $expected"
        }
        val projectVersions = inputs.properties.getValue("projectVersions") as Map<*, *>
        check(projectVersions.values.all { it == expected }) {
            "All platform projects must use VERSION_NAME=$expected."
        }
        val tag = inputs.properties.getValue("releaseTag").toString()
        if (tag.startsWith("v")) {
            check(!expected.endsWith("-SNAPSHOT")) {
                "A stable tag cannot publish a SNAPSHOT version."
            }
            check(tag == "v$expected") {
                "Release tag $tag does not match v$expected."
            }
        }
    }
}

tasks.register("verifyMavenCentralConfig") {
    group = "publishing"
    description = "Validates Maven Central metadata before publishing."
    val required = listOf(
        "GROUP",
        "VERSION_NAME",
        "POM_NAME",
        "POM_DESCRIPTION",
        "POM_URL",
        "POM_INCEPTION_YEAR",
        "POM_LICENSE_NAME",
        "POM_LICENSE_URL",
        "POM_DEVELOPER_ID",
        "POM_DEVELOPER_NAME",
        "POM_DEVELOPER_EMAIL",
        "POM_SCM_URL",
        "POM_SCM_CONNECTION",
        "POM_SCM_DEV_CONNECTION",
    )
    required.forEach { key ->
        inputs.property(key, providers.gradleProperty(key).orElse(""))
    }
    inputs.file(layout.projectDirectory.file("LICENSE"))
    doLast {
        val required = listOf(
            "GROUP",
            "VERSION_NAME",
            "POM_NAME",
            "POM_DESCRIPTION",
            "POM_URL",
            "POM_INCEPTION_YEAR",
            "POM_LICENSE_NAME",
            "POM_LICENSE_URL",
            "POM_DEVELOPER_ID",
            "POM_DEVELOPER_NAME",
            "POM_DEVELOPER_EMAIL",
            "POM_SCM_URL",
            "POM_SCM_CONNECTION",
            "POM_SCM_DEV_CONNECTION",
        )
        val invalid = required.filter { key ->
            val value = inputs.properties.getValue(key).toString()
            value.isBlank() || value.contains("TODO_REPLACE")
        }
        check(invalid.isEmpty()) {
            "Maven Central metadata is incomplete: ${invalid.joinToString(", ")}."
        }
        check(inputs.files.singleFile.isFile) {
            "A repository LICENSE file is required for public publication."
        }
    }
}

tasks.register("releaseCheck") {
    group = "verification"
    description = "Runs the platform, publication, version, and Maven Central release gates."
    dependsOn(
        "platformCheck",
        "publicationCheck",
        "verifyVersionConsistency",
        "verifyMavenCentralConfig",
    )
}
