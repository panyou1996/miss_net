import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:transparent_image/transparent_image.dart';
import '../../core/utils/image_proxy.dart';
import '../../domain/entities/video.dart';
import 'video_skeleton.dart';

class VideoCard extends StatelessWidget {
  final Video video;
  final VoidCallback onTap;
  final String? heroTag;

  const VideoCard({
    super.key, 
    required this.video, 
    required this.onTap,
    this.heroTag,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16.0),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.4 : 0.1),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: GestureDetector(
        onTap: onTap,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16.0), 
          child: Stack(
            children: [
              Positioned.fill(
                child: Hero(
                  tag: heroTag ?? video.id,
                  child: video.coverUrl != null
                      ? CachedNetworkImage(
                          imageUrl: ImageProxy.getUrl(video.coverUrl!),
                          httpHeaders: const {'Referer': 'https://missav.ws/'},
                          memCacheHeight: 400,
                          fit: BoxFit.cover,
                          placeholder: (context, url) => const VideoCardSkeleton(),
                          errorWidget: (context, url, error) => Container(
                            color: Colors.grey[900],
                            child: const Icon(Icons.broken_image, color: Colors.white12),
                          ),
                        )
                      : Image.memory(kTransparentImage),
                ),
              ),
              // Improved Gradient Overlay
              Positioned.fill(
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.9), 
                        Colors.black.withValues(alpha: 0.4),
                        Colors.transparent,
                        Colors.black.withValues(alpha: 0.1),
                      ],
                      stops: const [0.0, 0.3, 0.6, 1.0], 
                    ),
                  ),
                ),
              ),
              // Progress Bar
              if (video.progress > 0)
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    height: 3,
                    color: Colors.white.withValues(alpha: 0.1),
                    child: FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: video.progress,
                      child: Container(
                        decoration: BoxDecoration(
                          color: Colors.red,
                          boxShadow: [
                            BoxShadow(color: Colors.red.withValues(alpha: 0.5), blurRadius: 4, spreadRadius: 1)
                          ]
                        ),
                      ),
                    ),
                  ),
                ),
              // Title
              Positioned(
                bottom: 10,
                left: 10,
                right: 10,
                child: Text(
                  video.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                    height: 1.2,
                    letterSpacing: 0.2,
                  ),
                ),
              ),
              // Duration Badge
              if (video.duration != null && video.duration!.isNotEmpty)
                Positioned(
                  bottom: 30,
                  right: 5,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.7),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      video.duration!,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              // Subtitle Badge
              if (_hasSubtitles(video))
                Positioned(
                  top: 5,
                  left: 5,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.red,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text(
                      "SUB",
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  bool _hasSubtitles(Video video) {
    final title = video.title.toLowerCase();
    final categories = video.categories?.map((e) => e.toLowerCase()).toList() ?? [];
    return title.contains("中文字幕") || 
           title.contains("中文") || 
           categories.contains("subtitled");
  }
}