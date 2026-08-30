# Magic Android Platform 总纲

本文是 Magic Android App 工程体系的跨会话事实源。后续开始新会话、切换执行者或出现较长上下文时，先阅读本文，再查看具体实现和决策记录。

## 1. 最终目标

目标不是制造一个包办所有事情的“大框架”，而是把多个 Android App 中反复出现、且长期稳定的工程决策收敛成可升级的平台，从而：

- 更快创建新 App；
- 让已有 App 共享同一套构建和质量基线；
- 降低依赖升级、工程规范漂移和重复维护成本；
- 保持产品逻辑、发布身份和可选能力彼此隔离。

衡量成功的标准不是平台模块数量，而是新 App 需要重复决定和重复维护的事项持续减少，同时已有 App 不被迫采用无关能力。

## 2. 系统分工

整个体系分成四层，各自只解决一个问题：

| 层 | 职责 | 不负责 |
| --- | --- | --- |
| Android App Factory | 创建新的 Android 仓库和配套法律站点，生成可运行的初始结构 | 持续承载公共实现、替已有 App 升级 |
| Magic Android Platform | 持续提供构建约定、依赖基线和质量门禁 | 生成产品、包含产品逻辑 |
| Pulse | 提供 MVI 状态运行时和 Android/Compose 适配 | 构建配置、广告、媒体或产品流程 |
| Consumer App | 持有产品模型、UI、系统能力编排、签名和发布身份 | 复制维护公共工程基线 |

Factory 是入口，Platform 是持续演进的工程底座，Pulse 是状态运行时。三者不能合并成同一个仓库或发布单元。

## 3. 第一性原则

1. 只复用稳定决策，不复用“看起来相似”的产品行为。
2. 能力必须可选；不使用 Compose、Pulse 或质量门禁的模块不应被隐式绑定。
3. 逻辑组件不等于 Maven 组件。没有独立版本诉求，就不拆独立制品。
4. 平台提供默认值，App 继续使用标准 Android Gradle DSL 覆盖自身身份和特殊配置。
5. 先用真实 App 证明价值，再扩展平台；没有两个真实消费者的稳定共同语义，不新增 runtime library。
6. 平台迁移与产品重构分开进行，确保问题来源和回滚边界清晰。
7. 本地 composite build 用于开发验证；生成的新 App 最终只依赖已发布的稳定版本。

## 4. v0.1 边界

首版只发布一个 Gradle 插件实现制品，并暴露四个可独立启用的插件：

- `io.github.magic-xu.magic-android-application`
- `io.github.magic-xu.magic-android-compose`
- `io.github.magic-xu.magic-android-pulse`
- `io.github.magic-xu.magic-android-quality`

职责分别是：

- Application：SDK、Java、release shrinking、packaging 和基础 Android 依赖默认值；
- Compose：Compose 编译插件、build feature 和 Compose 依赖基线；
- Pulse：Pulse Android Compose 与测试依赖；
- Quality：包路径、依赖方向、页面 MVI 骨架、文件大小和语言资源 key 一致性。

v0.1 明确不包含：

- 广告、Consent、Analytics、Billing、Firebase；
- 媒体选择、图片或视频处理；
- 导航框架、权限框架、BaseActivity；
- 产品 UI、产品文案和产品模型；
- 空 BOM 或只有占位代码的 runtime 模块。

## 5. 仓库与发布形态

源代码仓库是独立的 `magic-android-platform`。本文中的相对路径均以该仓库根目录为基准。

生产发布目标是 Maven Central。v0.1 的发布形态为：

- 一个实现 JAR：`io.github.magic-xu:magic-android-platform-gradle-plugin`；
- 四个很小的 Gradle plugin marker POM；
- 所有插件 ID 使用同一个平台版本；
- 一次构建、一次验证、一次发布，不分别维护组件版本。

GitHub public 远端已经建立。首次正式发布前仍需确定开源许可证、Maven Central namespace、签名和发布凭据，并补齐最终 POM 元数据。凭据不得进入仓库。

## 6. 使用方式

平台开发和真实 App 试点阶段，在 App 的 `settings.gradle.kts` 中使用 composite build：

```kotlin
pluginManagement {
    includeBuild("<relative-path-to-magic-android-platform>")
}
```

路径由真实目录关系决定，不能把机器专属路径写入可发布模板。

正式发布后，Consumer App 删除 `includeBuild`，四个插件统一使用同一个稳定版本。Factory 只负责把这个稳定版本写入新项目模板，不复制或重新发布平台源码。

## 7. 推进路线

### Phase 0：平台可执行骨架

状态：已完成。

完成项：

