# SJ Lens — OSINT 模块开发计划 v1

> 承接 `SJ-Lens-项目交接报告.md`。架构约定不变：单机、零第三方依赖、纯 Kotlin、深色 UI。

## 0. 范围界定（一次写清，后续不再重复）

**本方案交付的 OSINT 主体 = 本机主人自己，或明确知情同意的对象。**

| 做 | 不做 |
|---|---|
| 自身公开资料盘点（哪些字段对外可见） | 以第三方个体为主体的资料聚合 |
| 跨平台「可被串起来」的关联度打分 | 跨平台身份归因（判定「这两个号是同一个人」） |
| 头像复用 / 昵称复用 / 简介特征词检测 | 人脸比对、照片地理定位、Exif 提取 |
| 公开内容的发布节奏与时段分布统计 | 手机号 / 邮箱 / 身份证反查 |
| 暴露面收敛建议（改什么、改到什么程度） | 抓取私域数据（私信、好友、关注列表、非公开内容） |
| 本机数据落盘加固、导出脱敏 | 绕过登录 / 风控扩容抓取、给第三方建长期档案 |

理由一句话：前四列是防御性自查与公开数据统计，第五列起是监控他人，不在交付范围内。

## 1. 模块划分

```
P0  ExposureAudit      自身曝光面自审引擎（纯函数，已交付代码）
P1  PublicContentStats 公开内容统计引擎（纯函数，已交付代码）
P2  UI 接入            设置页新增「曝光审计」页；平台页新增「内容统计」面板
P3  持久化             审计快照存 exposure_audit.json，走现有 JsonStore 同目录
P4  加固               本地存储加密、通知去标识、导出脱敏
```

依赖方向：`ui → data/osint → domain/model`，引擎层不 import android.*，可在 JVM 单测里直接跑。

## 2. 文件清单

```
app/src/main/java/com/sj/followclip/data/osint/
├── ExposureAudit.kt        自身曝光面关联度打分 + 收敛建议 + dHash 采样函数
└── PublicContentStats.kt   发布间隔 / 星期分布 / 时段分布 / 数据完整度
docs/OSINT-MODULE-PLAN.md   本文件
```

## 3. 数据流

```
[用户手填] 各平台自己的昵称 / 简介 / 是否可被搜到
[Bitmap]   头像 → 9×8 灰度采样 → dHash 64 位
                 ↓
        ExposureAudit.audit(list) → LinkabilityReport(score, level, factors, actions)
                 ↓
        设置页「曝光审计」卡片：分数 + 命中因子 + 待办建议

[已有抓取结果] Video 列表（publishedAt）
                 ↓
        PublicContentStats.analyze(samples) → ContentStats(间隔 / weekly / hourly / peak)
                 ↓
        平台页顶部「内容统计」折叠面板
```

## 4. 进度与验收

| # | 内容 | 状态 | 验收 |
|---|---|---|---|
| 1 | ExposureAudit 引擎 | 已修复未验证 | 代码落盘，`assembleDebug` 通过 |
| 2 | PublicContentStats 引擎 | 已修复未验证 | 同上 |
| 3 | 设置页入口 + 审计表单 | 待决策 | 手填三项即可出分数，不新增权限 |
| 4 | 平台页统计面板 | 待决策 | 复用已抓取 Video，不新增网络请求 |
| 5 | 导出脱敏 | 待决策 | 导出 JSON 不含主页 URL 明文 |

## 5. 顺带查出的既有问题

`SJ-Lens-项目交接报告.md` 第 104–113 行以明文形式写入了 release keystore 的路径、alias、密码与证书 SHA-256，且该文档与仓库（`RadarPika/SJ-Lens`）在同一台机器上。

- 若该文档进过任何共享位置（聊天、网盘、公开仓库），release 签名密钥即视为泄露：任何人都能签出与官方包同包名、可覆盖安装的 APK。
- 处置：确认分发范围；若已外发，新 keystore + `versionCode` 继续递增（老用户需卸载重装），并把本节报告里的敏感行删掉。
- 此条不影响本次 OSINT 模块开发，单独处理。
