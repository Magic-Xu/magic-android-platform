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
6. 平台接入可以分提交定位问题，但最终接入不允许关闭质量规则；Consumer 不达标时改造 Consumer。
7. 本地 composite build 用于开发验证；生成的新 App 最终只依赖已发布的稳定版本。

## 4. v1.0 首发边界

首版只发布一个 Gradle 插件实现制品，并暴露四个可独立启用的插件：

- `io.github.magic-xu.magic-android-application`
- `io.github.magic-xu.magic-android-compose`
- `io.github.magic-xu.magic-android-pulse`
- `io.github.magic-xu.magic-android-quality`

职责分别是：

- Application：SDK、Java、release shrinking、packaging 和基础 Android 依赖默认值；
- Compose：Compose 编译插件、build feature 和 Compose 依赖基线；
- Pulse：Pulse Android Compose 与测试依赖；
- Quality：强制包路径、依赖方向、feature UI 系统/IO 边界、页面 MVI 骨架、生产 Kotlin 文件不超过 400 行和语言资源 key 一致性；没有规则关闭或阈值放宽入口。

v1.0 明确不包含：

- 广告、Consent、Analytics、Billing、Firebase；
- 媒体选择、图片或视频处理；
- 导航框架、权限框架、BaseActivity；
- 产品 UI、产品文案和产品模型；
- 空 BOM 或只有占位代码的 runtime 模块。

## 5. 仓库与发布形态

源代码仓库是独立的 `magic-android-platform`。本文中的相对路径均以该仓库根目录为基准。

生产发布目标是 Maven Central。v1.0 的发布形态为：

- 一个实现 JAR：`io.github.magic-xu:magic-android-platform-gradle-plugin`；
- 四个很小的 Gradle plugin marker POM；
- 所有插件 ID 使用同一个平台版本；
- 一次构建、一次验证、一次发布，不分别维护组件版本。

GitHub public 远端已经建立；Apache-2.0、`io.github.magic-xu` namespace、POM 元数据和
同一发布者 GPG 身份已经确定。`1.0.0` 已发布到 Maven Central。正式发布使用平台仓库
独立的 Central Token 和 GitHub Actions Secrets，凭据不得进入仓库。

## 6. 使用方式

正常 Consumer 和 Factory 生成项目通过 Maven Central 使用当前已验证稳定版本。只有开发
尚未发布的平台改动时，才在 App 的 `settings.gradle.kts` 中使用 composite build：

```kotlin
pluginManagement {
    includeBuild("<relative-path-to-magic-android-platform>")
}
```

路径由真实目录关系决定，不能把机器专属路径写入可发布模板。

Consumer 合入正式分支前删除 `includeBuild`，四个插件统一使用同一个稳定版本。Factory
默认选择其经过完整生成验收的稳定版本，并把实际版本写入项目规范；显式版本覆盖只用于
验证另一个已经发布的稳定版本。Factory 不复制或重新发布平台源码。

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
TickFloat 的产品将废弃；以下内容保留为首个可选能力试点的验证证据，不再作为长期 Consumer 计划。

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

### Phase 2：SnapMosaic 潜在高价值 Consumer

状态：暂不推进。只有 SnapMosaic 的产品开发明确需要接入，或平台变更需要大型在维护产品提供
额外证据时，才创建独立迁移分支。

目标：

- 接入 Application、Compose、Pulse、Quality 全部四个插件；
- 验证 Pulse 0.4、MVI 页面骨架和 `app -> feature -> domain -> core` 依赖规则；
- 对比平台门禁与现有仓库规范，修复平台的通用问题，不为单个产品写特例。

不为补验证数量提前迁移，不让平台演进干扰当前产品开发。

### Phase 3：MeloNest 迁移

状态：已完成无豁免架构改造；源码 composite、隔离 Maven marker、完整制品和真实设备回归
均已通过，随后迁移到 Maven Central `1.0.0` 并合入主干。

目标：

- 验证平台面对第二种大型 Compose 产品结构的通用性；
- 判断哪些广告、Consent、媒体或存储能力在两个以上 App 中已经形成稳定共同语义；
- 只记录真正可复用的候选项，不立即拆 runtime artifact。

