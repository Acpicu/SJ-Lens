# SJ Lens

安卓端红队情报工具：围绕目标收集公开线索、登记成结构化数据本、按证据强度交档。
单机运行，数据只存本机，零第三方 SDK、零埋点。

**当前版本：3.0.2**（versionCode 26101919）· minSdk 24 / Android 7.0+ · APK 约 1.7 MB

---

## 能做什么

| 模块 | 说明 |
|---|---|
| **三平台更新检测** | B站 / 抖音 / 快手，**前台后台都能查** |
| **数据本** | 一条记录 = 一条可陈述事实，强制带出处、评级、置信度、状态 |
| **冲突与缺口** | 同字段两个值点名冲突；六类关键事实缺失自动列出 |
| **交档导出** | Markdown 与单页 HTML（深色主题，含时间线、冲突、缺口） |
| **关联度评估** | 9 个平台，同平台支持多号（微信①/微信②）互相比对 |
| **检查通知** | 检查中常驻进度横幅 + 完成横幅，可取消 |

### 各平台怎么查

| 平台 | 前台 | 后台 | 拿到什么 |
|---|---|---|---|
| B站 | 公开接口 | ✅ | 作品与动态逐条，可点开 App |
| 抖音 | 接口计数 + 渲染补条目 | ✅（计数） | 作品数 / 改名 / 改简介；前台额外给具体作品（含图文） |
| 快手 | 真实渲染 | ✅（HTTP 直取分享页计数） | 作品数 / 昵称 / 简介 |

## 更新判定靠基线

**第一次检查只记下当前状态，这一轮不会报更新；从第二轮起跟基线比对，变了才报。**
所以首轮看到「都是见过的」是正常的。首次启动的引导页会把这条讲清楚。

- 抖音接口 `aweme_count` 不含图文笔记，翻图文的检测走渲染路径（需前台）
- 快手后台依赖分享落地页 HTML 结构，改版后需要更新正则

## 隐私与安全

- 数据只存应用私有目录，`allowBackup=false`，adb backup 取不到
- 仅 `MainActivity` 对外导出；检查服务 `exported=false`
- 全部 HTTPS，无 `Log` / `println` 输出，WebView 无 JS 桥且拦截非 http(s) 跳转
- B站 登录 Cookie 经 Android Keystore AES-256-GCM 加密落盘（旧明文自动迁移删除）
- 导出备份前会提示「含全部目标资料，请勿外发」

## 已知限制

1. 首轮只建基线，不报更新
2. 抖音「具体哪几条」需应用在前台
3. 快手后台数字依赖分享页结构，改版需更新正则
4. 作品列表未分页：B站 取首页 30 条，抖音/快手 取首屏
5. 抖音 `aweme/post` 未逆向 `a_bogus`，条目由 DOM 渲染取得

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 正式包（需 keystore.properties，缺失则退回 debug 签名）
```

签名配置写在根目录 `keystore.properties`（**不入库**）：

```properties
storeFile=keystore/your-release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

Windows 下可直接双击 `一键打包到根目录.bat`：构建 release 并复制到工程根目录，文件名带版本号。

## 项目结构

```
app/src/main/java/com/sj/followclip/
├── data/
│   ├── check/      检查编排：CheckRunner（三平台）+ CheckController（应用级会话）+ CheckService（前台服务）
│   ├── local/      持久化：folders.json / 数据本 / 快照 / 待确认池
│   ├── osint/      关联度与内容统计引擎（纯 Kotlin，无 android 依赖）
│   ├── source/     B站公开接口 / 抖音与快手 HTTP 探测 / 渲染抽取
│   └── work/       定时任务
├── domain/model/   数据模型
├── ui/             界面：首页 / 目标 / 平台 / 数据本 / OSINT / 设置 / 引导
└── util/           通知、Cookie、头像、加密存储、缓存清理
```

技术栈：Kotlin + Jetpack Compose + Material 3，**零第三方网络 / 图片 / 数据库库**
（HTTP 用 `HttpURLConnection`，JSON 用 `org.json`，图片缓存自写 `LruCache`）。

## 免责

仅用于公开信息整理与授权的安全竞赛环境。抓取方式为读取公开页面/公开接口，
不破解、不绕过鉴权、不存储他人私密内容。使用风险由使用者自行承担。

## License

[MIT](LICENSE)