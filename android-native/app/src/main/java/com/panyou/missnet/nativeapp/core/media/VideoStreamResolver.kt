package com.panyou.missnet.nativeapp.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.panyou.missnet.nativeapp.BuildConfig
import com.panyou.missnet.nativeapp.core.model.StreamInfo
import com.panyou.missnet.nativeapp.core.util.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoStreamResolver(
    private val context: Context,
    private val mediaHeaderStore: MediaHeaderStore,
    private val httpClient: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun resolve(sourceUrl: String): StreamInfo {
        if (sourceUrl.contains(".m3u8", ignoreCase = true) || sourceUrl.contains(".mp4", ignoreCase = true)) {
            return directStreamInfo(sourceUrl)
        }

        cache[sourceUrl]?.takeIf { !it.isExpired() }?.let { return it.info }

        val resolved = runCatching { resolveWithHttp(sourceUrl) }
            .recoverCatching { resolveWithWebView(sourceUrl) }
            .getOrElse { resolveWithWebView(sourceUrl) }

        cache[sourceUrl] = CacheEntry(resolved, System.currentTimeMillis() + 15 * 60_000L)
        mediaHeaderStore.update(resolved.headers)
        return resolved
    }

    private fun directStreamInfo(sourceUrl: String): StreamInfo {
        val referer = when {
            sourceUrl.contains("51cg", ignoreCase = true) -> "https://51cg1.com/"
            sourceUrl.contains("missav", ignoreCase = true) -> BuildConfig.DEFAULT_SOURCE_REFERER
            else -> "${Uri.parse(sourceUrl).scheme}://${Uri.parse(sourceUrl).host.orEmpty()}/"
        }
        return StreamInfo(
            streamUrl = sourceUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to referer,
            ),
            mimeType = inferMimeType(sourceUrl),
        )
    }

    private suspend fun resolveWithHttp(sourceUrl: String): StreamInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(sourceUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Referer", BuildConfig.DEFAULT_SOURCE_REFERER)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Resolver HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.contains("Just a moment", ignoreCase = true)) {
                error("Cloudflare challenge")
            }
            parseFirstStreamUrl(body)?.let { streamUrl ->
                StreamInfo(
                    streamUrl = streamUrl,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_USER_AGENT,
                        "Referer" to BuildConfig.DEFAULT_SOURCE_REFERER,
                    ),
                    mimeType = inferMimeType(streamUrl),
                )
            } ?: error("No stream URL found")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveWithWebView(sourceUrl: String): StreamInfo = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = DEFAULT_USER_AGENT
            }

            fun finish(result: Result<StreamInfo>) {
                if (!continuation.isActive) return
                webView.stopLoading()
                webView.destroy()
                result.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
            }

            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                finish(Result.failure(IllegalStateException("Timed out resolving stream")))
            }
            timeoutHandler.postDelayed(timeoutRunnable, 25_000L)

            continuation.invokeOnCancellation {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                webView.stopLoading()
                webView.destroy()
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        val headers = request?.requestHeaders.orEmpty().toMutableMap().apply {
                            putIfAbsent("User-Agent", DEFAULT_USER_AGENT)
                            putIfAbsent("Referer", BuildConfig.DEFAULT_SOURCE_REFERER)
                            cookieManager.getCookie(url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                        }
                        finish(Result.success(StreamInfo(url, headers, inferMimeType(url))))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { html ->
                        val decoded = runCatching { appJson.decodeFromString<String>(html) }.getOrDefault(html)
                        parseFirstStreamUrl(decoded)?.let { streamUrl ->
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            val headers = mutableMapOf(
                                "User-Agent" to DEFAULT_USER_AGENT,
                                "Referer" to BuildConfig.DEFAULT_SOURCE_REFERER,
                            )
                            cookieManager.getCookie(streamUrl)?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
                            finish(Result.success(StreamInfo(streamUrl, headers, inferMimeType(streamUrl))))
                        }
                    }
                }
            }

            webView.loadUrl(
                sourceUrl,
                mapOf(
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Referer" to BuildConfig.DEFAULT_SOURCE_REFERER,
                ),
            )
        }
    }

    private fun parseFirstStreamUrl(html: String): String? {
        val directRegex = Regex("""https?://[^"'\\s]+\\.(m3u8|mp4)[^"'\\s]*""", RegexOption.IGNORE_CASE)
        directRegex.find(html)?.value?.let { return it }
        val partialRegex = Regex("""//[^"'\\s]+\\.m3u8[^"'\\s]*""", RegexOption.IGNORE_CASE)
        partialRegex.find(html)?.value?.let { return "https:$it" }
        return null
    }

    private fun inferMimeType(url: String): String? = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mp4", ignoreCase = true) -> "video/mp4"
        else -> null
    }

    private data class CacheEntry(
        val info: StreamInfo,
        val expiresAtEpochMs: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtEpochMs
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
