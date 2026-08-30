package com.magic.platform.gradle.quality

import java.io.File

internal class AndroidRepositoryQualityAnalyzer(
    private val projectRoot: File,
    sourceFiles: Collection<File>,
    stringResourceFiles: Collection<File>,
) {
    private val sources = sourceFiles.filter(File::isFile).sortedBy(File::getPath)
    private val strings = stringResourceFiles
        .filter { it.isFile && it.parentFile.name.isDefaultOrLocaleValuesDirectory() }
        .sortedBy(File::getPath)

    fun analyze(): List<QualityViolation> = buildList {
        addAll(checkFileSizes())
        addAll(checkPackagePaths())
        addAll(checkDependencyDirections())
        addAll(checkMviSkeletons())
        addAll(checkLocaleParity())
    }.sortedWith(compareBy({ it.rule.label }, { it.file.path }, { it.message }))

    private fun checkFileSizes(): List<QualityViolation> = sources.mapNotNull { file ->
        val lineCount = file.useLines { lines -> lines.count() }
        if (lineCount <= PRODUCTION_FILE_LINE_LIMIT) null else {
            QualityViolation(
                QualityRule.FileSize,
                file,
                "$lineCount lines exceeds the $PRODUCTION_FILE_LINE_LIMIT-line limit",
            )
        }
    }

    private fun checkPackagePaths(): List<QualityViolation> = sources.mapNotNull { file ->
        val sourceRoot = file.sourceRoot() ?: return@mapNotNull null
        val packageName = file.packageName()
            ?: return@mapNotNull QualityViolation(
                QualityRule.PackagePath,
                file,
                "missing package declaration",
            )
        val expectedDirectory = packageName.replace('.', File.separatorChar)
        val actualDirectory = file.parentFile.relativeTo(sourceRoot).path
        if (expectedDirectory == actualDirectory) null else {
            QualityViolation(
                QualityRule.PackagePath,
                file,
                "package $packageName expects directory $expectedDirectory",
            )
        }
    }

    private fun checkDependencyDirections(): List<QualityViolation> = sources.flatMap { file ->
        val source = file.readText()
        val packageName = PACKAGE_PATTERN.find(source)?.groupValues?.get(1)
            ?: return@flatMap emptyList()
        val owner = packageName.layerOwner() ?: return@flatMap emptyList()
        IMPORT_PATTERN.findAll(source).mapNotNull { match ->
            val importedPackage = match.groupValues[1]
            val dependency = importedPackage.layerOwner() ?: return@mapNotNull null
            if (owner.packagePrefix != dependency.packagePrefix) return@mapNotNull null
            val message = dependencyViolation(owner, dependency, importedPackage)
                ?: return@mapNotNull null
            QualityViolation(QualityRule.DependencyDirection, file, message)
        }.toList()
    }

    private fun dependencyViolation(
        owner: LayerOwner,
        dependency: LayerOwner,
        importedPackage: String,
    ): String? = when {
        owner.layer == Layer.Core && dependency.layer != Layer.Core ->
            "core imports ${dependency.layer.label} package $importedPackage"

        owner.layer == Layer.Domain && dependency.layer in setOf(Layer.Feature, Layer.App) ->
            "domain imports ${dependency.layer.label} package $importedPackage"

        owner.layer == Layer.Feature && dependency.layer == Layer.App ->
            "feature imports app package $importedPackage"

        owner.layer == Layer.Feature && dependency.layer == Layer.Feature &&
            owner.featureName != null && dependency.featureName != null &&
            owner.featureName != dependency.featureName ->
            "feature ${owner.featureName} imports sibling feature ${dependency.featureName}"

        else -> null
    }

    private fun checkMviSkeletons(): List<QualityViolation> {
        val pages = sources.mapNotNull { it.featurePage() }.toSet()
        return pages.flatMap { page ->
            val featureRoot = page.featureRoot
            val expected = listOf(
                ExpectedPageFile(
                    File(featureRoot, "contract/${page.pageName}Contract.kt"),
                    listOf("${page.pageName}State", "${page.pageName}Intent", "${page.pageName}Effect"),
                ),
                ExpectedPageFile(
                    File(featureRoot, "presentation/${page.pageName}ViewModel.kt"),
                    listOf("${page.pageName}ViewModel"),
                ),
                ExpectedPageFile(
                    File(featureRoot, "ui/${page.pageName}Screen.kt"),
                    listOf("${page.pageName}Screen"),
                ),
            )
            expected.flatMap { expectation -> expectation.violations() }
        }
    }

    private fun checkLocaleParity(): List<QualityViolation> {
        if (strings.isEmpty()) return emptyList()
        val defaultFile = strings.firstOrNull { it.parentFile.name == "values" }
            ?: return listOf(
                QualityViolation(
                    QualityRule.LocaleParity,
                    strings.first(),
                    "localized strings exist but values/strings.xml is missing",
                )
            )
        val defaultKeys = AndroidStringResources.keys(
            defaultFile,
            excludeNonTranslatable = true,
        )
        return strings.filterNot { it == defaultFile }.flatMap { localized ->
            val localizedKeys = AndroidStringResources.keys(localized)
            buildList {
                val missing = (defaultKeys - localizedKeys).sorted()
                val unexpected = (localizedKeys - defaultKeys).sorted()
                if (missing.isNotEmpty()) {
                    add(
                        QualityViolation(
                            QualityRule.LocaleParity,
                            localized,
                            "missing keys: ${missing.joinToString()}",
                        )
                    )
                }
                if (unexpected.isNotEmpty()) {
                    add(
                        QualityViolation(
                            QualityRule.LocaleParity,
                            localized,
                            "unexpected keys: ${unexpected.joinToString()}",
                        )
                    )
                }
            }
        }
    }

    private fun ExpectedPageFile.violations(): List<QualityViolation> {
        if (!file.isFile) {
            return listOf(
                QualityViolation(QualityRule.MviSkeleton, file, "required page file is missing")
            )
        }
        val source = file.readText()
        return declarations.mapNotNull { declaration ->
            val pattern = Regex(
                "(?m)^(?:public\\s+|internal\\s+)?(?:data\\s+|sealed\\s+)?" +
                    "(?:class|interface|object|fun|typealias)\\s+$declaration\\b"
            )
            if (pattern.containsMatchIn(source)) null else {
                QualityViolation(
                    QualityRule.MviSkeleton,
                    file,
                    "missing $declaration declaration",
                )
            }
        }
    }

    private fun File.packageName(): String? =
        PACKAGE_PATTERN.find(readText())?.groupValues?.get(1)

    private fun File.sourceRoot(): File? {
        val normalized = invariantSeparatorsPath
        val marker = SOURCE_ROOT_MARKERS.firstOrNull(normalized::contains) ?: return null
        return File(normalized.substringBefore(marker) + marker.removeSuffix("/"))
    }

    private fun File.featurePage(): FeaturePage? {
        val normalized = invariantSeparatorsPath
        val marker = "/feature/"
        if (marker !in normalized) return null
        val featureRootPrefix = normalized.substringBefore(marker) + marker
        val relative = normalized.substringAfter(marker)
        val segments = relative.split('/')
        if (segments.size != 3) return null
        val pageName = when {
            segments[1] == "ui" && segments[2].endsWith("Screen.kt") ->
                segments[2].removeSuffix("Screen.kt")

            segments[1] == "presentation" && segments[2].endsWith("ViewModel.kt") ->
                segments[2].removeSuffix("ViewModel.kt")

            else -> return null
        }
        if (pageName.isBlank()) return null
        return FeaturePage(
            featureRoot = File("$featureRootPrefix${segments[0]}"),
            pageName = pageName,
        )
    }

    private fun String.layerOwner(): LayerOwner? {
        val segments = split('.')
        val index = segments.indexOfFirst { it in LAYER_NAMES }
        if (index < 0) return null
        val layer = Layer.fromSegment(segments[index]) ?: return null
        val featureName = if (layer == Layer.Feature) segments.getOrNull(index + 1) else null
        return LayerOwner(
            layer = layer,
            featureName = featureName,
            packagePrefix = segments.take(index).joinToString("."),
        )
    }

    private fun String.isDefaultOrLocaleValuesDirectory(): Boolean {
        if (this == "values") return true
        return LOCALE_VALUES_DIRECTORY_PATTERN.matches(this)
    }

    private data class FeaturePage(val featureRoot: File, val pageName: String)
    private data class ExpectedPageFile(val file: File, val declarations: List<String>)
    private data class LayerOwner(
        val layer: Layer,
        val featureName: String?,
        val packagePrefix: String,
    )

    private enum class Layer(val label: String) {
        App("app"),
        Core("core"),
        Domain("domain"),
        Feature("feature");

        companion object {
            fun fromSegment(segment: String): Layer? = entries.firstOrNull {
                it.label == segment
            }
        }
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("(?m)^package\\s+([\\w.]+)")
        val IMPORT_PATTERN = Regex("(?m)^import\\s+([\\w.]+)")
        val LOCALE_VALUES_DIRECTORY_PATTERN =
            Regex("values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+)")
        val SOURCE_ROOT_MARKERS = listOf("/src/main/java/", "/src/main/kotlin/")
        val LAYER_NAMES = Layer.entries.map(Layer::label).toSet()
    }
}
