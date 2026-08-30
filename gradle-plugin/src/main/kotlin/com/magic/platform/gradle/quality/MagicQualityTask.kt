package com.magic.platform.gradle.quality

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class MagicQualityTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringResources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = projectRoot.get().asFile
        val violations = AndroidRepositoryQualityAnalyzer(
            projectRoot = root,
            sourceFiles = productionSources.files,
            stringResourceFiles = stringResources.files,
        ).analyze()

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Magic Android quality violations:\n" +
                    violations.joinToString(separator = "\n") { it.render(root) }
            )
        }
    }
}
