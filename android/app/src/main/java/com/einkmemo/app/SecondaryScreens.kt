package com.einkmemo.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Main }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        MenuRow(stringResource(R.string.settings_webdav), chevron = true) {
            store.clearTestResult()
            store.screen = Screen.WebDav
        }
        MenuRowTall(stringResource(R.string.settings_sync_now), syncText(store)) { store.syncNow() }
        MenuRowTall(
            stringResource(R.string.settings_archive_done, store.pendingArchiveCount),
            stringResource(R.string.settings_archive_done_sub),
        ) { store.archiveDone() }
        MenuRow(stringResource(R.string.settings_archive_records, store.archivedCount), chevron = true) {
            store.selectedArchiveId = null
            store.screen = Screen.Archive
        }
        MenuRowTall(
            stringResource(R.string.settings_font),
            stringResource(
                if (store.fontMode == FontMode.Pixel) R.string.settings_font_pixel_sub
                else R.string.settings_font_system_sub
            ),
        ) { store.toggleFontMode() }
        MenuRowTall(
            stringResource(R.string.settings_language),
            languageName(store.languageMode),
        ) { store.screen = Screen.Language }
        MenuRow(stringResource(R.string.settings_help), chevron = true) { store.screen = Screen.Help }
        MenuRow(stringResource(R.string.settings_about), chevron = true) { store.screen = Screen.About }
        }
    }
}

@Composable
private fun languageName(mode: LanguageMode): String = stringResource(
    when (mode) {
        LanguageMode.System -> R.string.language_system
        LanguageMode.English -> R.string.language_english
        LanguageMode.Chinese -> R.string.language_chinese
    }
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun applyLanguage(store: MemoStore, context: Context, mode: LanguageMode) {
    if (store.languageMode == mode) return
    store.updateLanguageMode(mode)
    context.findActivity()?.recreate()
}

@Composable
fun LanguageScreen(store: MemoStore) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Settings }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        LanguageOption(stringResource(R.string.language_system), store.languageMode == LanguageMode.System) {
            applyLanguage(store, context, LanguageMode.System)
        }
        LanguageOption(stringResource(R.string.language_english), store.languageMode == LanguageMode.English) {
            applyLanguage(store, context, LanguageMode.English)
        }
        LanguageOption(stringResource(R.string.language_chinese), store.languageMode == LanguageMode.Chinese) {
            applyLanguage(store, context, LanguageMode.Chinese)
        }
        }
    }
}

@Composable
private fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    MenuRowTall(title, if (selected) stringResource(R.string.language_current) else "") { onClick() }
}

@Composable
private fun syncText(store: MemoStore): String {
    if (store.syncing) return stringResource(R.string.sync_syncing)
    val at = store.lastSyncAt ?: return stringResource(R.string.sync_never)
    val status = when (store.lastSyncOk) {
        true -> stringResource(R.string.sync_ok)
        false -> stringResource(R.string.sync_failed)
        null -> stringResource(R.string.sync_unknown)
    }
    return stringResource(R.string.sync_last, fmtTime(at), status)
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        FieldRow(stringResource(R.string.webdav_url), cfg.url, stringResource(R.string.webdav_url_hint), onValueChange = { cfg = cfg.copy(url = it) })
        FieldRow(stringResource(R.string.webdav_user), cfg.user, stringResource(R.string.webdav_user_hint), onValueChange = { cfg = cfg.copy(user = it) })
        FieldRow(stringResource(R.string.webdav_pass), cfg.pass, stringResource(R.string.webdav_pass_hint), onValueChange = { cfg = cfg.copy(pass = it) }, isPassword = true)
        FieldRow(stringResource(R.string.webdav_remote), cfg.remotePath, stringResource(R.string.webdav_remote_hint), onValueChange = { cfg = cfg.copy(remotePath = it) })
        ButtonRow(stringResource(R.string.webdav_test), store.testResult.ifBlank { stringResource(R.string.webdav_untested) }) {
            store.updateSyncConfig(cfg)
            store.testConnection()
        }
        ButtonRow(stringResource(R.string.webdav_save)) { saveAndBack() }
        }
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
                    InkText(stringResource(R.string.archive_empty), 18.sp)
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
            ButtonRow(stringResource(R.string.archive_restore), onClick = onRestore)
            ButtonRow(stringResource(R.string.archive_delete), onClick = onDelete)
            ButtonRow(stringResource(R.string.cancel), onClick = onCancel)
        }
    }
}

@Composable
fun AboutScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        NavRow { store.screen = Screen.Settings }
        Column(Modifier.padding(22.dp)) {
            InkText("EinkMemo", 34.sp)
            Spacer(Modifier.height(10.dp))
            InkText(stringResource(R.string.about_line, BuildConfig.VERSION_NAME), 16.sp)
            Spacer(Modifier.height(6.dp))
            InkText(stringResource(R.string.about_font), 14.sp)
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
            HelpSection(stringResource(R.string.help_ops_title))
            stringArrayResource(R.array.help_ops_lines).forEach { HelpLine(it) }
            Spacer(Modifier.height(22.dp))
            HelpSection(stringResource(R.string.help_sync_title))
            stringArrayResource(R.array.help_sync_lines).forEach { HelpLine(it) }
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