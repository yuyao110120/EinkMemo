# 墨水屏平台事实核查（截至 2026-10）

**结论**：能可靠运行"本地优先 Web App"的只有 Android 墨水屏（文石、海信、Bigme）。网页层无法控制面板刷新，刷新策略掌握在系统或原生壳手里。

## 1. 可运行 Web App 的能力矩阵

| 平台 | 系统 / 浏览器 | Web App / PWA | 存储与网络 | JS 性能 |
|---|---|---|---|---|
| 文石 Boox | Android（历代 6–13，[wiki](https://en.wikipedia.org/wiki/Onyx_Boox)）；自带 NeoBrowser，可另装 Chrome 或 WebView 壳 | ✅ 可离线 PWA | IndexedDB / localStorage / Service Worker 均可用（Chromium；SW 需 HTTPS）；TLS 走系统 CA | 低端安卓 |
| 海信 Hisense | Android，可侧载 APK（未取得一手页，版本**不确定**） | ✅ | 同上 | 低端安卓 |
| Bigme | Android 11–14（**不确定**，同上） | ✅ | 同上 | 低端安卓 |
| Dasung | 纯显示器，无 OS，靠 HDMI/DP（[60Hz 面板](https://shop.dasung.com/products/dasung-paperlike-103-the-worlds-first-60hz-e-ink-monitor)） | 取决于所接电脑 | — | — |
| Kindle | "体验版"浏览器，NetFront/WebKit（[wiki](https://en.wikipedia.org/wiki/Amazon_Kindle)、[Amazon 帮助](https://www.amazon.com/gp/help/customer/display.html?ie=UTF8&nodeId=200137070)） | ❌ 无 SW / 无 PWA 安装 | localStorage 可用；IndexedDB **不确定** | 很弱 |
| Kobo | 多数机型内置 Beta 浏览器（[官方说明](https://help.kobo.com/hc/en-us/articles/360017763733-About-Beta-Features)） | ❌ | 受限，**不确定** | 弱 |
| reMarkable | Codex（Linux），无浏览器（[wiki](https://en.wikipedia.org/wiki/ReMarkable)） | ❌，需社区 hack | — | — |
| PocketBook | 自带浏览器，功能受限（[wiki](https://en.wikipedia.org/wiki/PocketBook_International)） | ⚠️ 勉强 | 受限 | 弱 |
| Supernote | 无浏览器、无第三方 App（**不确定**） | ❌ | — | — |

内核版本随机型与固件变化，需在设备上读 UA 实测（**不确定**）；Chromium 系 fetch/WebSocket 正常，老固件可能缺新根证书。

刷新模式是核心变量：Boox 的档位（HD / Regal 等，名称随固件变化）映射到 SDK 的 NORMAL / REGAL / FAST / FAST_X 与 DU / GU / ANIMATION 波形（[Onyx SDK](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Update-Mode.md)）。SDK 文档明确提到 WebView 会掉进 A2 快速模式、需原生封装来阻止（[EpdController](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EpdController.md)）。**截至 2026-10 没有可从网页调刷新的 Web API**，只有原生 `EpdController` 能改 UpdateMode——网页何时被降级成单色快速模式，网页自己说了不算。

## 2. 典型屏幕参数

| 型号 | 尺寸 | 分辨率 | PPI | 色彩 | 交互 |
|---|---|---|---|---|---|
| Kindle Oasis 2 | 7" | 1264×1680 | 300 | 单色 16 灰阶 | 触控 + 实体翻页键 |
| reMarkable 2 | 10.3" | 1872×1404 | ≈226 | 单色 | 触控 + 手写笔 |
| reMarkable Paper Pro | 11.8" | 2160×1620 | ≈229 | Gallery 3 彩色 | 触控 + 笔 |
| Boox Tab Ultra C | 10.3" | 1860×2480 | 300 / 彩色 150 | Kaleido 3 | 触控 + 笔 |
| Kobo Clara Colour | 6" | 1072×1448 | 300 / 150 | Kaleido 3 | 触控 |
| Kaleido 3 面板 | 7.8 / 10.3 / 13.3" | 依整机 | 300 / 150 | 4096 色 | 依整机 |

型号行数据来自维基百科对应机型条目；Kaleido 3 面板规格见 [E Ink 官方页](https://www.eink.com/brand/detail/Kaleido3)。未逐行核对的数值标"≈"。

## 3. 渲染优化的客观约束

- 物理层：电子墨水双稳态、反射式，像素变化靠微胶囊迁移，刷新延迟明显（具体毫秒数随面板与模式变化，**不确定**）；局部刷新累积残影，需要周期性整屏刷新清除。
- 会引发大范围刷新的操作（机制推断，固件策略各异）：整页导航、旋转、软键盘改变视口、大面积背景变化、切前后台。CSS 改不了，只能避免触发。
- 因此：纯黑白、1px 实线、正文 ≥16px、禁用动画/过渡/平滑滚动/阴影/半透明/backdrop-filter、用分页替代长滚动、避开 sticky 与 100vh、把 `prefers-reduced-motion` 当降级信号（它并不等同墨水屏，只是最接近的开关）。

## 4. Kaleido 3 的现实限制

4096 色、16 灰阶，彩色 150ppi 只有黑白 300ppi 的一半，饱和度仅比上代 Kaleido Plus 高 30%（[E Ink](https://www.eink.com/brand/detail/Kaleido3)）。彩色来自滤光片，亮度和锐度同时下降；彩色区细线与小字发糊；纯色块稳定，渐变容易脏。具体 sRGB 覆盖率各家实测差异大，**不确定**。

## 5. 1-bit 抖动的可行性

可行但代价明确：必须 1:1 像素、禁止缩放，否则出摩尔纹；抖动噪点会让每次局部刷新的变化面积变大、残影更重，滚动时闪烁明显；彩色滤光片下 RGB 子像素还会错位。适合图标、插图，且应预渲染成位图缓存；不适合正文与列表。（算法参考 [Ditherpunk](https://surma.dev/things/ditherpunk/)）

## 6. 值得参考的开源项目

| 项目 | 是什么 | 取舍 |
|---|---|---|
| [EinkBro](https://github.com/plateaukao/einkbro) | 专为墨水屏改的 Android 浏览器 | 把"整页刷新""翻页"做成壳层按钮，证明刷新要在原生层做 |
| [KOReader](https://github.com/koreader/koreader) | Lua 阅读器，覆盖 Kindle/Kobo/Android | 分页 + 局部刷新的极致样本，但生态封闭、非 Web |
| [Saber](https://github.com/saber-notes/saber) | Flutter 手写笔记 | 手写延迟处理可借鉴，体量大 |
| [Tasks.org](https://github.com/tasks/tasks) | 开源待办 | 数据模型可参考，Material 动效偏重 |
| [Notally](https://github.com/OmGodse/Notally) | 极简本地笔记 | 交互最接近"便签"，但纯 Android |

没有真正的"墨水屏待办"标杆，最接近的组合是 EinkBro 的刷新交互 + Notally 的极简界面。

## 7. 对架构的三条硬约束

1. 目标设备只锁 Android 墨水屏。PWA + IndexedDB 可离线，但"刷新控制"必须靠 WebView 壳调用厂商 SDK，纯浏览器做不到。
2. 交互按"整屏替换"设计，提供手动整页刷新；动画、过渡、平滑滚动一律不要。
3. 彩色机型按 150ppi 排信息密度；Kaleido 只做辅助，不做信息载体。