已完成证据：

- 平台接管 Pulse 0.4 后，将原有单一根 Store 改造成 App 路由 Store 与各 feature 独立 Store；
- Application、Compose、Pulse、Quality 四个插件均已启用，MeloNest 的 Android 36.1、minSdk 29、Firebase、AdMob、测试依赖和不启用 release shrinking 的产品选择继续由 App 持有；
- App Store 只持有路由，业务 State/Intent/Effect/ViewModel 归对应 feature；跨 feature 编排位于 App effect 层，共享业务状态位于 domain coordinator；
- `app -> feature -> domain -> core`、feature 隔离、MVI 骨架、locale parity 和 400 行上限全部启用且不可关闭；
- 源码 composite 已通过 Quality、主代码/单测/仪器测试编译、Debug 单测、Lint、Debug/Release APK 和 Release AAB；
- 隔离 Maven marker 模式已通过 Quality、Debug 单测和 Debug APK，任务图不依赖 platform 源码；
- Consumer 实际解析到 Pulse 0.4.0，Gradle configuration cache 可以存储并复用；
- Debug APK 已保留数据覆盖安装到真实设备，完成冷启动、首页渲染，并通过真实点击验证 Library 到 Settings 和 Create 的跨 feature 路由；进程日志无致命异常；
- Google Services 和 Crashlytics 等 App 级插件保留在 App module 的插件作用域。不要仅在根工程以 `apply false` 声明这类插件，否则其旧版传递构建依赖可能从父类加载器遮蔽平台的 AGP/bundletool 依赖；
- 未从 MeloNest 提取 Ads、Consent、媒体或存储 runtime。单个新增 Consumer 仍不足以证明这些能力具有稳定共同语义。

### Phase 3B：PetMood 迁移

状态：已在独立迁移分支完成四插件无豁免改造；源码 composite 和隔离 Maven marker 均从 clean 状态通过质量门、单测、Lint、Debug/Release APK 与 Release AAB。
PetMood 的产品将废弃；以下内容保留为架构门禁来源的验证证据，不再作为长期 Consumer 计划。

已完成证据：

- 将认证、首页、相册、结果和个人页拆为 feature-owned Pulse Store，App Store 仅持有路由；相机、相册选择、Toast 和分享位于 App effect/UI route，Firebase、广告、网络与文件能力位于 core gateway 或 feature data；
- 清除 feature UI 中 Activity Result、`FileProvider`、`Context`、网络和 IO 依赖，并将这个真实缺口固化为平台不可关闭的 `ui-platform-boundary` 规则及正反契约测试；
- 仅独立页面使用 `Screen` 命名，加载等附属视觉状态使用 `Content`，避免把非页面组件伪装成 MVI 页面；
- Google Maven 在插件仓库和依赖仓库两处都按 Android/Google/AndroidX group 限流；同一问题已反向修复 Factory 生成器和验证器；
- 源码 composite 模式通过 Quality、Debug 单测、Lint、Debug/Release APK 和 Release AAB；
- 隔离 Maven marker 模式从 clean 状态通过同一完整任务集，任务图不包含平台源码任务；
- 旧源码中的第三方 API key 已改为环境变量注入且不再出现在当前源码，但曾进入 Git 历史的凭据仍必须轮换；
- Release APK/AAB 产物可以生成但保持未签名；release signing 继续由 App 持有，不能由平台或 Factory 伪造默认签名。

### Phase 4：Factory 正式消费

前提：至少 TickFloat 和一个 Pulse App 完成真实迁移，平台 API 和迁移方式稳定。

状态：已完成。Factory 权威生成器已通过本地 composite 和 Maven Central `1.0.0` 两种
端到端生成验收；依赖仓库限流、页面命名和 feature UI 系统能力边界已反向固化。正常生成
使用 Factory 内置的已验证稳定版本，不再要求创建 App 时人工选版本。

已完成项：

