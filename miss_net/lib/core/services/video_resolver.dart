import 'dart:async';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

class VideoStreamInfo {
  final String streamUrl;
  final Map<String, String> headers;

  VideoStreamInfo({required this.streamUrl, required this.headers});
}

class VideoResolver {
  HeadlessInAppWebView? _headlessWebView;
  Completer<VideoStreamInfo>? _completer;

  Future<VideoStreamInfo> resolveStreamUrl(String sourceUrl) async {
    _completer = Completer<VideoStreamInfo>();
    
    // Dispose previous instance if any
    if (_headlessWebView != null) {
      await _headlessWebView?.dispose();
    }

    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(sourceUrl)),
      initialSettings: InAppWebViewSettings(
        isInspectable: true,
        mediaPlaybackRequiresUserGesture: false,
        useShouldInterceptRequest: true, // Important for interception
      ),
      onWebViewCreated: (controller) {
        print("Resolver: WebView Created");
      },
      onLoadStop: (controller, url) async {
         print("Resolver: Page Loaded: $url");
         // Sometimes we might need to inject JS to trigger play if auto-play is blocked
         // await controller.evaluateJavascript(source: "document.querySelector('video').play();");
      },
      shouldInterceptRequest: (controller, request) async {
        final url = request.url.toString();
        
        // Check for m3u8
        if (url.contains('.m3u8')) {
          print("Resolver: Found m3u8 -> $url");
          
          if (!_completer!.isCompleted) {
            _completer!.complete(VideoStreamInfo(
              streamUrl: url,
              headers: request.headers ?? {},
            ));
            
            // Stop loading to save resources? 
            // controller.stopLoading(); 
            // Better to dispose later
          }
        }
        return null; // Continue request normally
      },
      onConsoleMessage: (controller, consoleMessage) {
        print("Resolver Console: ${consoleMessage.message}");
      },
    );

    await _headlessWebView?.run();
    
    // Timeout safety
    return _completer!.future.timeout(const Duration(seconds: 30), onTimeout: () {
      _headlessWebView?.dispose();
      throw TimeoutException("Failed to resolve stream URL within 30 seconds");
    });
  }

  void dispose() {
    _headlessWebView?.dispose();
    _headlessWebView = null;
  }
}
