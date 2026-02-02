import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:transparent_image/transparent_image.dart';
import '../../core/utils/image_proxy.dart';
import '../../domain/entities/video.dart';
import 'video_skeleton.dart';

class VideoCard extends StatefulWidget {
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
  State<VideoCard> createState() => _VideoCardState();
}

class _VideoCardState extends State<VideoCard> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 100),
    );
    _scale = Tween<double>(begin: 1.0, end: 0.96).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return GestureDetector(
      onTapDown: (_) => _controller.forward(),
      onTapUp: (_) => _controller.reverse(),
      onTapCancel: () => _controller.reverse(),
      onTap: widget.onTap,
      child: ScaleTransition(
        scale: _scale,
        child: Container(
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
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16.0), 
            child: Stack(
              children: [
                Positioned.fill(
                  child: Hero(
                    tag: widget.heroTag ?? widget.video.id,
                    child: widget.video.coverUrl != null
                        ? CachedNetworkImage(
                            imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
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
                if (widget.video.progress > 0)
                  Positioned(
                    bottom: 0,
                    left: 0,
                    right: 0,
                    child: Container(
                      height: 3,
                      color: Colors.white.withValues(alpha: 0.1),
                      child: FractionallySizedBox(
                        alignment: Alignment.centerLeft,
                        widthFactor: widget.video.progress,
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
                Positioned(
                  bottom: 10,
                  left: 10,
                  right: 10,
                  child: Text(
                    widget.video.title,
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
                if (widget.video.duration != null && widget.video.duration!.isNotEmpty)
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
                        widget.video.duration!,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                if (_hasSubtitles(widget.video))
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
