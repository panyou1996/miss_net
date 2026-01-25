import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../../domain/entities/video.dart';
import '../../domain/repositories/video_repository.dart';
import '../datasources/video_datasource.dart';

class VideoRepositoryImpl implements VideoRepository {
  final VideoDataSource remoteDataSource;

  VideoRepositoryImpl({required this.remoteDataSource});

  @override
  Future<Either<Failure, List<Video>>> getRecentVideos({int limit = 20, int offset = 0}) async {
    try {
      final videos = await remoteDataSource.getRecentVideos(limit: limit, offset: offset);
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
}
