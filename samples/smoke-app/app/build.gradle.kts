plugins {
    id("io.github.magic-xu.magic-android-application")
    id("io.github.magic-xu.magic-android-compose")
    id("io.github.magic-xu.magic-android-pulse")
    id("io.github.magic-xu.magic-android-quality")
}

android {
    namespace = "com.magic.platform.smoke"

    defaultConfig {
        applicationId = "com.magic.platform.smoke"
        versionCode = 1
        versionName = "1.0"
    }
}

magicQuality {
    maxProductionFileLines.set(800)
    enforceDependencyDirection.set(true)
    enforceMviSkeleton.set(true)
    enforceLocaleParity.set(true)
}
