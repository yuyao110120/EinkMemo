package com.elinkmemo.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppRoot(activity: MainActivity) {
    val context = LocalContext.current
    val store = remember { MemoStore(context.applicationContext) }

    DisposableEffect(activity, store) {
        activity.pageKeyHandler = { delta ->
            if (store.editing == null && store.screen == Screen.Main) {
                store.dismissHint()
                store.turnPage(delta)
                true
            } else {
                false
            }
        }
        onDispose { activity.pageKeyHandler = null }
    }

    BackHandler(
        enabled = store.conflict != null || store.selectedArchiveId != null ||
            store.editing != null || store.screen != Screen.Main
    ) {
        when {
            store.conflict != null -> store.cancelConflict()
            store.selectedArchiveId != null -> store.selectedArchiveId = null
            store.editing != null -> store.cancelEdit()
            store.screen == Screen.Settings -> store.screen = Screen.Main
            else -> store.screen = Screen.Settings
        }
    }

    Box(Modifier.fillMaxSize().background(InkWhite)) {
        when (store.screen) {
            Screen.Main -> MainScreen(store)
            Screen.Settings -> SettingsScreen(store)
            Screen.WebDav -> WebDavScreen(store)
            Screen.Archive -> ArchiveScreen(store)
            Screen.About -> AboutScreen(store)
            Screen.Help -> HelpScreen(store)
        }
        if (!store.seenHint && store.screen == Screen.Main) {
            HintOverlay()
        }
        if (store.conflict != null) {
            ConflictOverlay(store)
        }
    }
}
