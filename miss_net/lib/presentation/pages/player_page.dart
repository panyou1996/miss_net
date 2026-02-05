import 'dart:async';
import 'dart:io';
import 'package:chewie/chewie.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui'; 
import 'package:cached_network_image/cached_network_image.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:palette_generator/palette_generator.dart';
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
  Color _dominantColor = Colors.red;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _checkFavoriteStatus();
    _loadRelatedVideos();
    _updatePalette();
    if (!kIsWeb) {
      _initializePlayer();
    } else {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _updatePalette() async {
    if (widget.video.coverUrl == null) return;
    try {
      final image = CachedNetworkImageProvider(ImageProxy.getUrl(widget.video.coverUrl!));
      final palette = await PaletteGenerator.fromImageProvider(image, maximumColorCount: 10);
      if (mounted) {
        setState(() {
          _dominantColor = palette.dominantColor?.color ?? Colors.red;
        });
      }
    } catch (e) {
      debugPrint("Palette Error: $e");
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
    HapticFeedback.mediumImpact();
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
          playedColor: _dominantColor,
          handleColor: _dominantColor,
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
    _saveProgress(); 
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
      final filename = "${widget.video.title.replaceAll(RegExp(r'[\/:*?"<>|]'), '_')}.mp4";
      final id = await _downloadService.downloadVideo(url, filename, headers: streamInfo.headers);
      if (id != null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(id.startsWith("ffmpeg_") ? "HLS Download started (FFmpeg)" : "Download started"))
          );
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

  void _showSpeedDialog() {
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text("Playback Speed", style: GoogleFonts.playfairDisplay(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              ...speeds.map((speed) => ListTile(
                title: Text("${speed}x", style: const TextStyle(color: Colors.white)),
                trailing: _videoPlayerController?.value.playbackSpeed == speed 
                    ? Icon(Icons.check, color: _dominantColor) 
                    : null,
                onTap: () {
                  _videoPlayerController?.setPlaybackSpeed(speed);
                  Navigator.pop(ctx);
                  setState(() {});
                },
              )),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _playerActionButton(BuildContext context, IconData icon, String label, VoidCallback onTap) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: () {
        HapticFeedback.lightImpact();
        onTap();
      },
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: theme.colorScheme.onSurface, size: 22),
            const SizedBox(height: 4),
            Text(label, style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6), fontSize: 10, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isPipMode) return _buildPlayerOnly();
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: isDark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark,
      child: Scaffold(
        body: Stack(
          children: [
            if (widget.video.coverUrl != null)
              Positioned.fill(
                child: ImageFiltered(
                  imageFilter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                  child: CachedNetworkImage(
                    imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
                    fit: BoxFit.cover,
                    color: isDark 
                        ? Colors.black.withValues(alpha: 0.7) 
                        : Colors.white.withValues(alpha: 0.85),
                    colorBlendMode: isDark ? BlendMode.darken : BlendMode.lighten,
                  ),
                ),
              ),
            Positioned.fill(child: Container(color: theme.scaffoldBackgroundColor.withValues(alpha: 0.3))),
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
          backgroundColor: _dominantColor,
          elevation: 10,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          child: Icon(_isFavorite ? Icons.favorite : Icons.favorite_border, color: Colors.white),
        ),
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
    return SingleChildScrollView(
      child: Column(
        children: [
          Hero(
            tag: widget.heroTag ?? widget.video.id,
            createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
            child: AspectRatio(aspectRatio: 16 / 9, child: _buildCoverImage()),
          ),
          const SizedBox(height: 100),
          const Center(child: CircularProgressIndicator(color: Colors.red)),
        ],
      ),
    );
  }

  Widget _buildError(BuildContext context) {
    final theme = Theme.of(context);
    return SingleChildScrollView(
      child: Column(
        children: [
          Hero(
            tag: widget.heroTag ?? widget.video.id,
            createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
            child: AspectRatio(aspectRatio: 16 / 9, child: _buildCoverImage()),
          ),
          const SizedBox(height: 50),
          Center(child: Text(_errorMessage!, style: TextStyle(color: theme.colorScheme.error))),
        ],
      ),
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
            alignment: Alignment.center,
            children: [
              if (widget.video.coverUrl != null)
                Positioned(
                  top: 0,
                  child: Opacity(
                    opacity: 0.8,
                    child: ImageFiltered(
                      imageFilter: ImageFilter.blur(sigmaX: 100, sigmaY: 100),
                      child: CachedNetworkImage(
                        imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
                        width: MediaQuery.of(context).size.width,
                        height: 300,
                        fit: BoxFit.cover,
                      ),
                    ),
                  ),
                ),
              Hero(
                tag: widget.heroTag ?? widget.video.id,
                createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Container(
                    decoration: BoxDecoration(
                      boxShadow: [
                        BoxShadow(color: _dominantColor.withValues(alpha: 0.3), blurRadius: 30, offset: const Offset(0, 10))
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(20),
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
                  ),
                ),
              ),
              if (!_isLoading && _errorMessage == null)
                Positioned(
                  right: 20,
                  bottom: 10,
                  child: IconButton(
                    icon: Icon(_isLocked ? Icons.lock : Icons.lock_open, color: Colors.white.withValues(alpha: 0.5), size: 20),
                    onPressed: _toggleLock,
                  ),
                ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 32.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.video.title, 
                  style: GoogleFonts.playfairDisplay(
                    color: theme.colorScheme.onSurface, 
                    fontSize: 28, 
                    fontWeight: FontWeight.w900, 
                    height: 1.1,
                    letterSpacing: -0.5
                  )
                ),
                const SizedBox(height: 24),
                Center(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(35),
                    child: BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.onSurface.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(35),
                          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            _playerActionButton(context, Icons.download_rounded, "SAVE", _handleDownload),
                            _playerActionButton(context, Icons.speed_rounded, "SPEED", _showSpeedDialog),
                            _playerActionButton(context, Icons.cast_connected_rounded, "CAST", _handleCast),
                            _playerActionButton(context, Icons.share_rounded, "SHARE", () {}),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                Row(
                  children: [
                    _infoBadge(context, Icons.timer_outlined, widget.video.duration ?? "Unknown"),
                    const SizedBox(width: 16),
                    _infoBadge(context, Icons.calendar_month_outlined, widget.video.releaseDate ?? "Recent"),
                  ],
                ),
                if (widget.video.categories != null && widget.video.categories!.isNotEmpty) ...[
                  const SizedBox(height: 32),
                  _buildVisualHeader("CATEGORIES"),
                  const SizedBox(height: 16),
                  Wrap(spacing: 10, runSpacing: 10, children: widget.video.categories!.map((cat) => _tagChip(context, cat)).toList()),
                ],
                if (widget.video.actors != null && widget.video.actors!.isNotEmpty) ...[
                  const SizedBox(height: 32),
                  _buildVisualHeader("ACTRESSES"),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: widget.video.actors!.map((actor) {
                      return InkWell(
                        onTap: () {
                          HapticFeedback.selectionClick();
                          Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: actor, actor: actor)));
                        },
                        borderRadius: BorderRadius.circular(12),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.onSurface.withValues(alpha: 0.06),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1)),
                          ),
                          child: Text(actor, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                        ),
                      );
                    }).toList(),
                  ),
                ],
                if (_relatedVideos.isNotEmpty) ...[
                  const SizedBox(height: 40),
                  Text("Related Content", style: GoogleFonts.playfairDisplay(color: theme.colorScheme.onSurface, fontSize: 22, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 16),
                  SizedBox(
                    height: 160,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      itemCount: _relatedVideos.length,
                      itemBuilder: (context, index) {
                         final rv = _relatedVideos[index];
                         return Padding(
                           padding: const EdgeInsets.only(right: 16),
                           child: SizedBox(
                             width: 220, 
                             child: VideoCard(
                               video: rv, 
                               onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: rv))),
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

  Widget _buildVisualHeader(String title) {
    return Row(
      children: [
        Container(width: 4, height: 16, decoration: BoxDecoration(color: _dominantColor, borderRadius: BorderRadius.circular(2))),
        const SizedBox(width: 8),
        Text(title, style: TextStyle(color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6), fontSize: 13, fontWeight: FontWeight.w900, letterSpacing: 1.2)),
      ],
    );
  }

  Widget _infoBadge(BuildContext context, IconData icon, String text) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: theme.colorScheme.onSurface.withValues(alpha: 0.05), borderRadius: BorderRadius.circular(8)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: theme.colorScheme.onSurface.withValues(alpha: 0.5)), 
          const SizedBox(width: 6), 
          Text(text, style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6), fontSize: 12, fontWeight: FontWeight.w600))
        ]
      ),
    );
  }

  Widget _tagChip(BuildContext context, String label) {
    final theme = Theme.of(context);
    final isImportant = label.toUpperCase() == "SUBTITLED" || label.contains("中文");
    return InkWell(
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: label, category: label))),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: isImportant ? _dominantColor.withValues(alpha: 0.12) : theme.colorScheme.onSurface.withValues(alpha: 0.06),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: isImportant ? _dominantColor.withValues(alpha: 0.25) : theme.dividerColor.withValues(alpha: 0.15), width: 0.8),
        ),
        child: Text(label, style: TextStyle(color: isImportant ? _dominantColor : theme.colorScheme.onSurface.withValues(alpha: 0.8), fontSize: 12, fontWeight: FontWeight.w600)),
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
}