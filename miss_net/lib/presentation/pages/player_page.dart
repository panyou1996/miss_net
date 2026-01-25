import 'package:chewie/chewie.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import '../../core/services/video_resolver.dart';
import '../../domain/entities/video.dart';
import '../../domain/repositories/video_repository.dart';
import '../../injection_container.dart';

class PlayerPage extends StatefulWidget {
  final Video video;

  const PlayerPage({super.key, required this.video});

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

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkFavoriteStatus();
    if (!kIsWeb) {
      _initializePlayer();
    } else {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _checkFavoriteStatus() async {
    final isFav = await _repository.isFavorite(widget.video.id);
    if (mounted) {
      setState(() {
        _isFavorite = isFav;
      });
    }
  }

  Future<void> _toggleFavorite() async {
    if (_isFavorite) {
      await _repository.removeFavorite(widget.video.id);
    } else {
      await _repository.saveFavorite(widget.video);
    }
    await _checkFavoriteStatus();
  }

  Future<void> _initializePlayer() async {
    try {
      final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
      
      _videoPlayerController = VideoPlayerController.networkUrl(
        Uri.parse(streamInfo.streamUrl),
        httpHeaders: streamInfo.headers,
      );

      await _videoPlayerController!.initialize();

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
          backgroundColor: Colors.grey.withOpacity(0.5),
          bufferedColor: Colors.grey,
        ),
        errorBuilder: (context, errorMessage) {
          return Center(
            child: Text(
              errorMessage,
              style: const TextStyle(color: Colors.white),
            ),
          );
        },
        allowedScreenSleep: false,
        deviceOrientationsAfterFullScreen: [DeviceOrientation.portraitUp],
      );

      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = "Failed to load video: $e";
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _videoPlayerController?.dispose();
    _chewieController?.dispose();
    _resolver.dispose();
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    // This can be used to detect PiP mode changes on some versions of Flutter
    final isPip = MediaQuery.of(context).size.width < 400 && MediaQuery.of(context).size.height < 400;
    if (isPip != _isPipMode) {
      setState(() {
        _isPipMode = isPip;
      });
    }
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
    if (_isPipMode) {
      return _buildPlayerOnly();
    }

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.of(context).pop(),
        ),
        actions: [
          if (defaultTargetPlatform == TargetPlatform.android && !_isLoading && _errorMessage == null)
            IconButton(
              icon: const Icon(Icons.picture_in_picture_alt, color: Colors.white),
              onPressed: _enterPipMode,
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _toggleFavorite,
        backgroundColor: Colors.red,
        child: Icon(
          _isFavorite ? Icons.favorite : Icons.favorite_border,
          color: Colors.white,
        ),
      ),
      body: SafeArea(
        child: kIsWeb 
          ? _buildWebPlaceholder()
          : _isLoading
            ? _buildLoading()
            : _errorMessage != null
                ? _buildError()
                : _buildFullDetailView(),
      ),
    );
  }

  Widget _buildPlayerOnly() {
    return _chewieController != null 
      ? Chewie(controller: _chewieController!) 
      : const Center(child: CircularProgressIndicator());
  }

  Widget _buildFullDetailView() {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: _videoPlayerController!.value.aspectRatio,
            child: Chewie(controller: _chewieController!),
          ),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.video.title,
                  style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    if (widget.video.duration != null) _infoBadge(Icons.timer, widget.video.duration!),
                    const SizedBox(width: 15),
                    if (widget.video.releaseDate != null) _infoBadge(Icons.calendar_today, widget.video.releaseDate!),
                  ],
                ),
                if (widget.video.categories != null && widget.video.categories!.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: widget.video.categories!.map((cat) => _tagChip(cat, Colors.red)).toList(),
                  ),
                ],
                if (widget.video.actors != null && widget.video.actors!.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  const Text("Actors", style: TextStyle(color: Colors.grey, fontSize: 14, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: widget.video.actors!.map((actor) => _tagChip(actor, Colors.blueGrey[800]!)).toList(),
                  ),
                ],
                const SizedBox(height: 100),
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
          ElevatedButton(
            onPressed: () => launchUrl(Uri.parse(widget.video.sourceUrl)),
            child: const Text("Watch on Source"),
          ),
        ],
      ),
    );
  }

  Widget _buildLoading() {
    return const Center(child: CircularProgressIndicator(color: Colors.red));
  }

  Widget _buildError() {
    return Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)));
  }

  Widget _infoBadge(IconData icon, String text) {
    return Row(
      children: [
        Icon(icon, size: 14, color: Colors.grey),
        const SizedBox(width: 4),
        Text(text, style: const TextStyle(color: Colors.grey, fontSize: 12)),
      ],
    );
  }

  Widget _tagChip(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(15)),
      child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 12)),
    );
  }
}