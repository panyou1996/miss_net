package com.panyou.missnet.data.media

object SourceRequestHeaders {
    const val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun forUrl(url: String?, includeUserAgent: Boolean = true): Map<String, String> {
        val referer = refererFor(url)
        val headers = linkedMapOf(
            "Referer" to referer,
            "Origin" to originFor(url)
        )
        if (includeUserAgent) {
            headers["User-Agent"] = browserUserAgent
        }
        return headers
    }

    fun refererFor(url: String?): String {
        val normalized = url.orEmpty().lowercase()
        return when {
            normalized.contains("51cg1.com") || normalized.contains("51cg") -> "https://51cg1.com/"
            else -> "https://missav.ws/"
        }
    }

    fun originFor(url: String?): String = refererFor(url).removeSuffix("/")
}
