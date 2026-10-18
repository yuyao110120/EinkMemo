# ElinkMemo

墨水屏极简便签 / 待办。目标设备：文石 Boox Leaf2（7 英寸 / 1680×1264 / 300ppi / Android 11）。

- 主页面：无标题栏，固定 10 条/页，右滑划掉、左滑或点击编辑、+ 连续新增，纯黑白零动画
- 已完成原地保留（删除线 + 灰），设置页手动「归档已完成」，归档记录可还原 / 永久删除
- 手动 WebDAV 同步（单文件 `notes.json`，内网 NAS），启动不自动同步
- 技术栈：Kotlin + Jetpack Compose 原生 APK（当前 v0.6，原型验收后实现）

## 目录

| 路径 | 内容 |
|---|---|
| `prototype/` | 高保真 HTML 交互原型（设计契约，用户已验收） |
| `android/` | Kotlin + Compose 原生 App 源码 |
| [GitHub Releases](https://github.com/yuyao110120/ElinkMemo/releases) | 可安装的 debug APK（`dist/` 不入库） |
| `docs/SPEC.md` | 冻结规格说明书 |
| `docs/BUILD.md` | 构建 / 安装 / 环境说明 |
| `docs/research/` | 三份事实调研（墨水屏平台、WebDAV/CORS、Leaf2 与构建链） |
| `tools/` | 本地工具链（JDK17 / Gradle / Android SDK）与 QA 脚本（不入交付） |

## 安装 APK（Leaf2）

1. 从 [Releases](https://github.com/yuyao110120/ElinkMemo/releases) 下载 `ElinkMemo-0.6-debug.apk`，传到设备（USB 拷贝 / BooxDrop / 微信文件传输助手均可）
2. 设备上点击安装；首次需允许「安装未知应用」
3. 打开 ElinkMemo：右滑划掉、点击编辑、+ 新增、⋯ 设置
4. 设置 → WebDAV 设置：填 NAS 地址（如 `http://192.168.1.x:5005/`）、用户名、密码、远端文件（默认 `ElinkMemo/notes.json`）→ 测试连接 → 保存
5. 设置 → 立即同步；首次同步会把本机数据写到远端（远端已有数据且本机也有数据时会弹冲突选择）

USB 调试方式（可选）：

    tools/android-sdk/platform-tools/adb install -r ElinkMemo-0.6-debug.apk

## 本地跑原型

    python3 -m http.server 4173 --bind 0.0.0.0 --directory prototype

Chrome 打开 http://127.0.0.1:4173/ ，Leaf2 浏览器打开 `http://<Mac 局域网 IP>:4173/`。

## 构建与发布

构建见 [docs/BUILD.md](docs/BUILD.md)。发布走 GitHub Releases（APK 不入库）：

    git tag -a v0.7 -m "ElinkMemo v0.7" && git push origin v0.7
    gh release create v0.7 dist/ElinkMemo-0.7-debug.apk --title "ElinkMemo v0.7" --notes-file tools/release-notes.md


## v0.6 变更（2026-10-18）

1. WebDAV 用户名/密码改为 Android Keystore 加密存储：AES-256-GCM 密钥保存在 AndroidKeyStore（密钥不出安全区），SharedPreferences 中只有 IV+密文 blob
2. 从 v0.5 升级时自动迁移：首次启动检测旧明文 sync_user / sync_pass，加密写入后删除明文
3. URL / 远端路径仍明文存于私有 SharedPreferences（非敏感）；其它功能与 UI 未改动

## v0.5 变更（2026-10-18）

1. 关于页版本号改为读取构建产物（BuildConfig.VERSION_NAME），不再手写，后续版本自动同步；本次显示 0.5

## v0.4 变更（2026-10-18）

1. 底部功能栏高度由 20dp 调整为 40dp（图标 24dp，页码 13sp）
2. 帮助页经真机确认无问题，未改动

## v0.3 变更（2026-10-18）

1. 首次启动提示增加翻页说明（上下滑或翻页键翻页）
2. 设置新增「帮助」页：操作说明 + 同步配置说明；关于页精简为名称 / 版本两行
3. 底部功能栏高度固定为 20dp

## v0.2 变更（2026-10-18）

1. 底部功能栏高度改为等于系统状态栏高度，图标与页码按比例缩小
2. 键盘弹出改为 `adjustResize`：列表在剩余空间内等比缩放，不再被整体顶出屏幕
3. WebDAV 配置字段增加占位示例文字（地址 / 账号 / 密码）
4. App 图标改为简笔便签线稿（折角 + 三行横线）
5. 实体翻页键兼容范围扩大：PAGE_UP/DOWN、音量键、方向键；未命中的按键会打 `ElinkMemoKey` 日志便于真机排查

## 已知限制（v0.6）

- 允许明文 HTTP（仅适合内网自用；后续版本收窄为 network security config 白名单）
- 实体翻页键：Leaf2 若按了没反应，请把 Boox 实体键映射改成「翻页键」或「音量键」再试；仍未触发可用 `adb logcat -s ElinkMemoKey` 抓按键码发我
- 未接 Onyx 私有 SDK，App 内无法强制整页刷新（依赖翻页/系统整刷；真机残影明显再补）
- 冲突处理是极简二选一（保留本机 / 保留远端），不做条目级合并

## 冒烟测试（Android 11 模拟器，2026-10-18）

在同一台 Mac 的 Android 11 模拟器（1080×1920）上安装 APK 并逐项验证，全部通过、无崩溃：

- 首次启动、首启提示、纯黑白渲染
- 连续新增 12 条、单页 10 条分页、页码 1/2、2/2
- 实体翻页键（PAGE_UP = keycode 92）翻页
- 右滑划掉（删除线 + 灰）、点击/左滑编辑
- 设置页、WebDAV 表单、测试连接（真实 HTTP 请求）
- 真实 WebDAV 手动同步：首次上传、远端修改后拉取、双向修改弹出冲突选择、「保留本机」覆盖
- 归档已完成 → 归档记录 → 还原为未完成
- 同步失败提示行（未配置时）
- 强杀重启后数据与完成状态持久化
- v0.2：状态栏等高底栏、键盘弹出列表等比缩放、WebDAV 占位示例、简笔便签图标、音量/方向/翻页键翻页
- v0.3：首启提示含翻页说明、帮助页、精简关于页
- v0.4：40dp 底栏（图标/页码重新配比）
- v0.5：关于页版本号自动读取
- v0.6：Keystore 加密凭据 + 旧明文自动迁移（模拟器验证：迁移后 prefs 无明文、重启后凭据可解密、真实 WebDAV 同步成功）

截图见 [docs/screenshots/](docs/screenshots/)。

> 模拟器与 Leaf2 的 DPI/字体渲染不同，真机上的字号、行高、残影、手势手感仍需你实测反馈。
