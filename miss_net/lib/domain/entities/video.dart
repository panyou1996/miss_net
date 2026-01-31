import 'package:equatable/equatable.dart';

class Video extends Equatable {
  final String id;
  final String externalId;
  final String title;
  final String? coverUrl;
  final String sourceUrl;
  final DateTime createdAt;
  final String? duration;
  final String? releaseDate;
  final List<String>? actors;
  final List<String>? categories;
  final int? lastPositionMs;
  final int? totalDurationMs;

  const Video({
    required this.id,
    required this.externalId,
    required this.title,
    this.coverUrl,
    required this.sourceUrl,
    required this.createdAt,
    this.duration,
    this.releaseDate,
    this.actors,
    this.categories,
    this.lastPositionMs,
    this.totalDurationMs,
  });

  double get progress {
    if (lastPositionMs == null || totalDurationMs == null || totalDurationMs == 0) return 0.0;
    return (lastPositionMs! / totalDurationMs!).clamp(0.0, 1.0);
  }

  @override
  List<Object?> get props => [
        id,
        externalId,
        title,
        coverUrl,
        sourceUrl,
        createdAt,
        duration,
        releaseDate,
        actors,
        categories,
        lastPositionMs,
        totalDurationMs,
      ];
}
