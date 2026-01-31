import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../entities/video.dart';

abstract class VideoRepository {
  Future<Either<Failure, List<Video>>> getRecentVideos({int limit = 20, int offset = 0, String? category, String? actor});
  Future<Either<Failure, List<Video>>> searchVideos(String query);
  Future<Either<Failure, List<String>>> getSearchSuggestions(String query);
  Future<Either<Failure, List<Video>>> getRelatedVideos(Video video);
  Future<Either<Failure, List<String>>> getPopularActors();
  Future<Either<Failure, List<String>>> getPopularTags();
  
  // Favorites
  Future<Either<Failure, List<Video>>> getFavorites();
  Future<Either<Failure, void>> saveFavorite(Video video);
  Future<Either<Failure, void>> removeFavorite(String id);
  Future<bool> isFavorite(String id);
  Stream<void> get favoritesStream;

  // History
  Future<Either<Failure, List<Video>>> getHistory();
  Future<Either<Failure, void>> saveToHistory(Video video, int positionMs, int totalDurationMs);
  Future<int> getProgress(String id);
  Future<Either<Failure, void>> clearHistory();
  
  // Search History
  Future<Either<Failure, List<String>>> getSearchHistory();
  Future<Either<Failure, void>> saveSearch(String query);
  Future<Either<Failure, void>> clearSearchHistory();
  
  // Cloud Sync
  Future<void> syncData();
}
