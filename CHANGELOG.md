# CHANGELOG

本文件按 Phase 记录实际改动。约定：

- 每段包含「已完成 / 验证方式 / 已知问题 / 下一步」四节。
- 按规格第二十章，`git commit` 与打 tag 一律由开发者本人执行，AI 不代替提交。
- 文档与代码不一致时，先改规格文档并升版本，再改代码。

---

## Phase 1 — 数据层与界面骨架（进行中）

**规格依据**：V2.0 第十九章 Phase 1。
**验收物**：真机截图四张（卡片网格、详情页、待确认页、设置页）。
**当前状态**：界面骨架完成并可构建；数据层（Room、DAO、DataStore）尚未开始。

### 一、已完成：界面架构

新增 14 个 Kotlin 文件，共约 1960 行（含注释与空行）。

| 文件 | 行数 | 职责 |
|---|---|---|
| `domain/model/Models.kt` | 68 | `Platform` 枚举、`CheckMode`、`Creator`、`Video`、`PendingGroup` |
| `ui/theme/Tokens.kt` | 80 | 设计令牌：颜色令牌（派生自 ColorScheme）+ 间距令牌（4 的倍数） |
| `ui/theme/Theme.kt` | 44 | Material You 动态取色，低版本回退 M3 基线色板 |
| `ui/theme/Type.kt` | 8 | 只用 Material 3 默认字阶 |
| `ui/AppNavigation.kt` | 145 | 路由表、NavHost、底部三项导航、首页专属 FAB |
| `ui/home/HomeScreen.kt` | 157 | 分组筛选 Chips、两列卡片网格、空态、排序 |
| `ui/home/CreatorCard.kt` | 197 | 卡片四元素、红色实心角标、橙色空心圈 |
| `ui/detail/DetailScreen.kt` | 272 | 主页卡片、立即刷新、作品列表、未读圆点 |
| `ui/unconfirmed/UnconfirmedScreen.kt` | 247 | 按博主折叠分组、三列封面网格、逐条勾选、本组全部确认、只看未确认开关、过期分组灰态 |
| `ui/settings/SettingsScreen.kt` | 196 | 七个区块，严格按规格第十四章顺序 |
| `ui/add/AddScreen.kt` | 299 | 链接输入、预览确认卡、备注名、分组、模式、间隔 |
| `ui/preview/FakeData.kt` | 170 | 假数据，时间实时算，保证截图文案永远新鲜 |
| `util/TimeFormat.kt` | 45 | 相对时间、时长、数字缩写 |
| `MainActivity.kt` | 33 | 单 Activity，铺主题后交给 AppNavigation |

界面实现严格对齐了规格里可核对的条款：底部导航三项、全局 FAB 仅首页、无硬编码色值、间距只用 4 的倍数、卡片固定四元素、抖音与快手标注「实验性」、待确认页三列封面且无数字空心圈、设置页区块顺序、失败与空态文案都给「发生了什么 + 下一步做什么」。

### 二、依赖变更（需开发者知悉）

新增 `androidx.compose.material:material-icons-core`。

- **原因**：Material3 1.4.0 起不再传递依赖图标包，底部导航需要图标，不引入就编译不过。
- **体积**：由 Compose BOM 固定为 1.7.8（该库已冻结），体量很小。
- **替代方案**：手写 ImageVector 路径数据，或底部导航放弃图标——两者都比这一行依赖更贵。

### 三、已知问题

1. **`@Preview` 暂时看不到**。VS Code 没有 Compose 实时预览（Android Studio 独有），本机也没装 Android Studio。预览构造仍然按要求写好并保留，真实反馈走真机截图闭环。
2. **头像与封面是占位**。v1.0 锁定的技术栈没有图片加载库，加载网络头像需要新增依赖并先经确认，所以 Phase 1 用「备注名首字 + 主题色底」和「色块 + 播放图标」占位。**这是规格的一处空白，需要补一条图片加载方案**。
3. **平台色点用主题色位而非品牌色**。规格禁止硬编码色值，故用 primary / secondary / tertiary 区分三个平台，颜色不如品牌色直观。若要品牌色，需先在规格里补平台色令牌。
4. **添加页的解析与保存是占位**，真实逻辑等 Phase 2 接入 Source 接口。
5. **首页与待确认页的排序语义**已按规格原文实现（同级内按最近抓取时间升序），如与预期不符需回改规格。

### 四、下一步

- [ ] Room：`creator`、`video`、`sourceState` 三张表 + DAO 方法清单（规格第五章）
- [ ] DataStore：抓取设置与通知开关
- [ ] 把界面从 `FakeData` 切到真实数据源，引入 ViewModel 与 AppContainer
- [ ] 真机安装并截图四张，作为 Phase 1 验收物

---

## Phase 0 — 需求冻结、建仓库、工程骨架（进行中）

**规格依据**：V2.0 第十九章。
**验收物**：云端产出可安装的签名空壳 APK。
**门禁**：① assembleRelease 云端通过 ② keystore Secrets 配置完成 ③ tag 与版本校验生效。
**当前状态**：工程骨架已在本地构建通过；门禁 ①②③ 均依赖云端，按开发者要求推后。

### 一、已完成

**本地开发环境（全部装在 D 盘，独立目录）**

| 工具 | 版本 | 位置 | 环境变量 |
|---|---|---|---|
| Temurin JDK | 17.0.20.1 | `D:\DevEnv\jdk-17` | `JAVA_HOME` |
| Gradle | 8.14.3 | `D:\DevEnv\gradle-8.14.3` | `GRADLE_HOME` |
| Gradle 缓存 | — | `D:\DevEnv\gradle-home` | `GRADLE_USER_HOME` |
| Android SDK | platform-tools 37.0.1 | `D:\DevEnv\android-sdk` | `ANDROID_HOME` / `ANDROID_SDK_ROOT` |
| VS Code | 1.141.2 | 用户目录 | 已在 PATH |
| Git | 2.56.0.2 | `C:\Program Files\Git` | 已在系统 PATH |