- 独立 Git 仓库和 Gradle Wrapper；
- 四个 convention plugins；
- Quality 规则单测；
- 独立 Smoke App；
- CI；
- 单实现制品、四 marker 的发布元数据验证；
- 同一 Smoke 能力矩阵分别通过源码 composite 和本地 Maven marker 制品消费；
- Gradle configuration cache 验证。

验证命令：

```bash
./gradlew platformCheck publicationCheck
./gradlew -p samples/smoke-app check assembleDebug
./gradlew -p samples/smoke-app \
  -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
  clean check assembleDebug
```

### Phase 1：TickFloat 首个真实试点

状态：源码 composite 与本地 Maven marker 两种接入均已完成完整构建和设备冒烟，用户已确认接入成功。

选择 TickFloat 的原因：

- 工作区当前干净，可从最新 `main` 创建独立迁移分支；
- 约 36 个生产 Kotlin 文件，问题定位和回滚成本低；
- AGP 9.1.0、Kotlin 2.2.10、Compose BOM 已与平台主要版本对齐；
- 同时包含 Compose、release shrinking、签名、系统悬浮窗和多语言资源，足以验证真实 App，而不是玩具示例；
- 当前不使用 Pulse，恰好验证插件能力确实可选。

Phase 1 只接入：

- `magic-android-application`
- `magic-android-compose`
- `magic-android-quality`

不在同一任务中把 TickFloat 重构成 Pulse MVI，也不改变产品行为、UI 或发布签名。先保留 App 的特殊配置，只删除已被平台完整覆盖且验证等价的重复项。

重点验证：

- Java 11 默认值迁移到平台 Java 17 后是否存在兼容问题；
- App 的 Android 36.1 minor SDK 覆盖是否仍然有效；
- release signing、SplashScreen、Android/Compose 测试依赖能否继续由 App 自己持有；
- 多语言 key 规则能否覆盖现有 locale 目录且没有误报；
- 接入前后 Debug 构建、测试、Lint 和关键人工流程是否等价。

已完成证据：

- TickFloat 仅启用 Application、Compose、Quality，未被隐式绑定 Pulse；
- Android 36.1、App 身份、可选 release signing、SplashScreen 和测试依赖继续由 App 持有；
- 源码 composite 与本地 Maven marker 两种模式均通过依赖解析、Quality、Lint、单测、Debug APK、
  Release/R8 APK 和 Release AAB；
- Debug APK 已安装到真实设备并完成冷启动、悬浮前台服务启动/停止与无崩溃冒烟；
- Maven 模式任务图不包含平台源码任务，并能存储和复用 Gradle configuration cache；
- `translatable="false"` 默认资源不再被错误要求出现在各语言目录，并有平台契约测试覆盖；
- 平台源码未引入 TickFloat 包名、权限、产品模型或其他 Consumer 特例。

### Phase 2：SnapMosaic 完整能力试点

前提：当前产品开发分支已完成或收口，工作区可安全创建平台迁移分支。

目标：

- 接入 Application、Compose、Pulse、Quality 全部四个插件；
- 验证 Pulse 0.4、MVI 页面骨架和 `app -> feature -> domain -> core` 依赖规则；
- 对比平台门禁与现有仓库规范，修复平台的通用问题，不为单个产品写特例。

SnapMosaic 不作为首个试点，因为它规模大且当前有活跃功能改动；平台问题和产品问题会互相干扰。

### Phase 3：MeloNest 迁移

状态：已在基于最新 `origin/main` 的独立迁移分支完成源码 composite、本地 Maven marker、完整构建和真实设备验证，待用户验收。

目标：

- 验证平台面对第二种大型 Compose 产品结构的通用性；
- 判断哪些广告、Consent、媒体或存储能力在两个以上 App 中已经形成稳定共同语义；
- 只记录真正可复用的候选项，不立即拆 runtime artifact。

已完成证据：

- 先独立验证原有源码和单测兼容 Pulse 0.4，再由平台接管 Pulse 版本与依赖，未修改产品 Kotlin 代码；
- Application、Compose、Pulse、Quality 四个插件均已启用，MeloNest 的 Android 36.1、minSdk 29、Firebase、AdMob、测试依赖和不启用 release shrinking 的产品选择继续由 App 持有；
- MeloNest 采用单一根 Pulse Store，而不是每个 feature 独立 Store；因此仅关闭不符合当前架构的 feature-to-app 依赖方向和每 feature MVI 骨架规则，包路径、文件大小和语言资源检查继续启用；
- 源码 composite 与本地 Maven marker 两种模式均通过 Quality、Lint、单测、Debug APK、Release APK 和 Release AAB；Maven 模式任务图不包含平台源码任务；
- Consumer 实际解析到 Pulse 0.4.0，Gradle configuration cache 可以存储并复用；
- Debug APK 已保留数据覆盖安装到真实设备，完成冷启动、首页渲染和通过真实点击进入设置页的状态切换；
- Google Services 和 Crashlytics 等 App 级插件保留在 App module 的插件作用域。不要仅在根工程以 `apply false` 声明这类插件，否则其旧版传递构建依赖可能从父类加载器遮蔽平台的 AGP/bundletool 依赖；
- 未从 MeloNest 提取 Ads、Consent、媒体或存储 runtime。单个新增 Consumer 仍不足以证明这些能力具有稳定共同语义。

