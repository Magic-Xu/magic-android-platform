package com.magic.platform.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class MagicAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 24
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        project.file("proguard-rules.pro"),
                    )
                }
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }

            packaging {
                resources {
                    excludes += setOf(
                        "META-INF/AL2.0",
                        "META-INF/LGPL2.1",
                        "META-INF/LICENSE*",
                        "META-INF/NOTICE*",
                    )
                }
            }
        }

        dependencies {
            add("implementation", PlatformDependencies.CoreKtx)
            add("implementation", PlatformDependencies.LifecycleRuntimeKtx)
            add("testImplementation", PlatformDependencies.Junit)
        }
    }
}
