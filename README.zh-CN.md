# Magic Android Platform

[English](README.md) | 简体中文

Magic Android App 共享的 Gradle 构建约定和质量门禁。

Android 基线、Compose、Pulse 和仓库质量检查可以独立选择。产品代码继续使用标准 Android
Gradle DSL 配置应用身份和版本信息。

跨会话事实源是
[platform-blueprint.md](docs/engineering/platform-blueprint.md)。修改平台边界、选择 Consumer
App、更新 Factory 或准备发布前，应先阅读该文档。

## 发布形态

四个插件 ID 是同一个发布单元中的四种能力，不是四个独立版本的组件。每次发布生成一个
实现 JAR 和四个轻量 Gradle plugin marker POM，同时发布源码、Dokka javadocs、Gradle
module metadata、完整 POM 元数据和签名。所有插件 ID 使用同一个平台版本并一起发布。

源码位于独立的 `magic-android-platform` Git 仓库，发布制品位于 Maven Central。当前稳定版
是 `1.0.0`，也是 Android App Factory 已验证的默认版本。

## 使用已发布的平台

在 `settings.gradle.kts` 中同时配置插件仓库和依赖仓库：

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}
```

所有已声明的能力必须使用同一个平台版本。Factory 的完整基线会在根工程声明四个插件；
Consumer 不需要某项能力时，可以省略对应插件：

```kotlin
// 根目录 build.gradle.kts
plugins {
    id("io.github.magic-xu.magic-android-application") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-compose") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-pulse") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-quality") version "1.0.0" apply false
}
```

```kotlin
// App module build.gradle.kts
plugins {
    id("io.github.magic-xu.magic-android-application")
    id("io.github.magic-xu.magic-android-compose")
    id("io.github.magic-xu.magic-android-pulse")
    id("io.github.magic-xu.magic-android-quality")
}

android {
    namespace = "com.example.app"
    defaultConfig {
        applicationId = "com.example.app"
        versionCode = 1
        versionName = "1.0.0"
    }
}
```

Application 插件提供共享的 SDK、Java、release、packaging 和基础依赖默认值；Compose 与
Pulse 插件分别添加对应依赖。Quality 规则是一个不可拆分的质量标准，固定检查 package
路径、依赖方向、feature UI 平台能力边界、feature MVI 骨架、多语言 key 一致性和生产
Kotlin 文件 400 行上限。Consumer 不能关闭或放宽单项规则；接入失败时必须修复 App 架构。

## 验证平台源码

环境要求：

- JDK 21
- Android SDK 36

运行插件测试并编译隔离的 Consumer：

```bash
./gradlew releaseCheck
./gradlew -p samples/smoke-app check assembleDebug
./gradlew -p samples/smoke-app \
  -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
  clean check assembleDebug
```

Maven 模式的 Smoke App 从隔离 Maven 仓库消费生成的 plugin marker POM 和实现 JAR，不能
通过 composite build 解析平台源码。

## 用本地 Consumer 开发平台改动

Composite build 只用于让真实 Consumer 验证尚未发布的平台改动。在 Consumer 的
`settings.gradle.kts` 中加入本地平台；不得把机器专属路径写入 Factory 产物或正式 Consumer
分支：

```kotlin
pluginManagement {
    includeBuild("../magic-android-platform")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Consumer 随后可以只应用正在开发的能力，不声明已发布版本：

```kotlin
plugins {
    id("io.github.magic-xu.magic-android-application")
    id("io.github.magic-xu.magic-android-compose")
    id("io.github.magic-xu.magic-android-pulse")
    id("io.github.magic-xu.magic-android-quality")
}

android {
    namespace = "com.example.app"
    defaultConfig {
        applicationId = "com.example.app"
        versionCode = 1
        versionName = "1.0.0"
    }
}
```

Google Services、Firebase Crashlytics 等 App 专属构建插件，即使后续按条件启用，也应在同一
module 的 `plugins` 块中声明。不要只在根工程通过 `apply false` 声明：父插件作用域中的旧版
传递构建依赖可能遮蔽平台使用的 AGP 和 bundletool 依赖。

平台职责和依赖边界见 [architecture.md](docs/engineering/architecture.md)，受保护的发布流程见
[publishing.md](docs/engineering/publishing.md)。
