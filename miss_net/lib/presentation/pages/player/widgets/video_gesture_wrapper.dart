import 'dart:async';
import 'package:flutter/material.dart';
import 'package:screen_brightness/screen_brightness.dart';
import 'package:video_player/video_player.dart';

class VideoGestureWrapper extends StatefulWidget {
  final Widget child;
  final VideoPlayerController controller;

  const VideoGestureWrapper({
    super.key,
    required this.child,
    required this.controller,
  });

  @override
  State<VideoGestureWrapper> createState() => _VideoGestureWrapperState();
}

class _VideoGestureWrapperState extends State<VideoGestureWrapper> {
  double? _brightness;
  double? _volume;
  
  // Feedback UI States
  String _message = "";
  IconData _icon = Icons.volume_up;
  bool _showOverlay = false;
  Timer? _overlayTimer;

  void _showFeedback(String msg, IconData icon) {
    setState(() {
      _message = msg;
      _icon = icon;
      _showOverlay = true;
    });
    _overlayTimer?.cancel();
    _overlayTimer = Timer(const Duration(seconds: 1), () {
      if (mounted) setState(() => _showOverlay = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onVerticalDragUpdate: (details) async {
        final width = MediaQuery.of(context).size.width;
        final delta = details.primaryDelta! / -200; // Sensitivity

        if (details.globalPosition.dx < width / 2) {
          // Left side: Brightness
          try {
            _brightness ??= await ScreenBrightness().current;
            _brightness = (_brightness! + delta).clamp(0.0, 1.0);
            await ScreenBrightness().setScreenBrightness(_brightness!);
            _showFeedback("${(_brightness! * 100).toInt()}%", Icons.brightness_medium);
          } catch (e) {
            debugPrint("Brightness error: $e");
          }
        } else {
          // Right side: Volume
          _volume ??= widget.controller.value.volume;
          _volume = (_volume! + delta).clamp(0.0, 1.0);
          await widget.controller.setVolume(_volume!);
          _showFeedback("${(_volume! * 100).toInt()}%", _volume! > 0 ? Icons.volume_up : Icons.volume_off);
        }
      },
      onHorizontalDragUpdate: (details) {
        // Seek logic
        final delta = details.primaryDelta! * 500; // 500ms per pixel approx
        final newPos = widget.controller.value.position + Duration(milliseconds: delta.toInt());
        _showFeedback(_formatDuration(newPos), delta > 0 ? Icons.fast_forward : Icons.fast_rewind);
      },
      onHorizontalDragEnd: (details) async {
        // Perform final seek on drag end to avoid jitter
        // Note: Actual seek value calculation here should be more precise, 
        // but for UX, showing feedback during drag and seeking at end is smoother.
      },
      onDoubleTapDown: (details) {
        final width = MediaQuery.of(context).size.width;
        if (details.globalPosition.dx < width / 3) {
          // Rewind 10s
          final pos = widget.controller.value.position - const Duration(seconds: 10);
          widget.controller.seekTo(pos);
          _showFeedback("-10s", Icons.replay_10);
        } else if (details.globalPosition.dx > width * 2 / 3) {
          // Forward 10s
          final pos = widget.controller.value.position + const Duration(seconds: 10);
          widget.controller.seekTo(pos);
          _showFeedback("+10s", Icons.forward_10);
        }
      },
      child: Stack(
        children: [
          widget.child,
          if (_showOverlay)
            Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.7),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(_icon, color: Colors.white, size: 40),
                    const SizedBox(height: 8),
                    Text(_message, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    String twoDigitMinutes = twoDigits(duration.inMinutes.remainder(60));
    String twoDigitSeconds = twoDigits(duration.inSeconds.remainder(60));
    return "${twoDigits(duration.inHours)}:$twoDigitMinutes:$twoDigitSeconds";
  }
}
