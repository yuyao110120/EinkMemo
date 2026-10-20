package com.elinkmemo.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Main }
        MenuRow("WebDAV 设置", chevron = true) {
            store.clearTestResult()
            store.screen = Screen.WebDav
        }
        MenuRowTall("立即同步", syncText(store)) { store.syncNow() }
        MenuRowTall(
            "归档已完成 (" + store.pendingArchiveCount + ")",
            "把划掉的条目移入归档记录",
        ) { store.archiveDone() }
        MenuRow("归档记录 (" + store.archivedCount + ")", chevron = true) {
            store.selectedArchiveId = null
            store.screen = Screen.Archive
        }
        MenuRowTall(
            "字体",
            if (store.fontMode == FontMode.Pixel) "像素字体 · 点击切换为系统" else "系统字体 · 点击切换为像素",
        ) { store.toggleFontMode() }
        MenuRow("帮助", chevron = true) { store.screen = Screen.Help }
        MenuRow("关于", chevron = true) { store.screen = Screen.About }
    }
}

private fun syncText(store: MemoStore): String {
    if (store.syncing) return "同步中…"
    val at = store.lastSyncAt ?: return "尚未同步"
    val status = when (store.lastSyncOk) {
        true -> "成功"
        false -> "失败"
        null -> "—"
    }
    return "上次同步：" + fmtTime(at) + " · " + status
}

@Composable
fun WebDavScreen(store: MemoStore) {
    var cfg by remember { mutableStateOf(store.syncConfig) }
    val saveAndBack: () -> Unit = {
        store.updateSyncConfig(cfg)
        store.screen = Screen.Settings
    }
    BackHandler { saveAndBack() }
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { saveAndBack() }
        FieldRow("地址", cfg.url, "http://192.168.1.10:5005/", onValueChange = { cfg = cfg.copy(url = it) })
        FieldRow("用户名", cfg.user, "你的 NAS 账号", onValueChange = { cfg = cfg.copy(user = it) })
        FieldRow("密码", cfg.pass, "你的 NAS 密码", onValueChange = { cfg = cfg.copy(pass = it) }, isPassword = true)
        FieldRow("远端文件", cfg.remotePath, "ElinkMemo/notes.json", onValueChange = { cfg = cfg.copy(remotePath = it) })
        ButtonRow("测试连接", store.testResult.ifBlank { "未测试" }) {
            store.updateSyncConfig(cfg)
            store.testConnection()
        }
        ButtonRow("保存") { saveAndBack() }
    }
}

@Composable
fun ArchiveScreen(store: MemoStore) {
    val archived = store.archivedItems
    Box(Modifier.fillMaxSize().background(InkWhite)) {
        Column(Modifier.fillMaxSize()) {
            NavRow {
                store.selectedArchiveId = null
                store.screen = Screen.Settings
            }
            if (archived.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(22.dp)) {
                    InkText("暂无归档", 18.sp)
                }
            } else {
                val grouped = archived.groupBy { dayKey(it.archivedAt ?: 0L) }
                LazyColumn(Modifier.weight(1f)) {
                    grouped.forEach { (day, dayItems) ->
                        item(key = "header-" + day) { GroupHeader(fmtDay(day)) }
                        items(dayItems, key = { it.id }) { item ->
                            ArchiveRow(item) { store.selectedArchiveId = item.id }
                        }
                    }
                }
            }
        }
        val selectedId = store.selectedArchiveId
        if (selectedId != null) {
            ArchiveActionSheet(
                onRestore = {
                    store.restoreArchived(selectedId)
                    store.selectedArchiveId = null
                },
                onDelete = {
                    store.deleteArchived(selectedId)
                    store.selectedArchiveId = null
                },
                onCancel = { store.selectedArchiveId = null },
            )
        }
    }
}

@Composable
private fun ArchiveRow(item: NoteItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkText(
                item.text,
                22.sp,
                color = InkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        RowSeparator()
    }
}

@Composable
private fun ArchiveActionSheet(
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onCancel)
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(InkWhite)) {
            RowSeparator()
            ButtonRow("还原为未完成", onClick = onRestore)
            ButtonRow("永久删除", onClick = onDelete)
            ButtonRow("取消", onClick = onCancel)
        }
    }
}

@Composable
fun AboutScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Settings }
        Column(Modifier.padding(22.dp)) {
            InkText("ElinkMemo", 34.sp)
            Spacer(Modifier.height(10.dp))
            InkText("版本 " + BuildConfig.VERSION_NAME + " · 墨水屏极简便签", 16.sp)
            Spacer(Modifier.height(6.dp))
            InkText("像素字体：Fusion Pixel Font · SIL OFL 1.1", 14.sp)
        }
    }
}

@Composable
fun HelpScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Settings }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            HelpSection("操作说明")
            HelpLine("右滑条目：划掉完成；再右滑取消")
            HelpLine("点击或左滑条目：编辑文本")
            HelpLine("点条目前的小图：选中后再点任意一条，交换两条位置")
            HelpLine("右下 +：新增；键盘上回车保存并继续输入")
            HelpLine("翻页：上下滑，或使用设备的翻页 / 音量键")
            HelpLine("左下 ⋯：进入设置（WebDAV、归档、帮助）")
            HelpLine("已完成不会自动消失；在设置里「归档已完成」后移入归档记录")
            HelpLine("归档记录里点击条目，可还原为未完成或永久删除")
            HelpLine("字体：设置 → 字体，可在系统字体 / 像素字体之间切换")
            Spacer(Modifier.height(22.dp))
            HelpSection("同步配置")
            HelpLine("设置 → WebDAV 设置：填写 NAS 的 WebDAV 地址")
            HelpLine("地址示例：http://192.168.1.10:5005/")
            HelpLine("再填用户名、密码；远端文件默认 ElinkMemo/notes.json，目录会自动创建")
            HelpLine("先点「测试连接」，成功后再点「保存」")
            HelpLine("设置 → 立即同步：手动同步，启动 App 时不会自动同步")
            HelpLine("如果远端和本机都改过，会弹出选择，二选一覆盖")
        }
    }
}

@Composable
private fun HelpSection(title: String) {
    InkText(title, 20.sp)
    Spacer(Modifier.height(8.dp))
    RowSeparator()
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HelpLine(text: String) {
    InkText(text, 16.sp, lineHeight = 24.sp)
    Spacer(Modifier.height(9.dp))
}
