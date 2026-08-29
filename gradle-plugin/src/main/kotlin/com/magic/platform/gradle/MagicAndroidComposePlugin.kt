package com.magic.platform.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class MagicAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                buildFeatures.compose = true
            }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
        }

        dependencies {
            add("implementation", platform(PlatformDependencies.ComposeBom))
            add("implementation", PlatformDependencies.ActivityCompose)
            add("implementation", PlatformDependencies.LifecycleRuntimeCompose)
            add("implementation", PlatformDependencies.LifecycleViewModelCompose)
            add("implementation", PlatformDependencies.ComposeUi)
            add("implementation", PlatformDependencies.ComposeUiGraphics)
            add("implementation", PlatformDependencies.ComposeUiToolingPreview)
            add("implementation", PlatformDependencies.Material3)
            add("debugImplementation", PlatformDependencies.ComposeUiTooling)
            add("debugImplementation", PlatformDependencies.ComposeUiTestManifest)
        }
    }
}
