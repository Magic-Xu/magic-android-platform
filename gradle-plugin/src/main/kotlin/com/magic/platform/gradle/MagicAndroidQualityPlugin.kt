package com.magic.platform.gradle

import com.magic.platform.gradle.quality.MagicQualityTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class MagicAndroidQualityPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val qualityCheck = tasks.register<MagicQualityTask>("magicQualityCheck") {
            group = "verification"
            description =
                "Checks package paths, component dependencies, MVI pages, file sizes, and locales."
            projectRoot.set(layout.projectDirectory)
            productionSources.from(
                fileTree("src/main/java") { include("**/*.kt") },
                fileTree("src/main/kotlin") { include("**/*.kt") },
            )
            stringResources.from(
                fileTree("src/main/res") { include("values*/strings.xml") }
            )
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(qualityCheck)
        }
    }
}
