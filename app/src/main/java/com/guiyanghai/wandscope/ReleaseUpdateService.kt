package com.guiyanghai.wandscope

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReleaseUpdateService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).build()

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPOSITORY
        val url = "https://github.com/$repo/releases/latest/download/update.json"
        val manifest = fetchOptional(url, 64 * 1024) ?: return@withContext UpdateCheckResult.NotPublished
        val json = JSONObject(manifest.toString(Charsets.UTF_8))
        val expected = setOf("versionCode", "versionName", "apkUrl", "sha256", "releaseUrl")
        if (json.keys().asSequence().toSet() != expected) throw IOException("update.json 字段不符合约定")
        val info = UpdateInfo(
            json.getInt("versionCode"),
            json.getString("versionName"),
            json.getString("apkUrl"),
            json.getString("sha256").lowercase(Locale.US),
            json.getString("releaseUrl"),
        )
        ReleaseUrlPolicy.requireAllowed(info.apkUrl, repo)
        ReleaseUrlPolicy.requireAllowed(info.releaseUrl, repo)
        require(info.sha256.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 格式错误" }
        if (info.versionCode > BuildConfig.VERSION_CODE) {
            UpdateCheckResult.Available(info)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    suspend fun downloadAndOpen(info: UpdateInfo, onProgress: (Int?) -> Unit) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "WandScope-${info.versionName}.apk")
        runCatching {
            downloadToFile(info.apkUrl, apk, 100 * 1024 * 1024, onProgress)
            onProgress(92)
            val actual = apk.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            if (!actual.equals(info.sha256, ignoreCase = true)) throw IOException("更新包 SHA-256 校验失败")
            onProgress(100)
        }.onFailure {
            apk.delete()
        }.getOrThrow()
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            throw IOException("请允许 WandScope 安装未知应用，然后再次点击下载更新")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun downloadToFile(initial: String, target: File, limit: Int, onProgress: (Int?) -> Unit) {
        var current = initial
        repeat(6) {
            ReleaseUrlPolicy.requireAllowed(current, BuildConfig.GITHUB_REPOSITORY)
            client.newCall(Request.Builder().url(current).header("Accept", "application/octet-stream").build()).execute().use { response ->
                if (response.code in 300..399) {
                    current = response.header("Location") ?: throw IOException("更新地址重定向缺少 Location")
                    return@repeat
                }
                if (!response.isSuccessful) throw IOException("更新服务器返回 HTTP ${response.code}")
                val body = response.body ?: throw IOException("更新响应为空")
                val total = body.contentLength()
                if (total > limit) throw IOException("更新文件超过大小限制")
                var lastReported = Int.MIN_VALUE
                fun reportProgress(bytesRead: Long) {
                    val progress = UpdateProgressPolicy.percentage(bytesRead, total)
                    val marker = progress ?: -1
                    if (marker != lastReported) {
                        lastReported = marker
                        onProgress(progress)
                    }
                }
                reportProgress(0L)
                var received = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target, false).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > limit) throw IOException("更新文件超过大小限制")
                            output.write(buffer, 0, count)
                            reportProgress(received)
                        }
                    }
                }
                return
            }
        }
        throw IOException("更新地址重定向次数过多")
    }

    private fun fetch(initial: String, limit: Int): ByteArray {
        return requireNotNull(fetchInternal(initial, limit, missingManifestIsEmpty = false))
    }

    private fun fetchOptional(initial: String, limit: Int): ByteArray? {
        return fetchInternal(initial, limit, missingManifestIsEmpty = true)
    }

    private fun fetchInternal(initial: String, limit: Int, missingManifestIsEmpty: Boolean): ByteArray? {
        var current = initial
        repeat(6) {
            ReleaseUrlPolicy.requireAllowed(current, BuildConfig.GITHUB_REPOSITORY)
            client.newCall(Request.Builder().url(current).header("Accept", "application/octet-stream").build()).execute().use { response ->
                if (response.code in 300..399) {
                    current = response.header("Location") ?: throw IOException("更新地址重定向缺少 Location")
                    return@repeat
                }
                if (missingManifestIsEmpty && UpdateHttpPolicy.isMissingManifest(response.code)) return null
                if (!response.isSuccessful) throw IOException("更新服务器返回 HTTP ${response.code}")
                val body = response.body ?: throw IOException("更新响应为空")
                if (body.contentLength() > limit) throw IOException("更新文件超过大小限制")
                val bytes = body.bytes()
                if (bytes.size > limit) throw IOException("更新文件超过大小限制")
                return bytes
            }
        }
        throw IOException("更新地址重定向次数过多")
    }

}
