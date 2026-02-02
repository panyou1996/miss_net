import 'dart:async';
import 'dart:io';
import 'package:chewie/chewie.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui'; 
import 'package:cached_network_image/cached_network_image.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import '../../core/services/video_resolver.dart';
import '../../core/services/download_service.dart';
import '../../core/services/cast_service.dart';
import '../../domain/entities/video.dart';
import '../../domain/repositories/video_repository.dart';
import '../../injection_container.dart';
import '../../core/utils/image_proxy.dart';
import 'category/category_detail_page.dart';
import 'player/widgets/video_gesture_wrapper.dart';
import '../widgets/video_card.dart';

class PlayerPage extends StatefulWidget {
  final Video video;
  final String? heroTag;

  const PlayerPage({super.key, required this.video, this.heroTag});

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> with WidgetsBindingObserver {
  final VideoResolver _resolver = sl<VideoResolver>();
  final VideoRepository _repository = sl<VideoRepository>();
  final DownloadService _downloadService = sl<DownloadService>();
  final CastService _castService = sl<CastService>();
  VideoPlayerController? _videoPlayerController;
  ChewieController? _chewieController;
  bool _isLoading = true;
  String? _errorMessage;
  bool _isFavorite = false;
  bool _isPipMode = false;
  bool _isLocked = false;
  Timer? _progressTimer;
  List<Video> _relatedVideos = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _checkFavoriteStatus();
    _loadRelatedVideos();
    if (!kIsWeb) {
      _initializePlayer();
    } else {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _loadRelatedVideos() async {
    final result = await _repository.getRelatedVideos(widget.video);
    result.fold(
      (l) => null,
      (videos) {
        if (mounted) setState(() => _relatedVideos = videos);
      },
    );
  }

  Future<void> _checkFavoriteStatus() async {
    final isFav = await _repository.isFavorite(widget.video.id);
    if (mounted) setState(() => _isFavorite = isFav);
  }

  Future<void> _toggleFavorite() async {
    if (_isFavorite) {
      await _repository.removeFavorite(widget.video.id);
    } else {
      await _repository.saveFavorite(widget.video);
    }
    await _checkFavoriteStatus();
  }

  Future<void> _saveProgress() async {
    if (_videoPlayerController != null && _videoPlayerController!.value.isInitialized) {
      final position = _videoPlayerController!.value.position.inMilliseconds;
      final duration = _videoPlayerController!.value.duration.inMilliseconds;
      await _repository.saveToHistory(widget.video, position, duration);
    }
  }

  void _startProgressTimer() {
    _progressTimer?.cancel();
    _progressTimer = Timer.periodic(const Duration(seconds: 5), (timer) {
      _saveProgress();
    });
  }

  Future<void> _initializePlayer() async {
    try {
      if (widget.video.isOffline && widget.video.filePath != null) {
        final file = File(widget.video.filePath!);
        if (await file.exists()) {
          _videoPlayerController = VideoPlayerController.file(file);
        } else {
          throw Exception("Offline file not found");
        }
      } else {
        final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
        _videoPlayerController = VideoPlayerController.networkUrl(
          Uri.parse(streamInfo.streamUrl),
          httpHeaders: streamInfo.headers,
        );
      }

      await _videoPlayerController!.initialize();

      // Load progress
      final savedPos = await _repository.getProgress(widget.video.id);
      if (savedPos > 0) {
        await _videoPlayerController!.seekTo(Duration(milliseconds: savedPos));
      }

      _chewieController = ChewieController(
        videoPlayerController: _videoPlayerController!,
        autoPlay: true,
        looping: false,
        aspectRatio: _videoPlayerController!.value.aspectRatio,
        allowFullScreen: true,
        allowMuting: true,
        showControls: !_isLocked,
        materialProgressColors: ChewieProgressColors(
          playedColor: Colors.red,
          handleColor: Colors.red,
          backgroundColor: Colors.grey.withValues(alpha: 0.5),
          bufferedColor: Colors.grey,
        ),
        errorBuilder: (context, errorMessage) {
          return Center(child: Text(errorMessage, style: const TextStyle(color: Colors.white)));
        },
        allowedScreenSleep: false,
        deviceOrientationsAfterFullScreen: [DeviceOrientation.portraitUp],
      );

      _startProgressTimer();

      if (mounted) setState(() => _isLoading = false);
    } catch (e) {
      if (mounted) setState(() { _isLoading = false; _errorMessage = "Failed to load video: $e"; });
    }
  }

  void _toggleLock() {
    setState(() {
      _isLocked = !_isLocked;
      // Update controls visibility
      if (_chewieController != null) {
        final oldController = _chewieController!;
        _chewieController = ChewieController(
          videoPlayerController: oldController.videoPlayerController,
          autoPlay: oldController.autoPlay,
          looping: oldController.looping,
          aspectRatio: oldController.aspectRatio,
          allowFullScreen: oldController.allowFullScreen,
          allowMuting: oldController.allowMuting,
          showControls: !_isLocked,
          materialProgressColors: oldController.materialProgressColors,
          errorBuilder: oldController.errorBuilder,
          allowedScreenSleep: oldController.allowedScreenSleep,
          deviceOrientationsAfterFullScreen: oldController.deviceOrientationsAfterFullScreen,
        );
      }
    });
    HapticFeedback.mediumImpact();
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    _saveProgress(); // Final save
    _castService.stop();
    WidgetsBinding.instance.removeObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.manual, overlays: SystemUiOverlay.values);
    _videoPlayerController?.dispose();
    _chewieController?.dispose();
    _resolver.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      _saveProgress();
    }
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    final isPip = MediaQuery.of(context).size.width < 400 && MediaQuery.of(context).size.height < 400;
    if (isPip != _isPipMode) setState(() => _isPipMode = isPip);
  }

