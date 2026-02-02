import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/material.dart';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';

class DownloadService {
  Future<void> init() async {
    await FlutterDownloader.initialize(debug: true, ignoreSsl: true);
  }

  Future<String?> downloadVideo(String url, String filename, {Map<String, String>? headers}) async {
    var status = await Permission.storage.status;
    if (!status.isGranted) {
        status = await Permission.storage.request();
    }

    final dir = await getExternalStorageDirectory();
    if (dir == null) return null;
    final String savePath = "${dir.path}/$filename";

    if (url.contains('.m3u8')) {
      // Handle HLS download with FFmpeg
      String headerString = "";
      if (headers != null) {
        headers.forEach((key, value) {
          headerString += "$key: $value\r\n";
        });
      }

      // Use -headers before -i for input headers
      final command = "-headers \"$headerString\" -i \"$url\" -c copy -bsf:a aac_adtstoasc \"$savePath\"";
      
      debugPrint("Starting FFmpeg download: $command");
      
      // We return a "ffmpeg_" prefix ID to distinguish it from flutter_downloader tasks
      FFmpegKit.execute(command).then((session) async {
        final returnCode = await session.getReturnCode();
        if (ReturnCode.isSuccess(returnCode)) {
          debugPrint("FFmpeg download success: $savePath");
        } else {
          debugPrint("FFmpeg download failed with return code $returnCode");
        }
      });
      
      return "ffmpeg_${DateTime.now().millisecondsSinceEpoch}";
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
}