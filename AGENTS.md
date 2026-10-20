# AGENTS.md — ElinkMemo 协作规则与项目备忘

本文件是给后续 agent 会话的**项目记忆**。开始工作前先读这里，再读 `README.md`、`docs/SPEC.md`、`docs/BUILD.md`。

## 所有者规则（2026-10-20 起，优先级最高）

### 1. 语义化版本 vX.Y.Z
- `versionName` 用三段式 `X.Y.Z`（不带 v），如 `0.11.0`；git tag 与 GitHub Release 用 `vX.Y.Z`，如 `v0.11.0`。
- `versionCode` 用 `X*10000 + Y*100 + Z`，保证单调递增（例：`0.11.0` → `1100`，`0.11.1` → `1101`）。
- 版本号在 `android/app/build.gradle.kts` 修改；Release 说明放 `docs/releases/vX.Y.Z.md`（CI 自动读取）。
- 当前 `v0.10` 是规则生效前的两段版本号，保持不动；从下一个版本起一律三段式。

### 2. 任何改动必须先由所有者确认，再 push / release
- 未得到所有者明确确认（例如「确认」「可以推送」「发布吧」）之前，**禁止** `git push`、打 tag、创建或更新 GitHub Release。
- 标准流程：在本地或 worktree 完成改动 → 构建 + 测试 + 截图/差异摘要 → 等所有者确认 → 再 push、tag、触发 CI 发布。
- 确认前可以本地 commit，但不要推远端；已 push 的改动要回滚也必须先询问。

### 3. 使用 git worktree
- 功能/修复在独立 worktree + 分支中进行，不直接修改主 checkout。
- 可用 `create_worktree` 工具（分支名如 `feat/swap-reorder`、`fix/hint-copy`），或 `git worktree add ../ElinkMemo-<name> -b <branch>`。
- 确认后再合并回 `main`（默认 fast-forward 或 `--no-ff`），然后 push / 打 tag / 发布；完成后清理 worktree。
- 例外：本次 `AGENTS.md` 规则文件为即时生效，直接在主 checkout 本地提交（未 push）。

## 项目速览

- 墨水屏极简便签/待办 App，目标设备文石 Boox Leaf2（7 英寸 / 300ppi / Android 11 / arm64-v8a），Kotlin + Jetpack Compose 原生实现。
- 仓库：https://github.com/yuyao110120/ElinkMemo（public，默认分支 `main`）
- 设计约束：严格 1-bit（纯黑/白，唯一灰 #808080 仅用于已完成文字）；10 条/页分页；底栏 40dp；二级页行高 64dp；无动画/圆角/阴影。
- 已有功能：右滑划掉、点击/左滑编辑、`+` 连续新增、手动 WebDAV 同步、Keystore 加密凭据、归档、系统/像素字体切换、每条前像素小图、点小图选中后点另一条交换位置。
- 原型与冻结规格：`prototype/`、`docs/SPEC.md`；调研：`docs/research/`。

## 构建 / 测试

本地工具链都在 `tools/`（已被 git 忽略）：JDK17、Gradle、Android SDK、AVD。构建：

    cd android
    JAVA_HOME=../tools/jdk17/Contents/Home \
    GRADLE_USER_HOME=../tools/gradle-home \
    ELINKMEMO_DEBUG_KEYSTORE=../tools/signing/elinkmemo-debug.keystore \
    ./gradlew --no-daemon assembleDebug

模拟器（AVD `elinktest`，无窗口）：

    TMPDIR=$PWD/tools/tmp HOME=$PWD/tools/home \
    ANDROID_HOME=$PWD/tools/android-sdk ANDROID_USER_HOME=$PWD/tools/android-user-home \
    tools/android-sdk/emulator/emulator -avd elinktest -no-window -no-audio -no-boot-anim \
      -no-snapshot -gpu swiftshader_indirect -no-metrics

真机 Leaf2：USB + USB 调试，`adb devices` 确认 serial（常见为 `27755393`），用 `adb -s <serial> install -r ...`。截图可用 `adb shell screencap -p /sdcard/x.png && adb pull`。

## 发布流程（仅在所有者确认后执行）

1. 改 `versionCode` / `versionName`（三段式），新增 `docs/releases/vX.Y.Z.md`。
2. `git push origin main`。
3. `git tag -a vX.Y.Z -m "ElinkMemo vX.Y.Z" && git push origin vX.Y.Z`。
4. `.github/workflows/release.yml` 自动构建 APK 并创建 Release（说明读取 `docs/releases/vX.Y.Z.md`）。
5. 验证 Release 资产：versionCode/versionName、证书 SHA-256 `b337bcc8…bd12c2`、下载 200。

注意：不要删除或替换已发布的 Release/资产、不要改已推送的 tag，除非所有者明确要求。

## 签名与安全

- 项目签名 keystore：`tools/signing/elinkmemo-debug.keystore`（同时存于 GitHub Secret `ANDROID_DEBUG_KEYSTORE_BASE64`），证书 SHA-256 指纹 `b337bcc8…bd12c2`。
- **绝不提交 keystore、账号密码、token**；WebDAV 凭据在设备端用 Android Keystore 加密，仓库里不应出现明文。
- 不要更换签名 keystore：已安装用户会无法覆盖升级（只能卸载重装）。

## 未决/待办

- 若所有者需要：首次启动提示文案、选中态视觉（当前整行反白）、底栏提示文案都还可再调。
- 可选优化：network security config 收窄明文 HTTP；Onyx SDK 整页刷新；条目级同步合并。