  Future<void> _enterPipMode() async {
    try {
      if (defaultTargetPlatform == TargetPlatform.android) {
        const MethodChannel('flutter.io/pip').invokeMethod('enterPipMode');
      }
    } catch (e) {
      debugPrint("PiP error: $e");
    }
  }

  Future<void> _handleDownload() async {
    try {
      final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
      final url = streamInfo.streamUrl;
      
      final filename = "${widget.video.title.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_')}.mp4";
      final id = await _downloadService.downloadVideo(url, filename, headers: streamInfo.headers);
      
      if (id != null) {
        if (mounted) {
          if (id.startsWith("ffmpeg_")) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text("HLS Download started (FFmpeg)"))
            );
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text("Download started"))
            );
          }
        }
      }
    } catch (e) {
      debugPrint("Download error: $e");
    }
  }

  Future<void> _handleCast() async {
    final devices = _castService.getDevices();
    if (devices.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("No DLNA devices found")));
      return;
    }
    
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.8),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: devices.length,
            itemBuilder: (ctx, i) => ListTile(
              leading: const Icon(Icons.tv, color: Colors.white),
              title: Text(devices[i].friendlyName ?? "Unknown Device", style: const TextStyle(color: Colors.white)),
              onTap: () async {
                Navigator.pop(ctx);
                try {
                  final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
                  await _castService.cast(devices[i], streamInfo.streamUrl, widget.video.title);
                  if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Casting to ${devices[i].friendlyName}")));
                } catch (e) {
                  if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Cast Error: $e")));
                }
              },
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isPipMode) return _buildPlayerOnly();
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      body: Stack(
        children: [
          if (widget.video.coverUrl != null)
            Positioned.fill(
              child: ImageFiltered(
                imageFilter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                child: CachedNetworkImage(
                  imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
                  fit: BoxFit.cover,
                  color: theme.scaffoldBackgroundColor.withValues(alpha: 0.6),
                  colorBlendMode: isDark ? BlendMode.darken : BlendMode.lighten,
                ),
              ),
            ),
          Positioned.fill(child: Container(color: theme.scaffoldBackgroundColor.withValues(alpha: 0.4))),
          SafeArea(
            child: Column(
              children: [
                _buildAppBar(context),
                Expanded(
                  child: kIsWeb 
                    ? _buildWebPlaceholder()
                    : _isLoading
                      ? _buildLoading(context)
                      : _errorMessage != null
                          ? _buildError(context)
                          : _buildFullDetailView(context),
                ),
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _toggleFavorite,
        backgroundColor: Colors.red,
        child: Icon(_isFavorite ? Icons.favorite : Icons.favorite_border, color: Colors.white),
      ),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(icon: Icon(Icons.arrow_back, color: theme.colorScheme.onSurface), onPressed: () => Navigator.of(context).pop()),
          if (defaultTargetPlatform == TargetPlatform.android && !_isLoading && _errorMessage == null)
            IconButton(icon: Icon(Icons.picture_in_picture_alt, color: theme.colorScheme.onSurface), onPressed: _enterPipMode),
        ],
      ),
    );
  }

  Widget _buildPlayerOnly() {
    return _chewieController != null ? Chewie(controller: _chewieController!) : const Center(child: CircularProgressIndicator());
  }

  Widget _buildLoading(BuildContext context) {
    return Column(
      children: [
        Hero(
          tag: widget.heroTag ?? widget.video.id,
          createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
          child: AspectRatio(aspectRatio: 16 / 9, child: _buildCoverImage()),
        ),
        const Expanded(child: Center(child: CircularProgressIndicator(color: Colors.red))),
      ],
    );
  }

  Widget _buildError(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Hero(
          tag: widget.heroTag ?? widget.video.id,
          createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
          child: AspectRatio(aspectRatio: 16 / 9, child: _buildCoverImage()),
        ),
        Expanded(child: Center(child: Text(_errorMessage!, style: TextStyle(color: theme.colorScheme.error)))),
      ],
    );
  }

  Widget _buildCoverImage() {
    if (widget.video.coverUrl != null) {
      return CachedNetworkImage(
        imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
        fit: BoxFit.cover,
        placeholder: (context, url) => Container(color: Colors.grey[900]),
        errorWidget: (context, url, error) => Container(color: Colors.grey[800]),
      );
    }
    return Container(color: Colors.grey[900]);
  }

  Widget _buildFullDetailView(BuildContext context) {
    final theme = Theme.of(context);
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              Hero(
                tag: widget.heroTag ?? widget.video.id,
                createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
                child: AspectRatio(
                  aspectRatio: _videoPlayerController?.value.aspectRatio ?? 16 / 9,
                  child: VideoGestureWrapper(
                    controller: _videoPlayerController!,
                    isLocked: _isLocked,
                    child: _chewieController != null 
                        ? Chewie(controller: _chewieController!)
                        : const Center(child: CircularProgressIndicator()),
                  ),
                ),
              ),
              if (!_isLoading && _errorMessage == null)
                Positioned(
                  right: 10,
                  bottom: 10,
                  child: IconButton(
                    icon: Icon(
                      _isLocked ? Icons.lock : Icons.lock_open,
                      color: Colors.white.withValues(alpha: 0.5),
                    ),
                    onPressed: _toggleLock,
                  ),
                ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.video.title, style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 20, fontWeight: FontWeight.bold)),
                const SizedBox(height: 12),
                
                // Action Buttons
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    IconButton(
                      icon: Icon(Icons.download, color: theme.colorScheme.onSurface), 
                      onPressed: _handleDownload,
                      tooltip: "Download",
                    ),
                    IconButton(
                      icon: Icon(Icons.cast, color: theme.colorScheme.onSurface), 
                      onPressed: _handleCast,
                      tooltip: "Cast",
                    ),
                    IconButton(
                      icon: Icon(Icons.share, color: theme.colorScheme.onSurface), 
                      onPressed: () {
                         // Share logic
                      },
                      tooltip: "Share",
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                Row(
                  children: [
                    if (widget.video.duration != null) _infoBadge(context, Icons.timer, widget.video.duration!),
                    const SizedBox(width: 15),
                    if (widget.video.releaseDate != null) _infoBadge(context, Icons.calendar_today, widget.video.releaseDate!),
                    const SizedBox(width: 15),
                    if (_hasSubtitles(widget.video)) _infoBadge(context, Icons.subtitles, "Subtitled"),
                  ],
                ),
                if (widget.video.categories != null && widget.video.categories!.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Wrap(spacing: 8, runSpacing: 8, children: widget.video.categories!.map((cat) => _tagChip(context, cat, Colors.red.withValues(alpha: 0.8))).toList()),
                ],
                if (widget.video.actors != null && widget.video.actors!.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  Text("Actors", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: widget.video.actors!.map((actor) {
                      return InkWell(
                        onTap: () {
                          Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: actor, actor: actor)));
                        },
                        child: _tagChip(context, actor, theme.cardColor.withValues(alpha: 0.5)),
                      );
                    }).toList(),
                  ),
                ],
                if (_relatedVideos.isNotEmpty) ...[
                  const SizedBox(height: 30),
                  Text("You May Also Like", style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 140,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      itemCount: _relatedVideos.length,
                      itemBuilder: (context, index) {
                         final rv = _relatedVideos[index];
                         return Padding(
                           padding: const EdgeInsets.only(right: 12),
                           child: SizedBox(
                             width: 200, 
                             child: VideoCard(
                               video: rv, 
                               onTap: () => Navigator.push(
                                 context, 
                                 MaterialPageRoute(builder: (_) => PlayerPage(video: rv))
                               ),
                             ),
                           ),
                         );
                      }
                    ),
                  ),
                ],
                const SizedBox(height: 120),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWebPlaceholder() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text("Embedded playback not supported on Web."),
          const SizedBox(height: 20),
          ElevatedButton(onPressed: () => launchUrl(Uri.parse(widget.video.sourceUrl)), child: const Text("Watch on Source")),
        ],
      ),
    );
  }

  Widget _infoBadge(BuildContext context, IconData icon, String text) {
    final theme = Theme.of(context);
    return Row(children: [Icon(icon, size: 16, color: theme.colorScheme.onSurface.withValues(alpha: 0.7)), const SizedBox(width: 6), Text(text, style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontSize: 13))]);
  }

  bool _hasSubtitles(Video video) {
    final title = video.title.toLowerCase();
    final categories = video.categories?.map((e) => e.toLowerCase()).toList() ?? [];
    return title.contains("中文字幕") || title.contains("中文") || categories.contains("subtitled");
  }

  Widget _tagChip(BuildContext context, String label, Color color) {
    final theme = Theme.of(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 5, sigmaY: 5),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20), border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1))),
          child: Text(label, style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 12, fontWeight: FontWeight.w500)),
        ),
      ),
    );
  }
}
