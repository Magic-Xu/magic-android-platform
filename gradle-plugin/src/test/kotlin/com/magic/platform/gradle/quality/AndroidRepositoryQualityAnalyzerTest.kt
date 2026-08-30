package com.magic.platform.gradle.quality

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidRepositoryQualityAnalyzerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsACompletePageAndMatchingLocales() {
        source(
            "src/main/kotlin/com/example/feature/home/contract/HomeContract.kt",
            """
            package com.example.feature.home.contract

            data class HomeState(val count: Int = 0)
            sealed interface HomeIntent
            sealed interface HomeEffect
            """,
        )
        source(
            "src/main/kotlin/com/example/feature/home/presentation/HomeViewModel.kt",
            """
            package com.example.feature.home.presentation

            class HomeViewModel
            """,
        )
        source(
            "src/main/kotlin/com/example/feature/home/ui/HomeScreen.kt",
            """
            package com.example.feature.home.ui

            fun HomeScreen() = Unit
            """,
        )
        strings(
            "src/main/res/values/strings.xml",
            mapOf("app_name" to "Example", "home_ready" to "Ready"),
        )
        strings(
            "src/main/res/values-zh-rCN/strings.xml",
            mapOf("app_name" to "示例", "home_ready" to "就绪"),
        )

        assertTrue(analyze().isEmpty())
    }

    @Test
    fun reportsForbiddenLayerAndSiblingFeatureImports() {
        source(
            "src/main/kotlin/com/example/core/files/FileStore.kt",
            """
            package com.example.core.files

            import com.example.feature.home.contract.HomeState
            """,
        )
        source(
            "src/main/kotlin/com/example/feature/share/ShareCoordinator.kt",
            """
            package com.example.feature.share

            import com.example.feature.home.contract.HomeState
            """,
        )

        val violations = analyze()

        assertEquals(2, violations.count { it.rule == QualityRule.DependencyDirection })
        assertTrue(violations.any { "core imports feature" in it.message })
        assertTrue(violations.any { "imports sibling feature home" in it.message })
    }

    @Test
    fun ignoresLayerWordsInExternalPackages() {
        source(
            "src/main/kotlin/com/example/core/files/FileStore.kt",
            """
            package com.example.core.files

            import third.party.feature.flags.Toggle
            """,
        )

        assertTrue(analyze().isEmpty())
    }

    @Test
    fun reportsPlatformAndIoImportsFromFeatureUi() {
        val forbiddenImports = listOf(
            "android.content.Intent",
            "androidx.activity.compose.rememberLauncherForActivityResult",
            "androidx.compose.ui.platform.LocalContext",
            "androidx.core.content.FileProvider",
            "com.google.android.gms.tasks.Task",
            "com.google.firebase.FirebaseApp",
            "java.io.File",
            "okhttp3.OkHttpClient",
            "retrofit2.Retrofit",
        )
        source(
            "src/main/kotlin/com/example/feature/home/ui/HomeContent.kt",
            buildString {
                appendLine("package com.example.feature.home.ui")
                appendLine()
                forbiddenImports.forEach { appendLine("import $it") }
            },
        )

        val violations = analyze().filter { it.rule == QualityRule.UiPlatformBoundary }

        assertEquals(forbiddenImports.size, violations.size)
        forbiddenImports.forEach { forbiddenImport ->
            assertTrue(violations.any { forbiddenImport in it.message })
        }
    }

    @Test
    fun allowsPlatformApisInAppEffectsAndNetworkApisInFeatureData() {
        source(
            "src/main/kotlin/com/example/app/effect/ShareEffectHandler.kt",
            """
            package com.example.app.effect

            import android.content.Intent
            """,
        )
        source(
            "src/main/kotlin/com/example/feature/home/data/HomeRepository.kt",
            """
            package com.example.feature.home.data

            import okhttp3.OkHttpClient
            """,
        )

        assertTrue(analyze().isEmpty())
    }

    @Test
    fun reportsPackagePathAndFileSizeViolations() {
        source(
            "src/main/kotlin/com/example/core/WrongLocation.kt",
            buildString {
                appendLine("package com.example.core.platform")
                repeat(PRODUCTION_FILE_LINE_LIMIT) { index ->
                    appendLine("val line$index = $index")
                }
            },
        )

        val violations = analyze()

        assertTrue(violations.any { it.rule == QualityRule.PackagePath })
        assertTrue(violations.any { it.rule == QualityRule.FileSize })
    }

    @Test
    fun reportsMissingMviFileAndDeclaration() {
        source(
            "src/main/kotlin/com/example/feature/home/contract/HomeContract.kt",
            """
            package com.example.feature.home.contract

            data class HomeState(val count: Int = 0)
            sealed interface HomeIntent
            """,
        )
        source(
            "src/main/kotlin/com/example/feature/home/ui/HomeScreen.kt",
            """
            package com.example.feature.home.ui

            fun HomeScreen() = Unit
            """,
        )

        val violations = analyze()

        assertTrue(
            violations.any {
                it.rule == QualityRule.MviSkeleton &&
                    it.file.name == "HomeContract.kt" &&
                    "HomeEffect" in it.message
            }
        )
        assertTrue(
            violations.any {
                it.rule == QualityRule.MviSkeleton &&
                    it.file.name == "HomeViewModel.kt" &&
                    "required page file is missing" in it.message
            }
        )
    }

    @Test
    fun reportsMissingAndUnexpectedLocaleKeys() {
        strings(
            "src/main/res/values/strings.xml",
            mapOf("app_name" to "Example", "home_ready" to "Ready"),
        )
        strings(
            "src/main/res/values-fr/strings.xml",
            mapOf("app_name" to "Exemple", "orphan" to "Orphelin"),
        )

        val violations = analyze()

        assertTrue(violations.any { "missing keys: string/home_ready" in it.message })
        assertTrue(violations.any { "unexpected keys: string/orphan" in it.message })
    }

    @Test
    fun ignoresNonTranslatableDefaultResourcesForLocaleParity() {
        write(
            "src/main/res/values/strings.xml",
            """
            <resources>
                <string name="app_name">Example</string>
                <string name="fgs_special_use_subtype" translatable="false">Overlay service</string>
            </resources>
            """.trimIndent(),
        )
        strings(
            "src/main/res/values-fr/strings.xml",
            mapOf("app_name" to "Exemple"),
        )

        assertTrue(analyze().isEmpty())
    }

    @Test
    fun ignoresNonLocaleValuesDirectories() {
        strings(
            "src/main/res/values/strings.xml",
            mapOf("app_name" to "Example", "home_ready" to "Ready"),
        )
        strings(
            "src/main/res/values-night/strings.xml",
            mapOf("app_name" to "Example at night"),
        )

        assertTrue(analyze().isEmpty())
    }

    private fun analyze(): List<QualityViolation> {
        val root = temporaryFolder.root
        return AndroidRepositoryQualityAnalyzer(
            projectRoot = root,
            sourceFiles = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList(),
            stringResourceFiles = root.walkTopDown()
                .filter { it.isFile && it.name == "strings.xml" }
                .toList(),
        ).analyze()
    }

    private fun source(path: String, content: String): File = write(path, content.trimIndent())

    private fun strings(path: String, values: Map<String, String>): File {
        val content = buildString {
            appendLine("<resources>")
            values.forEach { (name, value) ->
                appendLine("    <string name=\"$name\">$value</string>")
            }
            appendLine("</resources>")
        }
        return write(path, content)
    }

    private fun write(path: String, content: String): File {
        val file = temporaryFolder.root.resolve(path)
        check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        file.writeText(content)
        return file
    }
}
