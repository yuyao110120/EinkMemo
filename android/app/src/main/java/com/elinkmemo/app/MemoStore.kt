package com.elinkmemo.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val PAGE_SIZE = 10

private const val K_ITEMS = "items_json"
private const val K_REVISION = "revision"
private const val K_SEEN_HINT = "seen_hint"
private const val K_SYNC_URL = "sync_url"
private const val K_SYNC_USER = "sync_user"
private const val K_SYNC_PASS = "sync_pass"
private const val K_SYNC_PATH = "sync_path"
private const val K_LAST_SYNC = "last_sync_at"
private const val K_LAST_OK = "last_sync_ok"
private const val K_LAST_ERROR = "last_sync_error"
private const val K_LAST_SYNCED_REV = "last_synced_revision"
private const val K_LAST_REMOTE_REV = "last_remote_revision"
private const val K_FONT_MODE = "font_mode"

data class NoteItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long,
    val doneAt: Long? = null,
    val archivedAt: Long? = null,
    val updatedAt: Long,
)

enum class Screen { Main, Settings, WebDav, Archive, About, Help }

enum class FontMode { System, Pixel }

enum class EditMode { Add, Edit }

data class Editing(val mode: EditMode, val id: String? = null)

data class SyncConflict(
    val remoteItems: List<NoteItem>,
    val remoteRevision: Long,
)

class MemoStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("elinkmemo", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = WebDavClient()
    private val secure = SecureCredentials(context)

    var items by mutableStateOf(loadItems())
        private set
    var revision by mutableStateOf(prefs.getLong(K_REVISION, 0L))
        private set
    var syncConfig by mutableStateOf(loadSyncConfig())
        private set
    var lastSyncAt by mutableStateOf(prefs.getLong(K_LAST_SYNC, 0L).takeIf { it > 0L })
        private set
    var lastSyncOk by mutableStateOf(if (prefs.contains(K_LAST_OK)) prefs.getBoolean(K_LAST_OK, true) else null)
        private set
    var lastSyncError by mutableStateOf(prefs.getString(K_LAST_ERROR, null))
        private set
    var syncing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf("")
        private set
    var conflict by mutableStateOf<SyncConflict?>(null)
        private set

    var screen by mutableStateOf(Screen.Main)
    var page by mutableStateOf(0)
    var editing by mutableStateOf<Editing?>(null)
    var selectedArchiveId by mutableStateOf<String?>(null)
    var seenHint by mutableStateOf(prefs.getBoolean(K_SEEN_HINT, false))
        private set
    var fontMode by mutableStateOf(
        if (prefs.getInt(K_FONT_MODE, 0) == 1) FontMode.Pixel else FontMode.System
    )
        private set

    val activeItems: List<NoteItem>
        get() = items.filter { it.archivedAt == null }
    val archivedItems: List<NoteItem>
        get() = items.filter { it.archivedAt != null }.sortedByDescending { it.archivedAt }
    val pageCount: Int
        get() = maxOf(1, (activeItems.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pendingArchiveCount: Int
        get() = items.count { it.done && it.archivedAt == null }
    val archivedCount: Int
        get() = items.count { it.archivedAt != null }
    val failVisible: Boolean
        get() = lastSyncOk == false

    // ---------- local persistence ----------

    private fun loadItems(): List<NoteItem> {
        val raw = prefs.getString(K_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { itemFromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadSyncConfig(): SyncConfig {
        val legacyUser = prefs.getString(K_SYNC_USER, "") ?: ""
        val legacyPass = prefs.getString(K_SYNC_PASS, "") ?: ""
        var user = ""
        var pass = ""
        val stored = secure.load()
        if (stored != null) {
            user = stored.first
            pass = stored.second
        } else if (legacyUser.isNotEmpty() || legacyPass.isNotEmpty()) {
            // one-time migration from the old plaintext SharedPreferences storage
            secure.save(legacyUser, legacyPass)
            user = legacyUser
            pass = legacyPass
        }
        if (legacyUser.isNotEmpty() || legacyPass.isNotEmpty()) {
            prefs.edit().remove(K_SYNC_USER).remove(K_SYNC_PASS).apply()
        }
        return SyncConfig(
            url = prefs.getString(K_SYNC_URL, "") ?: "",
            user = user,
            pass = pass,
            remotePath = prefs.getString(K_SYNC_PATH, "ElinkMemo/notes.json") ?: "ElinkMemo/notes.json",
        )
    }

    private fun itemFromJson(o: JSONObject): NoteItem = NoteItem(
        id = o.optString("id", UUID.randomUUID().toString()),
        text = o.optString("text", ""),
        done = o.optBoolean("done", false),
        createdAt = o.optLong("createdAt", 0L),
        doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt", 0L),
        archivedAt = if (o.isNull("archivedAt")) null else o.optLong("archivedAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun itemToJson(item: NoteItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("text", item.text)
        put("done", item.done)
        put("createdAt", item.createdAt)
        put("doneAt", item.doneAt ?: JSONObject.NULL)
        put("archivedAt", item.archivedAt ?: JSONObject.NULL)
        put("updatedAt", item.updatedAt)
    }

    private fun itemsToJson(list: List<NoteItem>): JSONArray = JSONArray().apply {
        list.forEach { put(itemToJson(it)) }
    }

    private fun docJson(): String = JSONObject().apply {
        put("version", 1)
        put("revision", revision)
        put("items", itemsToJson(items))
    }.toString()

    private fun persistItems() {
        prefs.edit()
            .putString(K_ITEMS, itemsToJson(items).toString())
            .putLong(K_REVISION, revision)
            .apply()
    }

    private fun bumpRevision() {
        revision += 1
        persistItems()
    }

    // ---------- local actions ----------

    fun toggleDone(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = items[idx]
        if (item.archivedAt != null) return
        val now = System.currentTimeMillis()
        val updated = item.copy(done = !item.done, doneAt = if (!item.done) now else null, updatedAt = now)
        items = items.toMutableList().also { it[idx] = updated }
        bumpRevision()
    }

    fun openAdd() {
        editing = Editing(EditMode.Add)
    }

    fun openEdit(id: String) {
        if (items.none { it.id == id }) return
        editing = Editing(EditMode.Edit, id)
    }

    fun cancelEdit() {
        editing = null
    }

    fun commitText(text: String) {
        val current = editing ?: return
        val trimmed = text.trim()
        if (current.mode == EditMode.Edit) {
            if (trimmed.isNotEmpty()) {
                val idx = items.indexOfFirst { it.id == current.id }
                if (idx >= 0) {
                    val now = System.currentTimeMillis()
                    items = items.toMutableList().also { it[idx] = it[idx].copy(text = trimmed, updatedAt = now) }
                    bumpRevision()
                }
            }
            editing = null
        } else {
            if (trimmed.isEmpty()) {
                editing = null
                return
            }
            val now = System.currentTimeMillis()
            items = items + NoteItem(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                createdAt = now,
                updatedAt = now,
            )
            bumpRevision()
            page = pageCount - 1
        }
    }

    fun archiveDone(): Int {
        val now = System.currentTimeMillis()
        var count = 0
        items = items.map {
            if (it.done && it.archivedAt == null) {
                count++
                it.copy(archivedAt = now, updatedAt = now)
            } else it
        }
        if (count > 0) bumpRevision()
        page = 0
        return count
    }

    fun restoreArchived(id: String) {
        val target = items.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val restored = target.copy(done = false, doneAt = null, archivedAt = null, updatedAt = now)
        items = items.filter { it.id != id } + restored
        bumpRevision()
        page = pageCount - 1
    }

    fun deleteArchived(id: String) {
        if (items.none { it.id == id }) return
        items = items.filter { it.id != id }
        bumpRevision()
    }

    fun turnPage(delta: Int) {
        val next = (page + delta).coerceIn(0, pageCount - 1)
        if (next != page) page = next
    }

    fun dismissHint() {
        if (!seenHint) {
            seenHint = true
            prefs.edit().putBoolean(K_SEEN_HINT, true).apply()
        }
    }

    fun toggleFontMode() {
        fontMode = if (fontMode == FontMode.Pixel) FontMode.System else FontMode.Pixel
        prefs.edit().putInt(K_FONT_MODE, if (fontMode == FontMode.Pixel) 1 else 0).apply()
    }

    // ---------- sync config / test ----------

    fun updateSyncConfig(cfg: SyncConfig) {
        syncConfig = cfg
        prefs.edit()
            .putString(K_SYNC_URL, cfg.url)
            .putString(K_SYNC_PATH, cfg.remotePath)
            .remove(K_SYNC_USER)
            .remove(K_SYNC_PASS)
            .apply()
        secure.save(cfg.user, cfg.pass)
    }

    fun clearTestResult() {
        testResult = ""
    }

    fun testConnection() {
        testResult = "测试中…"
        val cfg = syncConfig
        scope.launch {
            testResult = withContext(Dispatchers.IO) { client.test(cfg) }
        }
    }

    // ---------- sync ----------

    fun syncNow() {
        if (syncing) return
        if (!syncConfig.isConfigured()) {
            lastSyncAt = System.currentTimeMillis()
            lastSyncOk = false
            lastSyncError = "未配置 WebDAV"
            persistSyncMeta()
            return
        }
        syncing = true
        val cfg = syncConfig
        val localJson = docJson()
        val localRev = revision
        val lastSynced = prefs.getLong(K_LAST_SYNCED_REV, 0L)
        val lastRemote = prefs.getLong(K_LAST_REMOTE_REV, -1L)
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { performSync(cfg, localJson, localRev, lastSynced, lastRemote) }
            }
            syncing = false
            outcome.onSuccess { applyOutcome(it) }
            outcome.onFailure { recordFailure(it.message ?: it.javaClass.simpleName) }
        }
    }

    private fun performSync(
        cfg: SyncConfig,
        localJson: String,
        localRev: Long,
        lastSynced: Long,
        lastRemote: Long,
    ): SyncOutcome {
        client.ensureDir(cfg)
        return when (val remote = client.fetch(cfg)) {
            is RemoteFetch.Missing -> {
                client.put(cfg, localJson, ifNoneMatch = true)
                SyncOutcome.Pushed(localRev)
            }
            is RemoteFetch.Found -> {
                val doc = parseDoc(remote.json)
                val remoteChanged = lastRemote < 0L || doc.revision != lastRemote
                val localChanged = localRev != lastSynced
                when {
                    !remoteChanged && !localChanged -> SyncOutcome.NoChange(localRev, doc.revision)
                    remoteChanged && !localChanged -> SyncOutcome.Pulled(doc.items, doc.revision)
                    !remoteChanged && localChanged -> {
                        client.put(cfg, localJson, ifMatch = remote.etag)
                        SyncOutcome.Pushed(localRev)
                    }
                    else -> SyncOutcome.Conflict(doc.items, doc.revision)
                }
            }
        }
    }

    private fun applyOutcome(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.NoChange -> markSuccess(outcome.localRevision, outcome.remoteRevision)
            is SyncOutcome.Pushed -> markSuccess(outcome.revision, outcome.revision)
            is SyncOutcome.Pulled -> {
                items = outcome.items
                revision = outcome.remoteRevision
                persistItems()
                page = 0
                markSuccess(outcome.remoteRevision, outcome.remoteRevision)
            }
            is SyncOutcome.Conflict -> {
                conflict = SyncConflict(outcome.items, outcome.remoteRevision)
                lastSyncError = "存在同步冲突"
                persistSyncMeta()
            }
        }
    }

    fun cancelConflict() {
        conflict = null
        lastSyncError = null
        persistSyncMeta()
    }

    fun resolveConflictKeepLocal() {
        conflict ?: return
        conflict = null
        if (!syncConfig.isConfigured()) {
            recordFailure("未配置 WebDAV")
            return
        }
        syncing = true
        val cfg = syncConfig
        val json = docJson()
        val rev = revision
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    client.ensureDir(cfg)
                    client.put(cfg, json)
                    rev
                }
            }
            syncing = false
            result.onSuccess { markSuccess(it, it) }
                .onFailure { recordFailure(it.message ?: "写入远端失败") }
        }
    }

    fun resolveConflictKeepRemote() {
        val current = conflict ?: return
        conflict = null
        items = current.remoteItems
        revision = current.remoteRevision
        persistItems()
        page = 0
        markSuccess(current.remoteRevision, current.remoteRevision)
    }

    private fun parseDoc(json: String): RemoteDoc {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw SyncException("远端内容不是有效 JSON")
        }
        val arr = obj.optJSONArray("items") ?: JSONArray()
        val list = (0 until arr.length()).mapNotNull { i ->
            runCatching { itemFromJson(arr.getJSONObject(i)) }.getOrNull()
        }
        return RemoteDoc(obj.optLong("revision", 0L), list)
    }

    private fun markSuccess(localRev: Long, remoteRev: Long) {
        lastSyncAt = System.currentTimeMillis()
        lastSyncOk = true
        lastSyncError = null
        prefs.edit()
            .putLong(K_LAST_SYNC, lastSyncAt ?: 0L)
            .putBoolean(K_LAST_OK, true)
            .remove(K_LAST_ERROR)
            .putLong(K_LAST_SYNCED_REV, localRev)
            .putLong(K_LAST_REMOTE_REV, remoteRev)
            .apply()
    }

    private fun recordFailure(message: String) {
        lastSyncAt = System.currentTimeMillis()
        lastSyncOk = false
        lastSyncError = message
        persistSyncMeta()
    }

    private fun persistSyncMeta() {
        val editor = prefs.edit()
            .putLong(K_LAST_SYNC, lastSyncAt ?: 0L)
            .putBoolean(K_LAST_OK, lastSyncOk ?: false)
        if (lastSyncError != null) editor.putString(K_LAST_ERROR, lastSyncError) else editor.remove(K_LAST_ERROR)
        editor.apply()
    }

    private data class RemoteDoc(val revision: Long, val items: List<NoteItem>)

    private sealed interface SyncOutcome {
        data class NoChange(val localRevision: Long, val remoteRevision: Long) : SyncOutcome
        data class Pushed(val revision: Long) : SyncOutcome
        data class Pulled(val items: List<NoteItem>, val remoteRevision: Long) : SyncOutcome
        data class Conflict(val items: List<NoteItem>, val remoteRevision: Long) : SyncOutcome
    }
}
