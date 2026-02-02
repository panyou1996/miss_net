import 'package:flutter/material.dart';
import 'package:flutter_downloader/flutter_downloader.dart';
import 'dart:io';
import 'dart:async';
import '../../../injection_container.dart';
import '../../../core/services/download_service.dart';
import '../player_page.dart';
import '../../../domain/entities/video.dart';

class DownloadsPage extends StatefulWidget {
  const DownloadsPage({super.key});

  @override
  State<DownloadsPage> createState() => _DownloadsPageState();
}

class _DownloadsPageState extends State<DownloadsPage> {
  final DownloadService _downloadService = sl<DownloadService>();
  List<DownloadTask>? _tasks;
  List<FFmpegTask> _ffmpegTasks = [];
  bool _isLoading = true;
  StreamSubscription? _ffmpegSub;

  @override
  void initState() {
    super.initState();
    _ffmpegTasks = _downloadService.getActiveFFmpegTasks();
    _ffmpegSub = _downloadService.ffmpegTaskStream.listen((tasks) {
      if (mounted) setState(() => _ffmpegTasks = tasks);
    });
    _prepare();
  }

  @override
  void dispose() {
    _ffmpegSub?.cancel();
    super.dispose();
  }

  Future<void> _prepare() async {
    final tasks = await FlutterDownloader.loadTasks();
    if (mounted) {
      setState(() {
        _tasks = tasks;
        _isLoading = false;
      });
    }
  }

  Future<void> _deleteTask(String id, bool isFfmpeg) async {
    if (isFfmpeg) {
      _downloadService.removeFFmpegTask(id);
    } else {
      await FlutterDownloader.remove(taskId: id, shouldDeleteContent: true);
      _prepare();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final allEmpty = (_tasks == null || _tasks!.isEmpty) && _ffmpegTasks.isEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text("Downloads", style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : allEmpty
              ? const Center(child: Text("No downloads yet", style: TextStyle(color: Colors.grey)))
              : ListView(
                  children: [
                    ..._ffmpegTasks.map((task) => _buildFFmpegTile(task, theme)),
                    if (_tasks != null)
                      ..._tasks!.map((task) => _buildDownloaderTile(task, theme)),
                  ],
                ),
    );
  }

  Widget _buildFFmpegTile(FFmpegTask task, ThemeData theme) {
    return ListTile(
      leading: const Icon(Icons.bolt, color: Colors.amber),
      title: Text(task.filename, style: TextStyle(color: theme.colorScheme.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 4),
          LinearProgressIndicator(
            value: task.status == "complete" ? 1.0 : (task.status == "failed" ? 0.0 : null),
            color: Colors.amber,
            backgroundColor: Colors.amber.withValues(alpha: 0.1),
          ),
          const SizedBox(height: 4),
          Text("${task.status.toUpperCase()} • HLS Stream", style: const TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
      trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _deleteTask(task.taskId, true)),
      onTap: task.status == "complete" ? () => _playFfmpeg(task) : null,
    );
  }

  Widget _buildDownloaderTile(DownloadTask task, ThemeData theme) {
    return ListTile(
      leading: const Icon(Icons.video_file, color: Colors.red),
      title: Text(task.filename ?? "Unknown", style: TextStyle(color: theme.colorScheme.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 4),
          LinearProgressIndicator(value: task.progress / 100, color: Colors.red, backgroundColor: Colors.red.withValues(alpha: 0.1)),
          const SizedBox(height: 4),
          Text("${_getStatusText(task.status)} • ${task.progress}%", style: const TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
      trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _deleteTask(task.taskId, false)),
      onTap: task.status == DownloadTaskStatus.complete ? () => _playDownloader(task) : null,
    );
  }

  String _getStatusText(DownloadTaskStatus status) {
    switch (status) {
      case DownloadTaskStatus.enqueued: return "Pending";
      case DownloadTaskStatus.running: return "Downloading";
      case DownloadTaskStatus.complete: return "Finished";
      case DownloadTaskStatus.failed: return "Failed";
      case DownloadTaskStatus.canceled: return "Canceled";
      case DownloadTaskStatus.paused: return "Paused";
      default: return "Unknown";
    }
  }

  void _playFfmpeg(FFmpegTask task) async {
    final file = File(task.path);
    if (await file.exists()) {
      _navigateToPlayer(task.taskId, task.filename, file.path);
    }
  }

  void _playDownloader(DownloadTask task) async {
    final file = File("${task.savedDir}/${task.filename}");
    if (await file.exists()) {
      _navigateToPlayer(task.taskId, task.filename ?? "Video", file.path);
    }
  }

  void _navigateToPlayer(String id, String title, String path) {
    if (mounted) {
      final video = Video(
        id: id, externalId: id, title: title, sourceUrl: "", coverUrl: null,
        createdAt: DateTime.now(), isOffline: true, filePath: path,
      );
      Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: video)));
    }
  }
}