package com.panyou.missnet.data.util

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WebViewSniffer(private val context: Context) {

    suspend fun sniffM3u8(url: String): String? = suspendCancellableCoroutine { continuation ->
        // Must run on Main Thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    if (url != null && (url.contains(".m3u8") || url.contains("playlist.m3u8"))) {
                        if (!continuation.isCompleted) {
                            continuation.resume(url)
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                }

                // Also check intercepted requests for better coverage
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString()
                    if (url != null && (url.contains(".m3u8"))) {
                         if (!continuation.isCompleted) {
                            continuation.resume(url)
                            view?.post { 
                                view.stopLoading()
                                view.destroy() 
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView.loadUrl(url)
            
            // Timeout safety (15s)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!continuation.isCompleted) {
                    continuation.resume(null) // Failed
                    webView.destroy()
                }
            }, 15000)
        }
    }
}
