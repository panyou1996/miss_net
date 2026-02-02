import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/material.dart';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'dart:async';
import 'dart:io';

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
    // 1. Better Permission Handling
    if (Platform.isAndroid) {
      final deviceInfo = await DeviceInfoPlugin().androidInfo;
      if (deviceInfo.version.sdkInt >= 33) {
        await [Permission.videos, Permission.photos].request();
      } else {
        await [Permission.storage].request();
      }
    }

    final dir = await getExternalStorageDirectory();
    if (dir == null) {
      debugPrint("Download Error: Could not get external storage directory");
      return null;
    }
    
    // Clean filename to prevent OS errors
    final cleanFilename = filename.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
    final String savePath = "${dir.path}/$cleanFilename";

    if (url.contains('.m3u8')) {
      String headerString = "";
      if (headers != null) {
        headers.forEach((key, value) {
          headerString += "$key: $value\r\n";
        });
      }

      final taskId = "ffmpeg_${DateTime.now().millisecondsSinceEpoch}";
      final newTask = FFmpegTask(taskId: taskId, filename: cleanFilename, path: savePath);
      _ffmpegTasks[taskId] = newTask;
      _controller.add(_ffmpegTasks.values.toList());

      // Correctly escape headers for FFmpeg
      final command = "-headers '$headerString' -i \"$url\" -c copy -bsf:a aac_adtstoasc \"$savePath\"";
      
      debugPrint("Executing FFmpeg: $command");
      
      FFmpegKit.execute(command).then((session) async {
        final returnCode = await session.getReturnCode();
        final logs = await session.getAllLogsAsString();
        
        if (ReturnCode.isSuccess(returnCode)) {
          debugPrint("FFmpeg Success: $savePath");
          _ffmpegTasks[taskId]?.status = "complete";
          _ffmpegTasks[taskId]?.progress = 100;
        } else {
          debugPrint("FFmpeg Failed. Logs: $logs");
          _ffmpegTasks[taskId]?.status = "failed";
        }
        _controller.add(_ffmpegTasks.values.toList());
      });
      
      return taskId;
    }

    final taskId = await FlutterDownloader.enqueue(
      url: url,
      savedDir: dir.path,
      fileName: cleanFilename,
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