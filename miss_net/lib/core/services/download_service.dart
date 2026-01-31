import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:ffmpeg_kit_flutter_full/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_full/return_code.dart';
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
      _downloadHls(url, "${dir.path}/$filename");
      return "hls_task"; // Dummy ID for HLS
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

  void _downloadHls(String url, String outPath) {
    // Simple FFmpeg command to merge segments into MP4
    final command = "-i $url -c copy -bsf:a aac_adtstoasc \"$outPath\"";
    
    FFmpegKit.execute(command).then((session) async {
      final returnCode = await session.getReturnCode();
      if (ReturnCode.isSuccess(returnCode)) {
        debugPrint("HLS Download Success: $outPath");
      } else {
        debugPrint("HLS Download Failed");
      }
    });
  }
}
