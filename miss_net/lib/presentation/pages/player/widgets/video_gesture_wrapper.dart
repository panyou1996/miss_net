import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:screen_brightness/screen_brightness.dart';
import 'package:video_player/video_player.dart';
import 'dart:ui';

class VideoGestureWrapper extends StatefulWidget {
  final Widget child;
  final VideoPlayerController controller;
  final bool isLocked;

  const VideoGestureWrapper({
    super.key,
    required this.child,
    required this.controller,
    this.isLocked = false,
  });

  @override
  State<VideoGestureWrapper> createState() => _VideoGestureWrapperState();
}

class _VideoGestureWrapperState extends State<VideoGestureWrapper> with SingleTickerProviderStateMixin {
  double? _brightness;
  double? _volume;
  
  // Feedback UI States
  String _message = "";
  IconData _icon = Icons.volume_up;
  bool _showOverlay = false;
  Timer? _overlayTimer;

  // Ripple Animation
  Offset _ripplePos = Offset.zero;
  bool _showRipple = false;
  late AnimationController _rippleController;

  @override
  void initState() {
    super.initState();
    _rippleController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
  }

  @override
  void dispose() {
    _rippleController.dispose();
    _overlayTimer?.cancel();
    super.dispose();
  }

  void _showFeedback(String msg, IconData icon, {bool heavy = false}) {
    setState(() {
      _message = msg;
      _icon = icon;
      _showOverlay = true;
    });
    
    // Physical Haptics
    if (heavy) {
      HapticFeedback.mediumImpact();
    } else {
      HapticFeedback.selectionClick();
    }

    _overlayTimer?.cancel();
    _overlayTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted) setState(() => _showOverlay = false);
    });
  }

  void _triggerRipple(Offset pos) {
    setState(() {
      _ripplePos = pos;
      _showRipple = true;
    });
    _rippleController.forward(from: 0).then((_) {
      if (mounted) setState(() => _showRipple = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onVerticalDragUpdate: (details) async {
        if (widget.isLocked) return;
        final width = MediaQuery.of(context).size.width;
        final delta = details.primaryDelta! / -250; // Adjusted sensitivity

        if (details.globalPosition.dx < width / 2) {
          try {
            _brightness ??= await ScreenBrightness().application;
            _brightness = (_brightness! + delta).clamp(0.0, 1.0);
            await ScreenBrightness().setApplicationScreenBrightness(_brightness!);
            _showFeedback("${(_brightness! * 100).toInt()}%", Icons.brightness_medium);
          } catch (e) {
            debugPrint("Brightness error: $e");
          }
        } else {
          _volume ??= widget.controller.value.volume;
          _volume = (_volume! + delta).clamp(0.0, 1.0);
          await widget.controller.setVolume(_volume!);
          _showFeedback("${(_volume! * 100).toInt()}%", _volume! > 0 ? Icons.volume_up : Icons.volume_off);
        }
      },
      onHorizontalDragUpdate: (details) {
        if (widget.isLocked) return;
        final delta = details.primaryDelta! * 200;
        final currentPos = widget.controller.value.position;
        final newPos = currentPos + Duration(milliseconds: delta.toInt());
        
        final clampedPos = Duration(
          milliseconds: newPos.inMilliseconds.clamp(0, widget.controller.value.duration.inMilliseconds)
        );
        
        widget.controller.seekTo(clampedPos);
        _showFeedback(_formatDuration(clampedPos), delta > 0 ? Icons.fast_forward : Icons.fast_rewind);
      },
      onDoubleTapDown: (details) {
        if (widget.isLocked) return;
        final width = MediaQuery.of(context).size.width;
        final pos = details.localPosition;
        _triggerRipple(pos);

        if (pos.dx < width / 3) {
          final target = widget.controller.value.position - const Duration(seconds: 10);
          widget.controller.seekTo(target);
          _showFeedback("-10s", Icons.replay_10, heavy: true);
        } else if (pos.dx > width * 2 / 3) {
          final target = widget.controller.value.position + const Duration(seconds: 10);
          widget.controller.seekTo(target);
          _showFeedback("+10s", Icons.forward_10, heavy: true);
        }
      },
      child: Stack(
        children: [
          widget.child,
          if (_showRipple)
            Positioned(
              left: _ripplePos.dx - 60,
              top: _ripplePos.dy - 60,
              child: AnimatedBuilder(
                animation: _rippleController,
                builder: (context, child) {
                  return Container(
                    width: 120,
                    height: 120,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white.withValues(alpha: (1 - _rippleController.value) * 0.2),
                      border: Border.all(color: Colors.white.withValues(alpha: (1 - _rippleController.value) * 0.4), width: 2),
                    ),
                  );
                },
              ),
            ),
          if (_showOverlay)
            Center(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(20),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(_icon, color: Colors.white, size: 42),
                        const SizedBox(height: 12),
                        Text(_message, style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold, letterSpacing: 1)),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    String m = twoDigits(duration.inMinutes.remainder(60));
    String s = twoDigits(duration.inSeconds.remainder(60));
    return duration.inHours > 0 ? "${twoDigits(duration.inHours)}:$m:$s" : "$m:$s";
  }
}