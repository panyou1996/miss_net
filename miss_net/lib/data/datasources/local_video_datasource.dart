import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/video_model.dart';

abstract class LocalVideoDataSource {
  Future<List<VideoModel>> getFavorites();
  Future<void> saveFavorite(VideoModel video);
  Future<void> removeFavorite(String id);
  Future<bool> isFavorite(String id);
}

class LocalVideoDataSourceImpl implements LocalVideoDataSource {
  final SharedPreferences sharedPreferences;

  LocalVideoDataSourceImpl(this.sharedPreferences);

  static const String CACHED_FAVORITES = 'CACHED_FAVORITES';

  @override
  Future<List<VideoModel>> getFavorites() async {
    final jsonString = sharedPreferences.getString(CACHED_FAVORITES);
    if (jsonString != null) {
      try {
        List<dynamic> jsonList = json.decode(jsonString);
        return jsonList.map((e) => VideoModel.fromJson(e)).toList();
      } catch (e) {
        print("Favorites Parse Error: $e");
        return [];
      }
    } else {
      return [];
    }
  }

  @override
  Future<void> saveFavorite(VideoModel video) async {
    List<VideoModel> currentFavorites = await getFavorites();
    if (!currentFavorites.any((v) => v.id == video.id)) {
      currentFavorites.insert(0, video);
      await _saveList(currentFavorites);
    }
  }

  @override
  Future<void> removeFavorite(String id) async {
    List<VideoModel> currentFavorites = await getFavorites();
    currentFavorites.removeWhere((v) => v.id == id);
    await _saveList(currentFavorites);
  }

  @override
  Future<bool> isFavorite(String id) async {
    List<VideoModel> currentFavorites = await getFavorites();
    return currentFavorites.any((v) => v.id == id);
  }

  Future<void> _saveList(List<VideoModel> videos) async {
    final String jsonString = json.encode(videos.map((v) => v.toJson()).toList());
    await sharedPreferences.setString(CACHED_FAVORITES, jsonString);
  }
}
