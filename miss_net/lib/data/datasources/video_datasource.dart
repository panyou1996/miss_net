import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/video_model.dart';

abstract class VideoDataSource {
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category});
  Future<List<VideoModel>> searchVideos(String query);
  Future<List<String>> getSearchSuggestions(String query);
}

class SupabaseVideoDataSourceImpl implements VideoDataSource {
  final SupabaseClient supabase;

  SupabaseVideoDataSourceImpl(this.supabase);

  @override
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category}) async {
    // ... implementation remains similar but ensures it uses the new columns if needed
    var queryBuilder = supabase.from('videos').select();

    if (category != null && category != 'new') {
      queryBuilder = queryBuilder.or('tags.cs.{"$category"},categories.cs.{"$category"}');
    }

    final response = await queryBuilder
        .order('created_at', ascending: false)
        .range(offset, offset + limit - 1);

    return (response as List).map((e) => VideoModel.fromJson(e)).toList();
  }

  @override
  Future<List<VideoModel>> searchVideos(String query) async {
    // Multi-dimensional search: Title, Actors, or Categories
    // We use .or with ilike for title and contains for arrays if query matches exactly, 
    // but for partial array match we use the text casting trick if supported or just ilike on the whole row.
    
    final response = await supabase
        .from('videos')
        .select()
        .or('title.ilike.%$query%,actors.cs.{"$query"},categories.cs.{"$query"}')
        .order('created_at', ascending: false)
        .limit(50);

    return (response as List).map((e) => VideoModel.fromJson(e)).toList();
  }

  @override
  Future<List<String>> getSearchSuggestions(String query) async {
    if (query.isEmpty) return [];

    // Fetch titles and actors to suggest
    final response = await supabase
        .from('videos')
        .select('title, actors, categories')
        .or('title.ilike.%$query%,actors.cs.{"$query"},categories.cs.{"$query"}')
        .limit(15);

    final List<String> suggestions = [];
    final List<dynamic> data = response as List;

    for (var item in data) {
      // Add matching actors
      final actors = item['actors'] as List?;
      if (actors != null) {
        for (var actor in actors) {
          if (actor.toString().toLowerCase().contains(query.toLowerCase()) && !suggestions.contains(actor)) {
            suggestions.add(actor.toString());
          }
        }
      }
      
      // Add matching categories
      final cats = item['categories'] as List?;
      if (cats != null) {
        for (var cat in cats) {
          if (cat.toString().toLowerCase().contains(query.toLowerCase()) && !suggestions.contains(cat)) {
            suggestions.add(cat.toString());
          }
        }
      }
    }

    return suggestions.take(10).toList();
  }
}
