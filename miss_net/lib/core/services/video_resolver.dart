import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:http/http.dart' as http;

class VideoStreamInfo {
  final String streamUrl;
  final Map<String, String> headers;

  VideoStreamInfo({required this.streamUrl, required this.headers});
}

class VideoResolver {
  HeadlessInAppWebView? _headlessWebView;
  Completer<VideoStreamInfo>? _completer;

  Future<VideoStreamInfo> resolveStreamUrl(String sourceUrl) async {
    if (kIsWeb) {
      return _resolveWeb(sourceUrl);
    } else {
      return _resolveMobile(sourceUrl);
    }
  }

  // --- Mobile Implementation (Headless WebView) ---
  Future<VideoStreamInfo> _resolveMobile(String sourceUrl) async {
    _completer = Completer<VideoStreamInfo>();
    
    // Dispose previous instance
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
    
    return _completer!.future.timeout(const Duration(seconds: 15), onTimeout: () {
      _headlessWebView?.dispose();
      throw TimeoutException("Failed to resolve stream URL via Headless WebView");
    });
  }

  // --- Web Implementation (Static Parse + Proxy) ---
  Future<VideoStreamInfo> _resolveWeb(String sourceUrl) async {
    try {
      // 1. Fetch HTML via CORS Proxy
      final proxyUrl = "https://api.allorigins.win/raw?url=${Uri.encodeComponent(sourceUrl)}";
      final response = await http.get(Uri.parse(proxyUrl));
      
      if (response.statusCode != 200) {
        throw Exception("Failed to fetch page source");
      }

      final html = response.body;

      // 2. Look for the packed JS function
      // Pattern: eval(function(p,a,c,k,e,d)...
      // We use dotAll: true to match across newlines
      final regExp = RegExp(r"eval\(function\(p,a,c,k,e,d\).*?\.split\('\|'\)\)\)", dotAll: true);
      final match = regExp.firstMatch(html);

      if (match != null) {
        final packed = match.group(0)!;
        print("Found packed JS: ${packed.substring(0, 50)}...");
        final unpacked = _unpack(packed);
        print("Unpacked JS: ${unpacked.substring(0, 100)}...");
        
        // 3. Extract .m3u8 from unpacked code
        // Look for: source='https://...' or https://...m3u8
        final m3u8Regex = RegExp(r"https?://[^']+\.m3u8");
        final m3u8Match = m3u8Regex.firstMatch(unpacked);

        if (m3u8Match != null) {
          final m3u8Url = m3u8Match.group(0)!;
          print("Found m3u8: $m3u8Url");
          return VideoStreamInfo(
            streamUrl: m3u8Url, 
            headers: {} // Usually direct m3u8 links don't need headers if extracted this way
          );
        }
      }
      
      throw Exception("No m3u8 found in unpacked code");

    } catch (e) {
      throw Exception("Web Resolution Failed: $e");
    }
  }

  // A basic Dean Edwards unpacker port for Dart
  String _unpack(String packed) {
    try {
      // 1. Extract arguments: p, a, c, k, e, d
      // Robust regex to find the payload and keywords
      // return p}('payload',36,count,'keywords'.split('|')
      final argsRegex = RegExp(r"\}\('(.*)',(\d+),(\d+),'(.*)'\.split\('\|'\)");
      final match = argsRegex.firstMatch(packed);
      
      if (match == null) {
        print("Unpack failed: Regex did not match arguments");
        return "";
      }

      String payload = match.group(1)!;
      // int radix = int.parse(match.group(2)!);
      // int count = int.parse(match.group(3)!);
      List<String> keywords = match.group(4)!.split('|');

      // 2. Decode logic: Replace base-N words with keywords
      // The logic in the packed code is: e=function(c){return c.toString(36)}; ... k[c]||c.toString(a)
      // Since 'e' usually encodes to base36, we can just map the tokens.
      
      // Simple Substitution:
      // Loop through keywords. If keyword exists, replace the Base36 key in payload.
      // Note: We must replace longer keys first to avoid partial matches? 
      // Actually the packer usually uses boundaries \b.
      
      String replace(String p, List<String> k) {
        // We need to replicate: p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])
        // e(c) converts index to Base36.
        
        for (int i = 0; i < k.length; i++) {
          if (k[i].isEmpty) continue; // Skip empty keywords (means map to self, usually handled by '||c')
          
          String key = i.toRadixString(36); // The 'e(c)' part
          
          // Replace \bKEY\b with VALUE
          // We use a custom replace to avoid messing up overlapping words
          p = p.replaceAllMapped(RegExp(r'\b' + key + r'\b'), (match) => k[i]);
        }
        return p;
      }

      return replace(payload, keywords);

    } catch (e) {
      print("Unpack error: $e");
      return "";
    }
  }

  void dispose() {
    _headlessWebView?.dispose();
    _headlessWebView = null;
  }
}