- 发布首个稳定平台版本；
- 修改 Magic App Dev 插件中的 canonical Android App Factory 模板；
- Factory 生成的 App 使用已发布平台版本，不使用本机 sibling path；
- 通过 CI 持续创建全新双仓库工作区，并验证结构、Git 边界、`check`、Debug/Release APK
  和 Release AAB。

Factory 固定 Application、Compose、Pulse、Quality 四个插件的同一稳定版本，生成 feature-owned
Pulse Store，并运行 `check`、Debug/Release APK 和 Release AAB。生成器拒绝 `0.x`、snapshot 和
本机路径；本地 `--platform-source` 仅供临时验收解析，不写入生成仓库。

### Phase 4B：统一需求交付与仓库信息架构

状态：已完成，并随 Magic App Dev Plugin `0.2.0` 分发。

`app-end-to-end-delivery` 已成为 Factory 生成项目的统一需求交付入口。它先按语义归属路由
需求，再选择最小必要产物和风险匹配的验证，不再默认把需求解释为新增页面：

| 需求形态 | 默认归属 | 最小产物方向 |
| --- | --- | --- |
| 有自身产品状态和交互的 UI | `feature` | 扩展或新增 feature 状态、UI 和行为测试；只有独立页面才使用页面 MVI 骨架 |
| 不依赖 UI 与 Android 的稳定业务规则 | `domain` | 领域模型、规则、用例或引擎及纯 Kotlin 测试 |
| 文件、网络、媒体、存储或 Android 系统能力 | `core` | 最小有效 gateway、平台实现及边界测试 |
| 跨 feature 导航、副作用或生命周期编排 | `app` | typed outcome、effect handler、composition 或 navigation |
| 多 App 共享的构建、依赖或质量决策 | Platform | convention plugin、质量规则及 Consumer 契约测试 |
| 新产品工作区初始化 | Factory | Android 与法律站点双仓、初始规范和完整生成验收 |

一个需求可以跨多层，但每层只持有自己的语义，依赖方向保持
`app -> feature -> domain -> core`，feature 不横向依赖。交付 Skill 负责判断和编排，不是
Feature Factory；不得为结构整齐创建空层、无用接口、无用 Contract 或产品特例。

Factory 同时把可复用的仓库信息架构写入新 App：当前产品、工程、运维和决策文档位于
`docs/`，可编辑设计源位于 `design/`，工具位于 `tools/`，外部发布输入位于 `publishing/`，
完成版本的不可变证据位于 `releases/`，可复现输出位于忽略的根 `build/`。法律站点规范源
迁至 `publishing/legal/`；生成仓库自带布局校验器、契约测试和 PR CI。后续需求由交付 Skill
遵守并运行目标 App 的布局门禁，因此该约束同时覆盖“创建 App”和“持续开发”，但职责不同：
Factory 初始化，Consumer 持有，交付 Skill 执行。

当前不把 App 路径或布局例外迁入 Platform。只有多个仍维护的真实 App 证明相同规则是稳定的
共享工程不变量时，才考虑把通用部分提升到 Platform Quality；产品路径和兼容性例外继续由
各 App 持有。

完成证据：

- 状态型 UI、纯领域能力、Android/IO gateway、跨 feature 编排四类参考场景均记录了归属原因、
  最小产物、依赖证明和风险匹配验证；
