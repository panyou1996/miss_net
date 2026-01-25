import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../entities/video.dart';

abstract class VideoRepository {
  Future<Either<Failure, List<Video>>> getRecentVideos({int limit = 20, int offset = 0});
  Future<Either<Failure, List<Video>>> searchVideos(String query);
}
