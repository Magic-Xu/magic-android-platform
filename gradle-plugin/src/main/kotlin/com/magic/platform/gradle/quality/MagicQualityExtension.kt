package com.magic.platform.gradle.quality

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class MagicQualityExtension @Inject constructor(objects: ObjectFactory) {
    val maxProductionFileLines: Property<Int> = objects.property(Int::class.java).convention(800)
    val enforceDependencyDirection: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)
    val enforceMviSkeleton: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)
    val enforceLocaleParity: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)
}
