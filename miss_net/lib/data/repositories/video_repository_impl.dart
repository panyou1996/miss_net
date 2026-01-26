import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../../domain/entities/video.dart';
import '../../domain/repositories/video_repository.dart';
import '../datasources/local_video_datasource.dart';
import '../datasources/video_datasource.dart';
import '../models/video_model.dart';

class VideoRepositoryImpl implements VideoRepository {
  final VideoDataSource remoteDataSource;
  final LocalVideoDataSource localDataSource;

  VideoRepositoryImpl({
    required this.remoteDataSource,
    required this.localDataSource,
  });

  @override
  Future<Either<Failure, List<Video>>> getRecentVideos({int limit = 20, int offset = 0, String? category, String? actor}) async {
    try {
      final videos = await remoteDataSource.getRecentVideos(limit: limit, offset: offset, category: category, actor: actor);
      return Right(videos);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, List<Video>>> searchVideos(String query) async {
    try {
      final videos = await remoteDataSource.searchVideos(query);
      return Right(videos);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, List<String>>> getSearchSuggestions(String query) async {
    try {
      final suggestions = await remoteDataSource.getSearchSuggestions(query);
      return Right(suggestions);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  // --- Favorites ---

  @override
  Future<Either<Failure, List<Video>>> getFavorites() async {
    try {
      final videos = await localDataSource.getFavorites();
      return Right(videos);
    } catch (e) {
      return const Left(CacheFailure("Cache Error"));
    }
  }

  @override
  Future<Either<Failure, void>> saveFavorite(Video video) async {
    try {
      final model = VideoModel(
        id: video.id,
        externalId: video.externalId,
        title: video.title,
        coverUrl: video.coverUrl,
        sourceUrl: video.sourceUrl,
        createdAt: video.createdAt,
        duration: video.duration,
        releaseDate: video.releaseDate,
        actors: video.actors,
        categories: video.categories,
      );
      await localDataSource.saveFavorite(model);
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("Cache Error"));
    }
  }

  @override
  Future<Either<Failure, void>> removeFavorite(String id) async {
    try {
      await localDataSource.removeFavorite(id);
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("Cache Error"));
    }
  }

  @override
  Future<bool> isFavorite(String id) async {
    return await localDataSource.isFavorite(id);
  }
}

class CacheFailure extends Failure {
  const CacheFailure(super.message);
}
