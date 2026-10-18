package com.elinkmemo.app

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val remotePath: String = "ElinkMemo/notes.json",
) {
    val base: String get() = url.trim().trimEnd('/')
    val fullUrl: String get() = base + "/" + remotePath.trim().trimStart('/')
    val dirParts: List<String>
        get() {
            val parts = remotePath.trim().trim('/').split('/').toMutableList()
            if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            return parts
        }

    fun isConfigured(): Boolean = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
}

sealed class RemoteFetch {
    data class Found(val json: String, val etag: String?) : RemoteFetch()
    data object Missing : RemoteFetch()
}

class SyncException(message: String) : Exception(message)

class WebDavClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun auth(config: SyncConfig): String =
        Credentials.basic(config.user, config.pass, Charsets.UTF_8)

    fun fetch(config: SyncConfig): RemoteFetch {
        val request = Request.Builder()
            .url(config.fullUrl)
            .header("Authorization", auth(config))
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            return when (resp.code) {
                200 -> RemoteFetch.Found(resp.body?.string().orEmpty(), resp.header("ETag"))
                404 -> RemoteFetch.Missing
                401, 403 -> throw SyncException("认证失败（HTTP " + resp.code + "）")
                else -> throw SyncException("读取远端失败（HTTP " + resp.code + "）")
            }
        }
    }

    fun ensureDir(config: SyncConfig) {
        var path = ""
        for (part in config.dirParts) {
            path += "/" + part
            val request = Request.Builder()
                .url(config.base + path + "/")
                .header("Authorization", auth(config))
                .method("MKCOL", null)
                .build()
            http.newCall(request).execute().use { resp ->
                if (resp.code !in intArrayOf(200, 201, 301, 405)) {
                    throw SyncException("创建远端目录失败（HTTP " + resp.code + "）")
                }
            }
        }
    }

    fun put(config: SyncConfig, json: String, ifNoneMatch: Boolean = false, ifMatch: String? = null) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder()
            .url(config.fullUrl)
            .header("Authorization", auth(config))
            .put(body)
        if (ifNoneMatch) builder.header("If-None-Match", "*")
        if (!ifMatch.isNullOrBlank()) builder.header("If-Match", ifMatch)
        http.newCall(builder.build()).execute().use { resp ->
            if (resp.code !in 200..299) throw SyncException("写入远端失败（HTTP " + resp.code + "）")
        }
    }

    fun test(config: SyncConfig): String {
        if (config.url.isBlank()) return "失败：地址为空"
        if (!config.url.startsWith("http://") && !config.url.startsWith("https://")) {
            return "失败：地址需以 http:// 或 https:// 开头"
        }
        return try {
            val request = Request.Builder()
                .url(config.fullUrl)
                .header("Authorization", auth(config))
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                when (resp.code) {
                    200, 404 -> "测试成功"
                    401, 403 -> "失败：认证失败（HTTP " + resp.code + "）"
                    else -> "失败：HTTP " + resp.code
                }
            }
        } catch (e: Exception) {
            "失败：" + (e.message ?: e.javaClass.simpleName)
        }
    }
}
