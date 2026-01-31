import 'package:flutter/material.dart';
import 'package:flutter_downloader/flutter_downloader.dart';
import 'dart:io';
import '../player_page.dart';
import '../../../domain/entities/video.dart';

class DownloadsPage extends StatefulWidget {
  const DownloadsPage({super.key});

  @override
  State<DownloadsPage> createState() => _DownloadsPageState();
}

class _DownloadsPageState extends State<DownloadsPage> {
  List<DownloadTask>? _tasks;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _prepare();
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

  Future<void> _deleteTask(DownloadTask task) async {
    await FlutterDownloader.remove(taskId: task.taskId, shouldDeleteContent: true);
    _prepare();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text("Downloads", style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : _tasks == null || _tasks!.isEmpty
              ? const Center(child: Text("No downloads yet", style: TextStyle(color: Colors.grey)))
              : ListView.builder(
                  itemCount: _tasks!.length,
                  itemBuilder: (context, index) {
                    final task = _tasks![index];
                    return ListTile(
                      leading: const Icon(Icons.video_file, color: Colors.red),
                      title: Text(
                        task.filename ?? "Unknown Video",
                        style: TextStyle(color: theme.colorScheme.onSurface),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SizedBox(height: 4),
                          LinearProgressIndicator(
                            value: task.progress / 100,
                            color: Colors.red,
                            backgroundColor: Colors.red.withValues(alpha: 0.1),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            "${_getStatusText(task.status)} • ${task.progress}%",
                            style: const TextStyle(fontSize: 12, color: Colors.grey),
                          ),
                        ],
                      ),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.grey),
                        onPressed: () => _deleteTask(task),
                      ),
                      onTap: task.status == DownloadTaskStatus.complete
                          ? () => _playOffline(task)
                          : null,
                    );
                  },
                ),
    );
  }

  String _getStatusText(DownloadTaskStatus status) {
    switch (status) {
      case DownloadTaskStatus.enqueued:
        return "Pending";
      case DownloadTaskStatus.running:
        return "Downloading";
      case DownloadTaskStatus.complete:
        return "Finished";
      case DownloadTaskStatus.failed:
        return "Failed";
      case DownloadTaskStatus.canceled:
        return "Canceled";
      case DownloadTaskStatus.paused:
        return "Paused";
      default:
        return "Unknown";
    }
  }

  void _playOffline(DownloadTask task) async {
    final file = File("${task.savedDir}/${task.filename}");
    if (await file.exists()) {
      if (mounted) {
        final video = Video(
          id: task.taskId,
          externalId: task.taskId,
          title: task.filename ?? "Downloaded Video",
          sourceUrl: "",
          coverUrl: null,
          createdAt: DateTime.now(),
          isOffline: true,
          filePath: file.path,
        );
        Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => PlayerPage(video: video)),
        );
      }
    }
  }
}