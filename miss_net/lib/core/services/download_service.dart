import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/material.dart';

class DownloadService {
  Future<void> init() async {
    await FlutterDownloader.initialize(debug: true, ignoreSsl: true);
  }

  Future<String?> downloadVideo(String url, String filename) async {
    var status = await Permission.storage.status;
    if (!status.isGranted) {
        status = await Permission.storage.request();
    }

    final dir = await getExternalStorageDirectory();
    if (dir == null) return null;

    if (url.contains('.m3u8')) {
      // HLS download disabled due to build issues with FFmpegKit
      return "hls_disabled";
    }

    final taskId = await FlutterDownloader.enqueue(
      url: url,
      savedDir: dir.path,
      fileName: filename,
      showNotification: true,
      openFileFromNotification: true,
    );
    return taskId;
  }
}