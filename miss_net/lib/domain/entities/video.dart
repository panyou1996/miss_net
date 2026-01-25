import 'package:equatable/equatable.dart';

class Video extends Equatable {
  final String id;
  final String externalId;
  final String title;
  final String? coverUrl;
  final String sourceUrl;
  final DateTime createdAt;

  const Video({
    required this.id,
    required this.externalId,
    required this.title,
    this.coverUrl,
    required this.sourceUrl,
    required this.createdAt,
  });

  @override
  List<Object?> get props => [id, externalId, title, coverUrl, sourceUrl, createdAt];
}
