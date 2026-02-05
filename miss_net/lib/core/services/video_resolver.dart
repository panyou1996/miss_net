import 'dart:async';
import 'dart:io';
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

  static final Map<String, _CacheEntry> _cache = {};

  Future<VideoStreamInfo> resolveStreamUrl(String sourceUrl) async {
    // 0. If it's already an m3u8 (e.g. from 51cg), return immediately
    if (sourceUrl.toLowerCase().contains('.m3u8')) {
      return VideoStreamInfo(
        streamUrl: sourceUrl,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Referer': sourceUrl.contains('51cg') ? 'https://51cg1.com/' : 'https://missav.ws/',
        },
      );
    }

    // 1. Check Cache
    if (_cache.containsKey(sourceUrl)) {
      final entry = _cache[sourceUrl]!;
      if (!entry.isExpired) return entry.info;
      _cache.remove(sourceUrl);
    }

    VideoStreamInfo info;
    try {
      if (kIsWeb) {
        info = await _resolveWeb(sourceUrl);
      } else if (Platform.isLinux) {
        info = await _resolveLinux(sourceUrl);
      } else if (Platform.isWindows || Platform.isMacOS) {
        info = await _resolveDesktop(sourceUrl);
      } else {
        info = await _resolveMobile(sourceUrl);
      }
    } catch (e) {
      debugPrint("Resolution attempt failed: $e. Trying fallback...");
      info = await _resolveWeb(sourceUrl); // Global fallback
    }

    _cache[sourceUrl] = _CacheEntry(info, DateTime.now().add(const Duration(minutes: 15)));
    return info;
  }

  // --- Linux Implementation (Pure HTTP with Multiple Patterns) ---
  Future<VideoStreamInfo> _resolveLinux(String sourceUrl) async {
    final headers = {
      'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Referer': 'https://missav.ws/',
    };

    try {
      final response = await http.get(Uri.parse(sourceUrl), headers: headers);
      if (response.statusCode != 200) throw Exception("Status ${response.statusCode}");

      final html = response.body;
      
      // Pattern 1: Direct URL in HTML
      final m3u8Regex = RegExp(r'''https?://[^"'\s]+\.m3u8[^"'\s]*''');
      final match = m3u8Regex.firstMatch(html);
      if (match != null) {
        return VideoStreamInfo(streamUrl: match.group(0)!, headers: headers);
      }

      // Pattern 2: Packed JS (Common in MissAV)
      if (html.contains('eval(function(p,a,c,k,e,d)')) {
        final evalRegex = RegExp(r"eval\(function\(p,a,c,k,e,d\).*?\.split\('\|'\)\)\)", dotAll: true);
        final evalMatch = evalRegex.firstMatch(html);
        if (evalMatch != null) {
          final unpacked = _unpack(evalMatch.group(0)!);
          final mMatch = m3u8Regex.firstMatch(unpacked);
          if (mMatch != null) {
            return VideoStreamInfo(streamUrl: mMatch.group(0)!, headers: headers);
          }
        }
      }
      
      throw Exception("No m3u8 pattern found in source");
    } catch (e) {
      rethrow;
    }
  }

  // --- Desktop WebView Implementation ---
  Future<VideoStreamInfo> _resolveDesktop(String sourceUrl) async {
    _completer = Completer<VideoStreamInfo>();
    if (_headlessWebView != null) await _headlessWebView?.dispose();

    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(sourceUrl)),
      onLoadStop: (controller, url) async {
        final html = await controller.evaluateJavascript(source: "document.documentElement.innerHTML");
        if (html != null) {
          final m3u8Regex = RegExp(r'''https?://[^"'\s]+\.m3u8[^"'\s]*''');
          final match = m3u8Regex.firstMatch(html.toString());
          if (match != null && !_completer!.isCompleted) {
            _completer!.complete(VideoStreamInfo(
              streamUrl: match.group(0)!,
              headers: {
                'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Referer': 'https://missav.ws/',
              },
            ));
          }
        }
      },
    );

    await _headlessWebView?.run();
    return await _completer!.future.timeout(const Duration(seconds: 20));
  }

  // --- Mobile Implementation (Android/iOS) ---
  Future<VideoStreamInfo> _resolveMobile(String sourceUrl) async {
    _completer = Completer<VideoStreamInfo>();
    if (_headlessWebView != null) await _headlessWebView?.dispose();

    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(sourceUrl)),
      initialSettings: InAppWebViewSettings(useShouldInterceptRequest: true),
      shouldInterceptRequest: (controller, request) async {
        final url = request.url.toString();
        if (url.contains('.m3u8') && !_completer!.isCompleted) {
          _completer!.complete(VideoStreamInfo(streamUrl: url, headers: request.headers ?? {}));
        }
        return null;
      },
    );

    await _headlessWebView?.run();
    return await _completer!.future.timeout(const Duration(seconds: 15));
  }

  // --- Web Proxy Fallback ---
  Future<VideoStreamInfo> _resolveWeb(String sourceUrl) async {
    final proxyUrl = "https://api.allorigins.win/raw?url=${Uri.encodeComponent(sourceUrl)}";
    final response = await http.get(Uri.parse(proxyUrl));
    if (response.statusCode != 200) throw Exception("Proxy error");

    final html = response.body;
    final evalRegex = RegExp(r"eval\(function\(p,a,c,k,e,d\).*?\.split\('\|'\)\)\)", dotAll: true);
    final match = evalRegex.firstMatch(html);

    if (match != null) {
      final unpacked = _unpack(match.group(0)!);
      final m3u8Regex = RegExp(r"https?://[^']+\.m3u8");
      final mMatch = m3u8Regex.firstMatch(unpacked);
      if (mMatch != null) return VideoStreamInfo(streamUrl: mMatch.group(0)!, headers: {});
    }
    throw Exception("No m3u8 found in proxy source");
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