package com.panyou.missnet.data.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

class VideoResolver @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context
) {
    suspend fun resolve(sourceUrl: String): String {
        Log.d("VideoResolver", "Resolving: $sourceUrl")
        if (sourceUrl.contains(".m3u8")) return sourceUrl

        // 1. High-speed HTTP + Regex
        try {
            val response = httpClient.get(sourceUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Referer", "https://missav.ws/")
            }
            val html = response.bodyAsText()

            val m3u8Regex = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
            val match = m3u8Regex.find(html)
            if (match != null) {
                Log.d("VideoResolver", "Found m3u8 via HTTP: ${match.value}")
                return match.value
            }
        } catch (e: Exception) {
            Log.e("VideoResolver", "HTTP Resolve failed, trying Sniffer")
        }

        // 2. Heavy-duty WebView Sniffer (Bypass Cloudflare)
        val sniffedUrl = WebViewSniffer(context).sniffM3u8(sourceUrl)
        if (sniffedUrl != null) {
            Log.d("VideoResolver", "Sniffer success: $sniffedUrl")
            return sniffedUrl
        }

        return sourceUrl
    }
}