import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../entities/video.dart';

abstract class VideoRepository {
  Future<Either<Failure, List<Video>>> getRecentVideos({int limit = 20, int offset = 0, String? category, String? actor});
  Future<Either<Failure, List<Video>>> searchVideos(String query);
  Future<Either<Failure, List<String>>> getSearchSuggestions(String query);
  
  // Favorites
  Future<Either<Failure, List<Video>>> getFavorites();
  Future<Either<Failure, void>> saveFavorite(Video video);
  Future<Either<Failure, void>> removeFavorite(String id);
  Future<bool> isFavorite(String id);
}
