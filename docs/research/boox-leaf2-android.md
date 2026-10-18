# Leaf2 上做墨水屏便签 App：实现事实（2026-10）

**TL;DR**：7 英寸 Carta 1200 / 300ppi / Android 11；刷新只能走 Onyx 私有 SDK（运行时反射固件隐藏类），WebView 默认可能被系统降级为 A2 快刷；没有 Android Studio 时，JDK 17 + cmdline-tools ≈0.5GB 就能出 APK。

## 1 硬件
| 项 | 事实 |
|---|---|
| 屏幕 | 7" E Ink Carta **1200**，1680×1264，300ppi（[IT之家](https://digi.ithome.com/archiver/676/742.htm)；[日方官方稿](https://www.gdm.or.jp/pressrelease/2022/1114/464258) 只写 "Carta"；具体面板批次**不确定**）|
| 前光 | 冷暖双色温、32 级（IT之家）|
| SoC / RAM | 高通四核 / 2GB LPDDR4X |
| 存储 | 32GB eMMC（国际版），国行 64GB；microSD ≤512GB |
| 系统 | Android 11 |
| 按键 | 实体翻页键、双扬声器、G-sensor 自动旋转 |
| 接口 | USB-C、BT 5.0、Wi-Fi 802.11ac |
| 电池 | 2000mAh，待机 26 天 |
| 机身 | 166×137×6mm，黑 185g / 白 170g |

## 2 系统 UI 与浏览器
内置浏览器组件 `com.android.browser/.BrowserActivity`、悬浮球 `com.onyx.floatingbutton`、应用市场 `com.onyx.appmarket`（[AppOpenGuide](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/AppOpenGuide.md)）。导航栏/导航球具体交互、沉浸式全屏是否影响刷新：**不确定**。默认浏览器（NeoBrowser）包名、系统 WebView 版本：**不确定**，随固件与 Play 更新。