Android SDK 已装组件：`cmdline-tools;latest`、`platform-tools`、`platforms;android-34`、`platforms;android-36`、`build-tools;34.0.0`、`build-tools;36.0.0`。

VS Code 插件：`fwcd.kotlin`、`redhat.vscode-xml`、`redhat.java`、`vscjava.vscode-gradle`、`EditorConfig.EditorConfig`、`yzhang.markdown-all-in-one`。

国内网络下 winget 直连 GitHub 下载停滞，Git 与 Gradle 改走 npmmirror / 腾讯云镜像，JDK 走清华 TUNA Adoptium 镜像。

**工程骨架**

- `settings.gradle.kts`：阿里云 Maven 镜像优先、官方源兜底；`rootProject.name = "SJFollowClip"`
- `gradle/libs.versions.toml`：版本目录，所有依赖版本集中管理，模块脚本不写死版本
- `gradle.properties`：`VERSION_NAME=1.0.0`、`VERSION_CODE=26101601`（YYMMDDnn）
- `gradle/wrapper`：Gradle 8.14.3，`distributionUrl` 指向腾讯云镜像
- `app/build.gradle.kts`：`namespace`/`applicationId` = `com.sj.followclip`；minSdk 26、targetSdk 34、compileSdk 36
- `MainActivity.kt`：单 Activity 空壳，Material3 `Scaffold`，带 `@Preview`
- `ui/theme/Theme.kt`：Material You 动态取色（API 31+），低版本回退 Material 3 基线色板，无硬编码色值
- `ui/theme/Type.kt`：只使用 Material 3 默认字阶
- 自适应启动图标（`mipmap-anydpi-v26`，书签造型，minSdk 26 故无需各密度位图）
- `AndroidManifest.xml`：仅申请 `INTERNET`，`allowBackup=false`
- `.vscode/tasks.json`：gradle 编译、adb 安装/截图/logcat、git 状态与最近提交
- `.editorconfig` / `.gitattributes` / `.gitignore`：统一 LF、`gradlew` 强制 LF、`*.bat` 强制 CRLF、keystore 与 `local.properties` 不入库

### 二、验证方式与实际结果

| 命令 | 结果 |
|---|---|
| `gradlew assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（11.3 MB） |
| `gradlew assembleRelease` | BUILD SUCCESSFUL，产物 `app-release-unsigned.apk`（8.2 MB，未签名） |
| `aapt2 dump badging` | package `com.sj.followclip`、versionCode `26101601`、versionName `1.0.0`、targetSdk `34`、label `SJ关注夹`、权限仅 `INTERNET` |

尚未做真机安装与截图：当前没有连接任何设备。

### 三、已知问题与取舍

1. **工程路径含中文**（`D:\桌面\SJ Lens`）。AGP 默认直接拒绝构建，已在 `gradle.properties` 设 `android.overridePathCheck=true` 绕过。若后续出现难以定位的构建异常，第一优先排查项是把工程整体迁到纯英文路径。
2. **compileSdk 取 36 而非 34**。规格只钉住 `targetSdk = 34`（并允许当前稳定版本），未规定 compileSdk；当前 AndroidX 依赖要求至少 35 至 36。
3. **依赖版本被 AGP 反向卡住**。`compose-bom 2026.09.00`（内含 compose-ui 1.12.1）与 `lifecycle 2.11.0` 的 AAR 元数据要求 `minAgpVersion=9.1.0`、`minCompileSdk=37`，而本机 SDK 仓库最高只提供 `android-36`，AGP 8.13.2 最高推荐 compileSdk 也是 36。因此锁定在 `compose-bom 2026.06.01`（compose-ui 1.11.4）与 `lifecycle 2.10.0`，两者 `minAgpVersion` 均为 8.6.0。升级路径是 AGP 9.x + compileSdk 37，待 `android-37` 可用后再评估。
4. **APK 改名未在 Gradle 内实现**。AGP 8.x 的 `VariantOutput` 公共 API 不暴露 `outputFileName`，产物名规范（`SJFollowClip-v版本号-release.apk`）留到发布工作流的打包步骤执行。
5. Gradle 8.14.3 对 Kotlin Gradle Plugin 2.5.0 起属已弃用版本（当前为警告，非错误）。
6. debug 包由 AGP 自动注入 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，属 AndroidX 自用的 signature 级权限，非高危权限。

### 四、下一步

- [ ] P0-2 用 `keytool` 生成 release keystore，本地离线备份并转 base64（JDK 已就位）
- [ ] P0-3 `.github/workflows/android.yml`：setup-java 17、Gradle 缓存、解码 keystore、assembleDebug + assembleRelease、上传产物、附到 Release、校验 tag 与 versionName 一致
- [ ] P0-4 `README.md`（安装步骤、已知限制、免责声明）、`LICENSE`、`docs/` 纳入两份规格文档
- [ ] P0-5 真机安装 + 截图存 `screenshots/`，打首个 tag
- [ ] 待定：仓库名与本地目录命名统一（目录 `SJ Lens` 与应用名 `SJ关注夹`、包名 `com.sj.followclip` 目前不一致）
- [ ] 待定：开源许可证选择（规格未指定）
- [ ] Phase 1：Room + DAO 三张表、DataStore、Navigation、动态取色、卡片网格假数据
