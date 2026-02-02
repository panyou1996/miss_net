import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/material.dart';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'dart:async';

class FFmpegTask {
  final String taskId;
  final String filename;
  final String path;
  int progress;
  String status;

  FFmpegTask({
    required this.taskId,
    required this.filename,
    required this.path,
    this.progress = 0,
    this.status = "running",
  });
}

class DownloadService {
  final _ffmpegTasks = <String, FFmpegTask>{};
  final _controller = StreamController<List<FFmpegTask>>.broadcast();

  Stream<List<FFmpegTask>> get ffmpegTaskStream => _controller.stream;

  Future<void> init() async {
    await FlutterDownloader.initialize(debug: true, ignoreSsl: true);
  }

  List<FFmpegTask> getActiveFFmpegTasks() => _ffmpegTasks.values.toList();

  Future<String?> downloadVideo(String url, String filename, {Map<String, String>? headers}) async {
    var status = await Permission.storage.status;
    if (!status.isGranted) {
        status = await Permission.storage.request();
    }

    final dir = await getExternalStorageDirectory();
    if (dir == null) return null;
    final String savePath = "${dir.path}/$filename";

    if (url.contains('.m3u8')) {
      String headerString = "";
      if (headers != null) {
        headers.forEach((key, value) {
          headerString += "$key: $value\r\n";
        });
      }

      final taskId = "ffmpeg_${DateTime.now().millisecondsSinceEpoch}";
      final newTask = FFmpegTask(taskId: taskId, filename: filename, path: savePath);
      _ffmpegTasks[taskId] = newTask;
      _controller.add(_ffmpegTasks.values.toList());

      final command = "-headers \"$headerString\" -i \"$url\" -c copy -bsf:a aac_adtstoasc \"$savePath\"";
      
      FFmpegKit.execute(command).then((session) async {
        final returnCode = await session.getReturnCode();
        if (ReturnCode.isSuccess(returnCode)) {
          _ffmpegTasks[taskId]?.status = "complete";
          _ffmpegTasks[taskId]?.progress = 100;
        } else {
          _ffmpegTasks[taskId]?.status = "failed";
        }
        _controller.add(_ffmpegTasks.values.toList());
      });

      // Track progress via statistics
      FFmpegKit.listSessions().then((sessions) {
         // In a real app, we'd use session.getStatistics() callback
         // but for simplicity, we update status on return.
      });
      
      return taskId;
    }

    final taskId = await FlutterDownloader.enqueue(
      url: url,
      savedDir: dir.path,
      fileName: filename,
      showNotification: true,
      openFileFromNotification: true,
      headers: headers ?? {},
    );
    return taskId;
  }

  void removeFFmpegTask(String taskId) {
    _ffmpegTasks.remove(taskId);
    _controller.add(_ffmpegTasks.values.toList());
  }
}