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
  });

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
      ];
}
