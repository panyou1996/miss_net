import '../../domain/entities/video.dart';

class VideoModel extends Video {
  const VideoModel({
    required super.id,
    required super.externalId,
    required super.title,
    super.coverUrl,
    required super.sourceUrl,
    required super.createdAt,
    super.duration,
    super.releaseDate,
    super.actors,
    super.categories,
  });

  factory VideoModel.fromJson(Map<String, dynamic> json) {
    return VideoModel(
      id: json['id'] as String,
      externalId: json['external_id'] as String,
      title: json['title'] as String,
      coverUrl: json['cover_url'] as String?,
      sourceUrl: json['source_url'] as String,
      createdAt: DateTime.parse(json['created_at'] as String),
      duration: json['duration'] as String?,
      releaseDate: json['release_date'] as String?,
      actors: json['actors'] != null ? List<String>.from(json['actors'] as List) : null,
      categories: json['categories'] != null ? List<String>.from(json['categories'] as List) : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'external_id': externalId,
      'title': title,
      'cover_url': coverUrl,
      'source_url': sourceUrl,
      'created_at': createdAt.toIso8601String(),
      'duration': duration,
      'release_date': releaseDate,
      'actors': actors,
      'categories': categories,
    };
  }
}
