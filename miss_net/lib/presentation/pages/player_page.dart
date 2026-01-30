import 'dart:async';
import 'package:chewie/chewie.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui'; 
import 'package:cached_network_image/cached_network_image.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import '../../core/services/video_resolver.dart';
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
  VideoPlayerController? _videoPlayerController;
  ChewieController? _chewieController;
  bool _isLoading = true;
  String? _errorMessage;
  bool _isFavorite = false;
  bool _isPipMode = false;
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
      await _repository.saveToHistory(widget.video, position);
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
      final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
      
      _videoPlayerController = VideoPlayerController.networkUrl(
        Uri.parse(streamInfo.streamUrl),
        httpHeaders: streamInfo.headers,
      );

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
        showControls: true,
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

  @override
  void dispose() {
    _progressTimer?.cancel();
    _saveProgress(); // Final save
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

  @override
  Widget build(BuildContext context) {
    if (_isPipMode) return _buildPlayerOnly();

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          if (widget.video.coverUrl != null)
            Positioned.fill(
              child: ImageFiltered(
                imageFilter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                child: CachedNetworkImage(
                  imageUrl: ImageProxy.getUrl(widget.video.coverUrl!),
                  fit: BoxFit.cover,
                  color: Colors.black.withValues(alpha: 0.6),
                  colorBlendMode: BlendMode.darken,
                ),
              ),
            ),
          Positioned.fill(child: Container(color: Colors.black.withValues(alpha: 0.4))),
          SafeArea(
            child: Column(
              children: [
                _buildAppBar(),
                Expanded(
                  child: kIsWeb 
                    ? _buildWebPlaceholder()
                    : _isLoading
                      ? _buildLoading()
                      : _errorMessage != null
                          ? _buildError()
                          : _buildFullDetailView(),
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

  Widget _buildAppBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(icon: const Icon(Icons.arrow_back, color: Colors.white), onPressed: () => Navigator.of(context).pop()),
          if (defaultTargetPlatform == TargetPlatform.android && !_isLoading && _errorMessage == null)
            IconButton(icon: const Icon(Icons.picture_in_picture_alt, color: Colors.white), onPressed: _enterPipMode),
        ],
      ),
    );
  }

  Widget _buildPlayerOnly() {
    return _chewieController != null ? Chewie(controller: _chewieController!) : const Center(child: CircularProgressIndicator());
  }

  Widget _buildLoading() {
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

  Widget _buildError() {
    return Column(
      children: [
        Hero(
          tag: widget.heroTag ?? widget.video.id,
          createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
          child: AspectRatio(aspectRatio: 16 / 9, child: _buildCoverImage()),
        ),
        Expanded(child: Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)))),
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

  Widget _buildFullDetailView() {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Hero(
            tag: widget.heroTag ?? widget.video.id,
            createRectTween: (begin, end) => MaterialRectArcTween(begin: begin, end: end),
            child: AspectRatio(
              aspectRatio: _videoPlayerController!.value.aspectRatio,
              child: VideoGestureWrapper(
                controller: _videoPlayerController!,
                child: Chewie(controller: _chewieController!),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.video.title, style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
                const SizedBox(height: 12),
                Row(
                  children: [
                    if (widget.video.duration != null) _infoBadge(Icons.timer, widget.video.duration!),
                    const SizedBox(width: 15),
                    if (widget.video.releaseDate != null) _infoBadge(Icons.calendar_today, widget.video.releaseDate!),
                    const SizedBox(width: 15),
                    if (_hasSubtitles(widget.video)) _infoBadge(Icons.subtitles, "Subtitled"),
                  ],
                ),
                if (widget.video.categories != null && widget.video.categories!.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Wrap(spacing: 8, runSpacing: 8, children: widget.video.categories!.map((cat) => _tagChip(cat, Colors.red.withValues(alpha: 0.8))).toList()),
                ],
                if (widget.video.actors != null && widget.video.actors!.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  const Text("Actors", style: TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: widget.video.actors!.map((actor) {
                      return InkWell(
                        onTap: () {
                          Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: actor, actor: actor)));
                        },
                        child: _tagChip(actor, Colors.white.withValues(alpha: 0.1)),
                      );
                    }).toList(),
                  ),
                ],
                if (_relatedVideos.isNotEmpty) ...[
                  const SizedBox(height: 30),
                  const Text("You May Also Like", style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
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
          const Text("Embedded playback not supported on Web.", style: TextStyle(color: Colors.white)),
          const SizedBox(height: 20),
          ElevatedButton(onPressed: () => launchUrl(Uri.parse(widget.video.sourceUrl)), child: const Text("Watch on Source")),
        ],
      ),
    );
  }

  Widget _infoBadge(IconData icon, String text) {
    return Row(children: [Icon(icon, size: 16, color: Colors.white70), const SizedBox(width: 6), Text(text, style: const TextStyle(color: Colors.white70, fontSize: 13))]);
  }

  bool _hasSubtitles(Video video) {
    final title = video.title.toLowerCase();
    final categories = video.categories?.map((e) => e.toLowerCase()).toList() ?? [];
    return title.contains("中文字幕") || title.contains("中文") || categories.contains("subtitled");
  }

  Widget _tagChip(String label, Color color) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 5, sigmaY: 5),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20), border: Border.all(color: Colors.white.withValues(alpha: 0.1))),
          child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w500)),
        ),
      ),
    );
  }
}