### Phase 4：Factory 正式消费

前提：至少 TickFloat 和一个 Pulse App 完成真实迁移，平台 API 和迁移方式稳定。

动作：

- 发布首个稳定平台版本；
- 修改 Magic App Dev 插件中的 canonical Android App Factory 模板；
- Factory 生成的 App 使用已发布平台版本，不使用本机 sibling path；
- 更新 Factory acceptance sample 并执行端到端生成验证。

### Phase 5：按证据扩展 runtime

只有当至少两个真实 App 共享相同语义、生命周期和测试契约时，才考虑新增 runtime library，例如 Consent、Ads gateway 或 Media I/O。新增模块仍遵循一个仓库版本和一个发布流程；除非出现明确的独立兼容性需求，否则不拆独立版本线。

## 8. 每个真实 App 的接入协议

1. 只读记录接入前的分支、工作区、构建和测试结果。
2. 禁止在 `main/master` 直接修改；从最新主干创建平台迁移分支。
3. 先使用 composite build，应用需要的插件，不立即发布快照。
4. 第一次提交只做构建层接入和等价去重，不做产品重构。
5. 对 Quality 误报先判断规则是否通用；不能用产品特例掩盖平台缺陷。
6. 运行 App 原有测试、`check`、`assembleDebug` 和适用的关键 UI 流程。
7. 记录平台接管的配置、Consumer 保留的覆盖项、发现的缺口和回滚方式。
8. 用户验收后才能进入 PR、合并和下一 App。

每次迁移必须能回答：

- 删除了哪些重复维护点？
- App 仍保留哪些特殊配置，为什么？
- 平台是否引入行为变化或依赖升级？
- 发现的问题属于平台、App，还是迁移本身？
- 移除平台接入后能否恢复原状？

## 9. 发布门槛

首个稳定版本发布前必须全部满足：

- 根工程插件单测和 `validatePlugins` 通过；
- 独立 Smoke App 的 Quality、单测、Lint、Debug APK 通过；
- 配置缓存可存储并复用；
- 所有 marker POM 指向同一个实现制品和同一个版本；
- 至少一个真实 App 完成 composite build 迁移与人工验收；
- 发布 POM 具备最终许可证、SCM 和开发者元数据；
- CI 使用无仓库凭据的 PR 验证，发布凭据只存在于安全的 CI secrets。

## 10. 当前状态与下一动作

当前状态：

- 平台 v0.1 骨架已推送到 public GitHub 远端，托管 CI 已通过，但尚未正式发布到 Maven Central；
- Smoke App 的 Application-only、Compose-only、Full 三种组合均已通过 clean、check 和 Debug APK 构建；
- TickFloat 已完成 Application、Compose、Quality 的源码与 Maven 制品双模式迁移，并通过完整构建和设备冒烟；
- MeloNest 已完成四插件、Pulse 0.4 的源码与 Maven 制品双模式迁移，并通过完整构建和设备冒烟，待用户验收；
- 发布检查会自动断言一个实现 JAR、一个 sources JAR、四个 marker POM，且 marker 均指向同一实现版本；
- Factory 尚未修改；
- 平台尚无稳定发布版本；真实 App 的 Maven marker 验证仍使用隔离的本地发布仓库。

下一动作：

1. 用户验收 MeloNest 迁移，再决定是否推送、创建 PR 和合并；
2. 确定开源许可证、Maven Central namespace、签名、SCM 与开发者 POM 元数据，准备首个稳定版本；
3. TickFloat 与 MeloNest 分别提供非 Pulse 和 Pulse Consumer 证据后，稳定版本不再被 SnapMosaic 试点阻塞；SnapMosaic 等活跃产品改动收口后仍可继续 Phase 2，用于扩大多模块规则证据；
4. 首个稳定版本发布后再修改 Factory，并执行生成项目的端到端验收。

## 11. 新会话恢复顺序

新会话开始时：

1. 阅读本文；
2. 阅读根目录 `README.md`、`docs/engineering/architecture.md` 和相关 ADR；
3. 检查平台仓库与目标 App 的当前分支和工作区，不假设它们仍与本文记录一致；
4. 运行平台验证命令；
5. 根据“当前状态与下一动作”继续，不跳过真实 App 验证直接修改 Factory 或发布。
