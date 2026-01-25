import 'package:dartz/dartz.dart';
import '../../core/error/failures.dart';
import '../entities/video.dart';

abstract class VideoRepository {
  Future<Either<Failure, List<Video>>> getRecentVideos({int page = 1});
  Future<Either<Failure, List<Video>>> searchVideos(String query);
}