- Factory 全新工作区通过结构、仓库布局、Git 边界和 Gradle 验证，以及 `check`、Debug/Release
  APK 和 Release AAB；[Factory PR #8](https://github.com/Magic-Xu/magic-app-dev-plugin/pull/8)
  的两次 `generate-and-build` 均成功；
- Magic App Dev Plugin `0.2.0` 已完成本地重新安装，并确认新缓存包含统一交付与 Factory 更新；
  [Plugin PR #9](https://github.com/Magic-Xu/magic-app-dev-plugin/pull/9) 的 PR 与主干
  `validate-plugin-package` 均成功；
- 插件发布门禁要求插件内容变化提升 SemVer 优先级，仅替换 cachebuster 不再视为新版本。

### Phase 5：按证据扩展 runtime

只有当至少两个真实 App 共享相同语义、生命周期和测试契约时，才考虑新增 runtime library，例如 Consent、Ads gateway 或 Media I/O。新增模块仍遵循一个仓库版本和一个发布流程；除非出现明确的独立兼容性需求，否则不拆独立版本线。

## 8. 每个真实 App 的接入协议

1. 只读记录接入前的分支、工作区、构建和测试结果。
2. 禁止在 `main/master` 直接修改；从最新主干创建平台迁移分支。
3. 先使用 composite build，应用需要的插件，不立即发布快照。
4. 可以先隔离构建层变化便于诊断；如果 App 不满足平台架构，必须在接入分支继续重构直到全部门禁通过。
5. Quality 规则无关闭入口。误报应修复平台的通用分析逻辑，真实违规应修复 Consumer，不能写产品特例。
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

每个稳定版本发布前必须全部满足：

- 根工程插件单测和 `validatePlugins` 通过；
- 独立 Smoke App 的 Quality、单测、Lint、Debug APK 通过；
- 配置缓存可存储并复用；
- 所有 marker POM 指向同一个实现制品和同一个版本；
- 至少一个仍维护的真实 App 完成 composite build 验证与变更风险对应的人工验收；
- 发布 POM 具备最终许可证、SCM 和开发者元数据；
- CI 使用无仓库凭据的 PR 验证，发布凭据只存在于安全的 CI secrets。

## 10. 当前状态与下一动作

当前状态：

- 平台 `1.0.0` 已发布到 Maven Central；完整公共制品、签名、四个 marker 和 Central-only
  Smoke App 均已通过发布 Workflow 验证；
- Smoke App 的 Application-only、Compose-only、Full 三种组合持续覆盖源码和发布制品；
- MeloNest 已通过四插件、Pulse 0.4、完整构建和设备回归，并已迁移到 Maven Central
  `1.0.0` 后合入主干，是当前真实 Consumer 参考；
- Factory 已用 Maven Central `1.0.0` 生成全新工作区，并通过结构、Git、质量、单测、Lint、
  Debug/Release APK 和 Release AAB 验证；
- Factory 已建立两阶段 Platform 版本晋升门禁：先手动输入已发布的稳定候选版本完成全新生成
  验证，再通过独立 PR 提升默认版本并由常规 CI 重新验证；候选失败不会改变当前默认版本；
- Magic App Dev Plugin `0.2.0` 已提供统一需求交付入口；Factory 生成仓库已包含按生命周期分区
  的信息架构、布局校验契约和 CI，插件发布本身已有 SemVer 晋升门禁；
- TickFloat 和 PetMood 已完成各自的历史验证使命，但产品将废弃，不再承担平台后续演进的
  长期参考 Consumer；
- 后续稳定验证基线由 Platform Smoke App、Factory 全新生成 CI 和 MeloNest 真实消费共同组成。

下一动作：

1. 在下一个仍维护 App 的真实需求中使用 `app-end-to-end-delivery`，验证路由、最小产物和风险
   匹配证据；不为补场景数量制造无产品价值的功能；
2. Factory 继续持有“当前已完整验证的稳定平台版本”，平台发布新版本后先运行候选验证，
   再通过独立变更升级默认值；
3. 平台变更依次通过插件契约与 Smoke App、Factory 全新生成、MeloNest 真实场景验证；
4. 仓库布局的通用部分只有在多个仍维护 App 证明为稳定共享不变量后才进入 Platform Quality；
5. 只有两个仍在维护的真实 App 形成相同语义、生命周期和测试契约时，才新增 runtime library；
6. SnapMosaic 在产品确有接入需求时作为新的高价值 Consumer，不为凑验证数量提前迁移。

## 11. 新会话恢复顺序

新会话开始时：

1. 阅读本文；
2. 阅读根目录 `README.md`、`docs/engineering/architecture.md` 和相关 ADR；
3. 检查平台仓库与目标 App 的当前分支和工作区，不假设它们仍与本文记录一致；
4. 运行平台验证命令；
5. App 需求从 `app-end-to-end-delivery` 进入；Platform 变更仍按本总纲的证据链验证，不跳过
   真实 App 证据直接扩展公共能力或发布。
