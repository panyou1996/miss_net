import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/video_model.dart';

abstract class LocalVideoDataSource {
  Future<List<VideoModel>> getFavorites();
  Future<void> saveFavorite(VideoModel video);
  Future<void> removeFavorite(String id);
  Future<bool> isFavorite(String id);

  // History
  Future<List<VideoModel>> getHistory();
  Future<void> saveToHistory(VideoModel video, int positionMs);
  Future<int> getProgress(String id);
}

class LocalVideoDataSourceImpl implements LocalVideoDataSource {
  final SharedPreferences sharedPreferences;

  LocalVideoDataSourceImpl(this.sharedPreferences);

  static const String CACHED_FAVORITES = 'CACHED_FAVORITES';
  static const String CACHED_HISTORY = 'CACHED_HISTORY';
  static const String PROGRESS_PREFIX = 'PROGRESS_';

  // --- Favorites ---

  @override
  Future<List<VideoModel>> getFavorites() async {
    final jsonString = sharedPreferences.getString(CACHED_FAVORITES);
    if (jsonString != null) {
      try {
        List<dynamic> jsonList = json.decode(jsonString);
        return jsonList.map((e) => VideoModel.fromJson(e)).toList();
      } catch (e) {
        return [];
      }
    }
    return [];
  }

  @override
  Future<void> saveFavorite(VideoModel video) async {
    List<VideoModel> currentFavorites = await getFavorites();
    if (!currentFavorites.any((v) => v.id == video.id)) {
      currentFavorites.insert(0, video);
      await _saveFavorites(currentFavorites);
    }
  }

  @override
  Future<void> removeFavorite(String id) async {
    List<VideoModel> currentFavorites = await getFavorites();
    currentFavorites.removeWhere((v) => v.id == id);
    await _saveFavorites(currentFavorites);
  }

  @override
  Future<bool> isFavorite(String id) async {
    List<VideoModel> currentFavorites = await getFavorites();
    return currentFavorites.any((v) => v.id == id);
  }

  Future<void> _saveFavorites(List<VideoModel> videos) async {
    final String jsonString = json.encode(videos.map((v) => v.toJson()).toList());
    await sharedPreferences.setString(CACHED_FAVORITES, jsonString);
  }

  // --- History ---

  @override
  Future<List<VideoModel>> getHistory() async {
    final jsonString = sharedPreferences.getString(CACHED_HISTORY);
    if (jsonString != null) {
      try {
        List<dynamic> jsonList = json.decode(jsonString);
        return jsonList.map((e) => VideoModel.fromJson(e)).toList();
      } catch (e) {
        return [];
      }
    }
    return [];
  }

  @override
  Future<void> saveToHistory(VideoModel video, int positionMs) async {
    List<VideoModel> currentHistory = await getHistory();
    currentHistory.removeWhere((v) => v.id == video.id);
    currentHistory.insert(0, video);
    
    if (currentHistory.length > 20) {
      currentHistory = currentHistory.sublist(0, 20);
    }

    await sharedPreferences.setString(CACHED_HISTORY, json.encode(currentHistory.map((v) => v.toJson()).toList()));
    await sharedPreferences.setInt('$PROGRESS_PREFIX${video.id}', positionMs);
  }

  @override
  Future<int> getProgress(String id) async {
    return sharedPreferences.getInt('$PROGRESS_PREFIX$id') ?? 0;
  }
}