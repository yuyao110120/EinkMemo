# WebDAV 同步方案调研（ElinkMemo）

**结论**：浏览器直连主流 WebDAV 基本不可行（CORS）；Android 原生壳或「本地文件夹 + FolderSync」可行；同步用「单文件 + ETag + 墓碑」即可，不必上 CRDT。

## 1 浏览器直连与 CORS

`PROPFIND` 不是安全方法、`Authorization` 不是安全头，浏览器必先发 `OPTIONS` 预检；服务端须回 `Access-Control-Allow-Origin` 等头（[Fetch 标准](https://fetch.spec.whatwg.org/#cors-preflight-fetch)）。2026-10-18 实测 + 一手来源：

| 服务 | CORS | 证据 |
|---|---|---|
| 坚果云 | 无：预检 401，无 ACAO | 实测 `OPTIONS dav.jianguoyun.com/dav/` |
| Nextcloud | 默认无：预检 401；需 [WebAppPassword](https://apps.nextcloud.com/apps/webapppassword) 或 #40537 | [#3131](https://github.com/nextcloud/server/issues/3131)（35.0.1 复测） |
| Synology | 无：预检超时/504 | [社区实测](https://tevinzhang.com/en/fix-cors-for-synology-webdav-no-nginx-hacks/) |
| Alist | 有：全局 gin CORS，默认 `*` | [源码](https://github.com/AlistGo/alist/blob/main/server/router.go)、[默认值](https://github.com/AlistGo/alist/blob/main/internal/conf/config.go#L201) |
| rclone | 需手动 `--allow-origin` | [文档](https://rclone.org/commands/rclone_serve_webdav/) |
| Koofr | 无：预检 401，无 ACAO | 实测 `app.koofr.net/dav/Koofr/` |
| pCloud | 无：预检 401，无 ACAO | 实测 `webdav.pcloud.com` |
| Box | 已停用 | [2023-04-28 EOL](https://support.box.com/hc/en-us/articles/360052806073) |

替代方案与代价：

| 方案 | 代价 |
|---|---|
| 自建反代（Caddy/Nginx） | 需服务器、域名、证书；可加缓存 |
| Cloudflare Worker | 免运维，但凭据经第三方，需处理流式/超时 |
| 原生壳（CapacitorHttp 走原生网络） | 需打包上架；无 CORS |
| 服务端代理 | 同反代，另担隐私与运维 |

## 2 协议与库

- 方法：`PROPFIND`（Depth 0/1/infinity，结果 207 Multi-Status XML）、`MKCOL`、`GET`、`PUT`、`DELETE`、`MOVE`、`COPY`（[RFC 4918 §9](https://www.rfc-editor.org/rfc/rfc4918#section-9)）。
- `MKCOL` 父目录缺失返回 409，须逐级创建（§9.3）。
- 条件写：`If-Match: "<etag>"` 失败回 412；新建用 `If-None-Match: *`（[RFC 9110 §13.1](https://www.rfc-editor.org/rfc/rfc9110#section-13.1.1)）。弱 ETag（`W/`）不能用于 If-Match（[RFC 4918 §8.6](https://www.rfc-editor.org/rfc/rfc4918#section-8.6)）；PUT 响应不保证带新 ETag，写后应重新 PROPFIND。
- 库：`webdav` 5.11.0（2026-09，浏览器+Node，v5 仅 ESM，浏览器无流）[README](https://github.com/perry-mitchell/webdav-client)；[`dav`](https://www.npmjs.com/package/dav) 1.8.0（2018 停更）；[`webdav-client`](https://www.npmjs.com/package/webdav-client) 1.4.3（2019 停更）。
- 坚果云：必须用应用密码，地址 `https://dav.jianguoyun.com/dav/`；免费 600 次/30 分钟、付费 1500；单文件 500MB；单请求 750 项（[官方](https://help.jianguoyun.com/?p=2064)、[地址](https://help.jianguoyun.com/?p=3168)）。

## 3 本地优先同步设计

| 方案 | 请求数 | 冲突粒度 | 适用 |
|---|---|---|---|
| 单 `notes.json` | 1 GET + 1 PUT | 整文件 | 单清单、条目少 ✅ |
| 每条目一文件 | 1 PROPFIND + N PUT | 条目 | 条目多、协作 |
| `todo.txt`/md | 1 + 1 | 行/段 | 人类可读、可 diff |

并发：文件级 LWW + ETag 最简单；条目级用 `updatedAt`/`deletedAt` 墓碑合并可保留双方编辑；CRDT 最重——Yjs 293KB JS、Automerge WASM 3.56MB、Loro WASM 3.32MB（未压缩；[Yjs](https://unpkg.com/browse/yjs@13.6.33/dist/)、[Automerge](https://unpkg.com/browse/@automerge/automerge@3.5.0/dist/)、[Loro](https://unpkg.com/browse/loro-crdt@1.16.4/)），低端墨水屏不划算。

**建议**：单文件 JSON（或 todo.txt）+ `If-Match` 条件写 + 条目墓碑；412 时 GET→合并→重试；按需同步，不后台轮询。理由：清单小，整文件合并比多请求和 CRDT 运行时更省电，也便于导出备份。

## 4 浏览器存储与凭据

- localStorage 每源 5MiB（[MDN](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)）。
- IndexedDB 容量大（Firefox 10% 磁盘、Safari 60%），默认 best-effort，可 `navigator.storage.persist()` 申请持久化（[MDN](https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/persist)、[WebKit](https://webkit.org/blog/14403/updates-to-storage-policy/)）；Safari 长期不访问仍可能清理。
- Service Worker 缓存壳与离线队列（[MDN](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)）。
- 凭据勿放 localStorage（XSS 可读，[OWASP](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)）：仅内存保存，或口令派生密钥 + WebCrypto 加密后存 IndexedDB；原生壳用 Keystore；用应用密码而非主密码。

## 5 本地测试

`rclone serve webdav --allow-origin http://localhost:5173` 最轻；wsgiDAV 带 `Cors` 中间件（[文档](https://wsgidav.readthedocs.io/en/latest/)）；Nextcloud [Docker](https://hub.docker.com/_/nextcloud) + WebAppPassword 做真实服务。自动化用 [Playwright](https://playwright.dev/) 在浏览器里断言 207/412——CORS 只在浏览器生效，curl 不能替代。

## 6 Android 文件夹 + 同步 App

可行且常见：App 用 SAF [`ACTION_OPEN_DOCUMENT_TREE`](https://developer.android.com/training/data-storage/shared/documents-files) 选目录，`FLAG_GRANT_PERSISTABLE_URI_PERMISSION` 持久授权（[Intent](https://developer.android.com/reference/android/content/Intent#FLAG_GRANT_PERSISTABLE_URI_PERMISSION)），FolderSync 通过 WebDAV 上传（[Play](https://play.google.com/store/apps/details?id=dk.tacit.android.foldersync.full)）；Android 11+ 分区存储下这是合规路径（[`MANAGE_EXTERNAL_STORAGE` 受 Play 限制](https://support.google.com/googleplay/android-developer/answer/10467955)）。Syncthing 无 WebDAV 后端（[社区](https://forum.syncthing.net/t/syncthing-and-webdav-on-nas/22332)）。代价：同步延迟、两个 App、文件级冲突；但绕开 CORS 且服务商无关。

**架构建议**：Android/墨水屏 App 用原生网络直连 WebDAV；PWA 不要假设能直连坚果云/Nextcloud/Synology，需原生壳或用户自建代理。

## 7 不确定 / 待实测

- 坚果云 ETag 强弱、是否支持 `If-Match`、并发写行为：未实测（无账号）。
- Synology 不同 DSM 版本、不同反代下的预检表现可能不同。
- pCloud/Koofr 官方未声明 CORS，结论仅来自 2026-10-18 预检实测。
- CRDT 在低端墨水屏上的实际内存/帧率未实测；体积数据来自 unpkg 未压缩文件。
- 除坚果云外，其他服务的单文件大小/频率限制未逐一核实。