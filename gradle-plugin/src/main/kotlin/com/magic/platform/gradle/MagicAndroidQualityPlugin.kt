package com.magic.platform.gradle

import com.magic.platform.gradle.quality.MagicQualityExtension
import com.magic.platform.gradle.quality.MagicQualityTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class MagicAndroidQualityPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val extension = extensions.create<MagicQualityExtension>("magicQuality")
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
            maxProductionFileLines.set(extension.maxProductionFileLines)
            enforceDependencyDirection.set(extension.enforceDependencyDirection)
            enforceMviSkeleton.set(extension.enforceMviSkeleton)
            enforceLocaleParity.set(extension.enforceLocaleParity)
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(qualityCheck)
        }
    }
}