## 3 刷新控制（最关键）
| 项 | 事实 |
|---|---|
| API | `com.onyx.android.sdk.api.device.epd.EpdController`（artifact `onyxsdk-device`）|
| 能做 | `invalidate(v, GC)` 整屏刷；`setViewDefaultUpdateMode(v, REGAL)` 按 View；`setAppScopeRefreshMode(UpdateOption)` 全 App；`applySystemFastMode`；`setWebViewContrastOptimize(WebView, bool)` |
| 枚举 | UpdateOption：NORMAL / FAST_QUALITY / REGAL / FAST / FAST_X；UpdateMode：DU / GU / GC / REGAL / ANIMATION / DEEP_GC（[文档](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Update-Mode.md)）|
| 依赖 | `com.onyx.android.sdk:onyxsdk-device:1.3.6`（最新），仓库 `repo.boox.com`；HTTPS 在本机报 *DH key too small*，只能 HTTP + `allowInsecureProtocol`；jcenter 已死。jar 打进 APK |
| 可用性 | 官方为第三方 App 发布（[wiki](https://github.com/onyx-intl/public-wiki)），但类只反射固件里的 `android.onyx.hardware.DeviceController`、`ViewUpdateHelper`；非 Onyx 设备必失败，Onyx 上也受隐藏 API 限制 |
| 降级 | try/catch + 判断 `Build.MANUFACTURER`；失败退回"禁动画 + 整屏重绘"的纯软件方案 |
| Android 11 | 隐藏 API 受限；官方 demo 另打包 `hiddenapibypass 4.3`，是否必需**不确定** |
| WebView A2 | 官方文档称该 API 用于"阻止 WebView 进 A2"；FAST_X 文案为"细节损失大，适合网页/视频" → 自有 WebView 壳可反制，但只在 Onyx 生效 |

### 整页刷新怎么触发
| 方式 | 说明 |
|---|---|
| 手动 | `EpdController.invalidate(v, GC)` / `refreshScreen(v, GC)`，即"刷一下"按钮 |
| 定时 | `EpdDeviceManager.setGcInterval(n)` + `applyWithGCInterval(v, isTextPage)`，由 App 控频（[文档](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EpdDeviceManager.md)）|
| 系统 | 固件自动合并局部刷新并择机整刷，阈值未公开（**不确定**）|
| 网页 | 没有可从 JS 调用的刷新 API，必须由原生壳代劳；EinkBro 源码里**未见** EpdController 调用，也未见独立整页刷新实现——它的策略是零动画 + 交给系统 |

## 4 WebView vs 原生
| 维度 | 结论 |
|---|---|
| 文字清晰度 | 16 级灰度下小字抗锯齿发虚；原生 + REGAL 更稳（推断，**不确定**）|
| 残影/闪烁 | 滚动触发快刷档，细节丢失且留残影（FAST_X 定义）|
| 动画 | 必关：EinkBro 用 `windowAnimationStyle=@null`、`overridePendingTransition(0,0)`、对话框 `windowAnimations=0`（[styles.xml](https://github.com/plateaukao/einkbro/blob/main/app/src/main/res/values/styles.xml)）|
| 沉浸式 | 与刷新无直接耦合证据（**不确定**）|
| 结论 | 静态列表页 WebView 可行；EinkBro 已用 Compose + WebView 且**不接** Onyx SDK（[build.gradle.kts](https://github.com/plateaukao/einkbro/blob/main/app/build.gradle.kts)）|

Onyx 官方适配建议：黑白为主、避免半透明遮罩、翻页替代滑动、禁动画、字号 ≥14sp、按钮 ≥36/48dp（[中文指南](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Eink-Develop-Guide_zh.md)）。

## 5 构建与侧载（macOS arm64、无 Studio、现有 JDK 8）
| 组件 | 版本 | 下载体积 |
|---|---|---|
| JDK | Temurin 17（[AGP 8.x 要求](https://developer.android.com/build/releases/gradle-plugin)）| ~180MB |
| cmdline-tools | latest | 153MB |
| platform-tools | r37 | 9MB |
| platforms;android-33 | — | 67MB |
| build-tools;34.0.0 | — | 61MB |
| Gradle | 8.7（Java 17 自 7.3 起支持）| 134MB |
| AGP | 8.5.2 | 12MB |

合计 ≈0.5GB，不含 Maven 依赖。建议 compileSdk 34 / **targetSdk 30**（= Leaf2 的 Android 11，避开 scoped storage）/ minSdk 26。debug 自签足够安装：`adb install -r app-debug.apk`，或 BooxDrop / USB 拷贝后点击安装。

## 6 安装、商店与实体键
- 未知来源：设置 > 应用 > 特殊应用权限 > 安装未知应用（`ACTION_MANAGE_UNKNOWN_APP_SOURCES`，[AOSP Settings.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java)）。
- 开发者选项：连点"版本号"7 次（AOSP `TAPS_TO_BE_A_DEVELOPER = 7`）→ 开 USB 调试。
- Google Play：官方稿称支持；国行/固件差异**不确定**。BooxDrop 为官方无线传文件功能（[官方](https://shop.boox.com/blogs/news/booxdrop)）。
- 实体键：`KEYCODE_PAGE_UP=92`、`PAGE_DOWN=93`、`VOLUME_UP=24`（[AOSP KeyEvent](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/KeyEvent.java)）；前台 `dispatchKeyEvent` 可拦，用于快捷操作；Boox 是否可改映射**不确定**。

## 7 对原型的直接影响
1. 零动画、整屏切换、纯黑白、正文 ≥16sp、避免半透明遮罩。
2. 列表避免大面积重绘：分页/整屏替换，而非连续滚动。
3. 刷新策略做成可插拔接口：Onyx 反射实现 + 空实现，保证非 Onyx 设备不崩。

## 8 待真机验证（研究未覆盖）
- Leaf2 当前固件下 EpdController 是否仍生效（隐藏 API 是否被拦）。
- 实体翻页键真实 keycode：92/93 还是 24/25。
- 系统 WebView 版本与默认浏览器包名（`adb shell dumpsys webviewupdate`）。
