import 'package:flutter_downloader/flutter_downloader.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

class DownloadService {
  Future<void> init() async {
    await FlutterDownloader.initialize(debug: true, ignoreSsl: true);
  }

  Future<String?> downloadVideo(String url, String filename) async {
    var status = await Permission.storage.status;
    if (!status.isGranted) {
        status = await Permission.storage.request();
    }
    
    // For Android 13+
    if (await Permission.manageExternalStorage.isDenied) {
        // Just try continue, standard storage permission might be enough or scoped storage
    }

    final dir = await getExternalStorageDirectory();
    if (dir == null) return null;

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
