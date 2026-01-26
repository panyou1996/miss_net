import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:http/http.dart' as http;

class VideoStreamInfo {
  final String streamUrl;
  final Map<String, String> headers;

  VideoStreamInfo({required this.streamUrl, required this.headers});
}

class _CacheEntry {
  final VideoStreamInfo info;
  final DateTime expiry;

  _CacheEntry(this.info, this.expiry);

  bool get isExpired => DateTime.now().isAfter(expiry);
}

class VideoResolver {
  HeadlessInAppWebView? _headlessWebView;
  Completer<VideoStreamInfo>? _completer;

  // Cache: sourceUrl -> Entry
  static final Map<String, _CacheEntry> _cache = {};

  Future<VideoStreamInfo> resolveStreamUrl(String sourceUrl) async {
    // Check Cache first
    if (_cache.containsKey(sourceUrl)) {
      final entry = _cache[sourceUrl]!;
      if (!entry.isExpired) {
        debugPrint("Resolver: Cache Hit for $sourceUrl");
        return entry.info;
      } else {
        _cache.remove(sourceUrl);
      }
    }

    VideoStreamInfo info;
    if (kIsWeb) {
      info = await _resolveWeb(sourceUrl);
    } else {
      info = await _resolveMobile(sourceUrl);
    }

    // Save to cache (valid for 15 minutes)
    _cache[sourceUrl] = _CacheEntry(
      info, 
      DateTime.now().add(const Duration(minutes: 15))
    );
    
    return info;
  }

  // --- Mobile Implementation (Headless WebView) ---
  Future<VideoStreamInfo> _resolveMobile(String sourceUrl) async {
    _completer = Completer<VideoStreamInfo>();
    
    if (_headlessWebView != null) {
      await _headlessWebView?.dispose();
    }

    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(sourceUrl)),
      initialSettings: InAppWebViewSettings(
        isInspectable: true,
        mediaPlaybackRequiresUserGesture: false,
        useShouldInterceptRequest: true,
      ),
      shouldInterceptRequest: (controller, request) async {
        final url = request.url.toString();
        if (url.contains('.m3u8')) {
          if (!_completer!.isCompleted) {
            _completer!.complete(VideoStreamInfo(
              streamUrl: url,
              headers: request.headers ?? {},
            ));
          }
        }
        return null;
      },
    );

    await _headlessWebView?.run();
    
    try {
      return await _completer!.future.timeout(const Duration(seconds: 15));
    } catch (e) {
      await _headlessWebView?.dispose();
      _headlessWebView = null;
      throw TimeoutException("Failed to resolve stream URL via Headless WebView");
    }
  }

  // --- Web Implementation ---
  Future<VideoStreamInfo> _resolveWeb(String sourceUrl) async {
    try {
      final proxyUrl = "https://api.allorigins.win/raw?url=${Uri.encodeComponent(sourceUrl)}";
      final response = await http.get(Uri.parse(proxyUrl));
      if (response.statusCode != 200) throw Exception("Failed to fetch page source");

      final html = response.body;
      final regExp = RegExp(r"eval\(function\(p,a,c,k,e,d\).*?\.split\('\|'\)\)\)", dotAll: true);
      final match = regExp.firstMatch(html);

      if (match != null) {
        final unpacked = _unpack(match.group(0)!);
        final m3u8Regex = RegExp(r"https?://[^']+\.m3u8");
        final m3u8Match = m3u8Regex.firstMatch(unpacked);

        if (m3u8Match != null) {
          return VideoStreamInfo(streamUrl: m3u8Match.group(0)!, headers: {});
        }
      }
      throw Exception("No m3u8 found in unpacked code");
    } catch (e) {
      throw Exception("Web Resolution Failed: $e");
    }
  }

  String _unpack(String packed) {
    try {
      final argsRegex = RegExp(r"\}\('(.*)',(\d+),(\d+),'(.*)'\.split\('\|'\)");
      final match = argsRegex.firstMatch(packed);
      if (match == null) return "";

      String payload = match.group(1)!;
      List<String> keywords = match.group(4)!.split('|');

      for (int i = 0; i < keywords.length; i++) {
        if (keywords[i].isEmpty) continue;
        String key = i.toRadixString(36);
        payload = payload.replaceAllMapped(RegExp(r'\b' + key + r'\b'), (match) => keywords[i]);
      }
      return payload;
    } catch (e) {
      return "";
    }
  }

  void dispose() {
    _headlessWebView?.dispose();
    _headlessWebView = null;
  }
}