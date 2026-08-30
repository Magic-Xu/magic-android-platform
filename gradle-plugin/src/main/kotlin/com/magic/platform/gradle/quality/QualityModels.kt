package com.magic.platform.gradle.quality

import java.io.File

internal const val PRODUCTION_FILE_LINE_LIMIT = 400

internal enum class QualityRule(val label: String) {
    DependencyDirection("dependency-direction"),
    FileSize("file-size"),
    LocaleParity("locale-parity"),
    MviSkeleton("mvi-skeleton"),
    PackagePath("package-path"),
}

internal data class QualityViolation(
    val rule: QualityRule,
    val file: File,
    val message: String,
) {
    fun render(projectRoot: File): String {
        val path = runCatching { file.relativeTo(projectRoot).invariantSeparatorsPath }
            .getOrDefault(file.path)
        return "[${rule.label}] $path: $message"
    }
}
