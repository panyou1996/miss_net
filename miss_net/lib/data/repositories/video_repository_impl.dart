import 'dart:async';
import 'package:dartz/dartz.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../../core/error/failures.dart';
import '../../domain/entities/video.dart';
import '../../domain/repositories/video_repository.dart';
import '../../core/services/privacy_service.dart';
import '../datasources/local_video_datasource.dart';
import '../datasources/video_datasource.dart';
import '../models/video_model.dart';

class VideoRepositoryImpl implements VideoRepository {
  final VideoDataSource remoteDataSource;
  final LocalVideoDataSource localDataSource;
  final PrivacyService privacyService;
  final _favoritesController = StreamController<void>.broadcast();

  VideoRepositoryImpl({
    required this.remoteDataSource,
    required this.localDataSource,
    required this.privacyService,
  });

  @override
  Stream<void> get favoritesStream => _favoritesController.stream;

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

  @override
  Future<Either<Failure, List<Video>>> getRelatedVideos(Video video) async {
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
      final videos = await remoteDataSource.getRelatedVideos(model);
      return Right(videos);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, List<String>>> getPopularActors() async {
    try {
      final result = await remoteDataSource.getPopularActors();
      return Right(result);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, List<String>>> getPopularTags() async {
    try {
      final result = await remoteDataSource.getPopularTags();
      return Right(result);
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
      _favoritesController.add(null);
      // Try to sync instantly if logged in
      syncData();
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("Cache Error"));
    }
  }

  @override
  Future<Either<Failure, void>> removeFavorite(String id) async {
    try {
      await localDataSource.removeFavorite(id);
      _favoritesController.add(null);
      
      // Also remove from cloud if logged in
      final user = Supabase.instance.client.auth.currentUser;
      if (user != null) {
        await Supabase.instance.client.from('favorites')
          .delete()
          .eq('user_id', user.id)
          .eq('video_id', id);
      }
      
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("Cache Error"));
    }
  }

  @override
  Future<bool> isFavorite(String id) async {
    return await localDataSource.isFavorite(id);
  }

  // --- History ---

  @override
  Future<Either<Failure, List<Video>>> getHistory() async {
    try {
      final videos = await localDataSource.getHistory();
      return Right(videos);
    } catch (e) {
      return const Left(CacheFailure("History Cache Error"));
    }
  }

  @override
  Future<Either<Failure, void>> saveToHistory(Video video, int positionMs) async {
    if (privacyService.isIncognito) return const Right(null);
    try {
      final model = VideoModel(
        id: video.id,
        externalId: video.externalId,
        title: video.title,
        coverUrl: video.coverUrl,
        sourceUrl: video.sourceUrl,
        createdAt: video.createdAt,
        duration: video.duration,
        actors: video.actors,
        categories: video.categories,
        releaseDate: video.releaseDate,
      );
      await localDataSource.saveToHistory(model, positionMs);
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("History Save Error"));
    }
  }

  @override
  Future<int> getProgress(String id) async {
    return await localDataSource.getProgress(id);
  }

  @override
  Future<Either<Failure, void>> clearHistory() async {
    try {
      await localDataSource.clearHistory();
      return const Right(null);
    } catch (e) {
      return const Left(CacheFailure("Failed to clear history"));
    }
  }

  @override
  Future<void> syncData() async {
    final user = Supabase.instance.client.auth.currentUser;
    if (user == null) return;

    try {
      // 1. Push Local Favorites to Cloud
      final localFavs = await localDataSource.getFavorites();
      if (localFavs.isNotEmpty) {
        final updates = localFavs.map((v) => {
          'user_id': user.id,
          'video_id': v.id,
        }).toList();
        await Supabase.instance.client.from('favorites').upsert(updates);
      }

      // 2. Pull Cloud Favorites to Local
      final response = await Supabase.instance.client
          .from('favorites')
          .select('video_id, videos(*)')
          .eq('user_id', user.id);
      
      final List<dynamic> rows = response as List;
      for (var row in rows) {
        if (row['videos'] != null) {
          final videoModel = VideoModel.fromJson(row['videos']);
          await localDataSource.saveFavorite(videoModel);
        }
      }
      
      _favoritesController.add(null);
    } catch (e) {
      // print("Sync Failed: $e");
    }
  }
}

class CacheFailure extends Failure {
  const CacheFailure(super.message);
}
