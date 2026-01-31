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
  Future<void> saveToHistory(VideoModel video, int positionMs, int totalDurationMs);
  Future<int> getProgress(String id);
  Future<void> clearHistory();

  // Search History
  Future<List<String>> getSearchHistory();
  Future<void> saveSearch(String query);
  Future<void> clearSearchHistory();
}

class LocalVideoDataSourceImpl implements LocalVideoDataSource {
  final SharedPreferences sharedPreferences;

  LocalVideoDataSourceImpl(this.sharedPreferences);

  static const String _cachedFavorites = 'CACHED_FAVORITES';
  static const String _cachedHistory = 'CACHED_HISTORY';
  static const String _cachedSearch = 'CACHED_SEARCH';
  static const String _progressPrefix = 'PROGRESS_';
  static const String _durationPrefix = 'DURATION_';

  // --- Favorites ---

  @override
  Future<List<VideoModel>> getFavorites() async {
    final jsonString = sharedPreferences.getString(_cachedFavorites);
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
    await sharedPreferences.setString(_cachedFavorites, jsonString);
  }

  // --- History ---

  @override
  Future<List<VideoModel>> getHistory() async {
    final jsonString = sharedPreferences.getString(_cachedHistory);
    if (jsonString != null) {
      try {
        List<dynamic> jsonList = json.decode(jsonString);
        return jsonList.map((e) {
          final model = VideoModel.fromJson(e);
          final pos = sharedPreferences.getInt('$_progressPrefix${model.id}') ?? 0;
          final total = sharedPreferences.getInt('$_durationPrefix${model.id}') ?? 0;
          return VideoModel(
            id: model.id,
            externalId: model.externalId,
            title: model.title,
            coverUrl: model.coverUrl,
            sourceUrl: model.sourceUrl,
            createdAt: model.createdAt,
            duration: model.duration,
            releaseDate: model.releaseDate,
            actors: model.actors,
            categories: model.categories,
            lastPositionMs: pos,
            totalDurationMs: total,
          );
        }).toList();
      } catch (e) {
        return [];
      }
    }
    return [];
  }

  @override
  Future<void> saveToHistory(VideoModel video, int positionMs, int totalDurationMs) async {
    List<VideoModel> currentHistory = await getHistory();
    currentHistory.removeWhere((v) => v.id == video.id);
    currentHistory.insert(0, video);
    
    if (currentHistory.length > 20) {
      currentHistory = currentHistory.sublist(0, 20);
    }

    await sharedPreferences.setString(_cachedHistory, json.encode(currentHistory.map((v) => v.toJson()).toList()));
    await sharedPreferences.setInt('$_progressPrefix${video.id}', positionMs);
    await sharedPreferences.setInt('$_durationPrefix${video.id}', totalDurationMs);
  }

  @override
  Future<int> getProgress(String id) async {
    return sharedPreferences.getInt('$_progressPrefix$id') ?? 0;
  }

  @override
  Future<void> clearHistory() async {
    await sharedPreferences.remove(_cachedHistory);
    // Note: We don't clear progress for individual videos as that might still be useful
    // if the user re-watches them.
  }

  // --- Search History ---

  @override
  Future<List<String>> getSearchHistory() async {
    return sharedPreferences.getStringList(_cachedSearch) ?? [];
  }

  @override
  Future<void> saveSearch(String query) async {
    if (query.trim().isEmpty) return;
    List<String> history = await getSearchHistory();
    history.remove(query);
    history.insert(0, query);
    if (history.length > 10) history = history.sublist(0, 10);
    await sharedPreferences.setStringList(_cachedSearch, history);
  }

  @override
  Future<void> clearSearchHistory() async {
    await sharedPreferences.remove(_cachedSearch);
  }
}