import 'package:flutter/foundation.dart';

class ImageProxy {
  /// Proxy and optimize images using wsrv.nl
  /// This helps with CORS on Web and anti-hotlinking on Mobile.
  static String getUrl(String? url) {
    if (url == null || url.isEmpty) return "";
    
    // On Mobile, we can use Referer headers, so we don't need a proxy.
    // Direct connection is much faster.
    if (!kIsWeb) return url;
    
    // If it's already a proxied URL, return as is
    if (url.contains("wsrv.nl")) return url;
    
    // wsrv.nl is highly reliable and provides free optimization
    return "https://wsrv.nl/?url=${Uri.encodeComponent(url)}&w=400&output=webp&q=80";
  }
}
