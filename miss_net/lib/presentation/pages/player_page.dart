import 'package:chewie/chewie.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import '../../core/services/video_resolver.dart';
import '../../domain/entities/video.dart';
import '../../injection_container.dart';

class PlayerPage extends StatefulWidget {
  final Video video;

  const PlayerPage({super.key, required this.video});

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  final VideoResolver _resolver = sl<VideoResolver>();
  final VideoRepository _repository = sl<VideoRepository>();
  VideoPlayerController? _videoPlayerController;
  ChewieController? _chewieController;
  bool _isLoading = true;
  String? _errorMessage;
  bool _isFavorite = false;

  @override
  void initState() {
    super.initState();
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
      // 1. Resolve the Stream URL
      final streamInfo = await _resolver.resolveStreamUrl(widget.video.sourceUrl);
      
      // 2. Initialize Video Player
      _videoPlayerController = VideoPlayerController.networkUrl(
        Uri.parse(streamInfo.streamUrl),
        httpHeaders: streamInfo.headers,
      );

      await _videoPlayerController!.initialize();

      // 3. Initialize Chewie
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
    _videoPlayerController?.dispose();
    _chewieController?.dispose();
    _resolver.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
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
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    "This video cannot be played embedded due to site restrictions.",
                    style: TextStyle(color: Colors.white, fontSize: 16),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton.icon(
                    onPressed: () {
                      launchUrl(Uri.parse(widget.video.sourceUrl));
                    }, 
                    icon: const Icon(Icons.open_in_new),
                    label: const Text("Watch on Source Site"),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ],
              ),
            )
          : _isLoading
            ? const Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    CircularProgressIndicator(color: Colors.red),
                    SizedBox(height: 20),
                    Text(
                      "Resolving Stream...",
                      style: TextStyle(color: Colors.white),
                    )
                  ],
                ),
              )
            : _errorMessage != null
                ? Center(
                    child: Text(
                      _errorMessage!,
                      style: const TextStyle(color: Colors.red),
                      textAlign: TextAlign.center,
                    ),
                  )
                : Chewie(controller: _chewieController!),
      ),
    );
  }
}
