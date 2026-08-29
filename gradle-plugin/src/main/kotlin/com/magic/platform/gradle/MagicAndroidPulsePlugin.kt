package com.magic.platform.gradle

import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class MagicAndroidPulsePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val configured = AtomicBoolean(false)
        fun configurePulse() {
            if (!configured.compareAndSet(false, true)) return
            dependencies {
                add("implementation", PlatformDependencies.PulseAndroidCompose)
                add("testImplementation", PlatformDependencies.PulseTesting)
            }
        }

        pluginManager.withPlugin("com.android.application") { configurePulse() }
        pluginManager.withPlugin("com.android.library") { configurePulse() }
    }
}
