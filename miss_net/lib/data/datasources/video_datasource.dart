import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/video_model.dart';

abstract class VideoDataSource {
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category, String? actor});
  Future<List<VideoModel>> searchVideos(String query);
  Future<List<String>> getSearchSuggestions(String query);
  Future<List<VideoModel>> getRelatedVideos(VideoModel video);
  Future<List<String>> getPopularActors();
  Future<List<String>> getPopularTags();
}

class SupabaseVideoDataSourceImpl implements VideoDataSource {
  final SupabaseClient supabase;
  String _baseUrl = 'https://missav.ws';

  SupabaseVideoDataSourceImpl(this.supabase) {
    _loadConfig();
  }

  Future<void> _loadConfig() async {
    try {
      final res = await supabase.from('app_config').select('value').eq('key', 'base_url').single();
      if (res['value'] != null) {
        _baseUrl = res['value'] as String;
        debugPrint("Config: Base URL updated to $_baseUrl");
      }
    } catch (e) {
      debugPrint("Config Error: Using fallback URL");
    }
  }

  @override
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category, String? actor}) async {
    var queryBuilder = supabase.from('videos').select().eq('is_active', true);

    if (category != null && category != 'new') {
      queryBuilder = queryBuilder.or('tags.cs.{"$category"},categories.cs.{"$category"}');
    }
    
    if (actor != null) {
      queryBuilder = queryBuilder.contains('actors', [actor]);
    }

    final response = await queryBuilder
        .order('created_at', ascending: false)
        .range(offset, offset + limit - 1);

    return _mapVideos(response as List);
  }

  @override
  Future<List<VideoModel>> searchVideos(String query) async {
    // Optimized Full Text Search on 'title'
    // Ensure you have enabled the text search index in Supabase for the 'title' column.
    // e.g. create index on videos using gin(to_tsvector('english', title));
    
    final response = await supabase
        .from('videos')
        .select()
        .textSearch('title', "'$query'")
        .order('created_at', ascending: false)
        .limit(50);

    return _mapVideos(response as List);
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

  @override
  Future<List<VideoModel>> getRelatedVideos(VideoModel video) async {
    List<dynamic> data = [];

    // Priority 1: Same Actors (Strongest signal)
    if (video.actors != null && video.actors!.isNotEmpty) {
      try {
        final res = await supabase
            .from('videos')
            .select()
            .overlaps('actors', video.actors!)
            .neq('id', video.id)
            .limit(12);
        data.addAll(res as List);
      } catch (e) {
        debugPrint("Related Videos (Actors) Error: $e");
      }
    }

    // Priority 2: Same Categories (Fill if needed)
    if (data.length < 6 && video.categories != null && video.categories!.isNotEmpty) {
      try {
        final res = await supabase
            .from('videos')
            .select()
            .overlaps('categories', video.categories!)
            .neq('id', video.id)
            .limit(12);
        data.addAll(res as List);
      } catch (e) {
         debugPrint("Related Videos (Cats) Error: $e");
      }
    }

    // Deduplicate by ID
    final uniqueMap = {for (var item in data) item['id']: item};
    return _mapVideos(uniqueMap.values.toList());
  }

  @override
  Future<List<String>> getPopularActors() async {
    try {
      final res = await supabase.rpc('get_popular_actors', params: {'limit_count': 20});
      return (res as List).map((e) => e['actor'] as String).toList();
    } catch (e) {
      debugPrint("RPC Error Actors: $e");
      return [];
    }
  }

  @override
  Future<List<String>> getPopularTags() async {
    try {
      final res = await supabase.rpc('get_popular_tags', params: {'limit_count': 30});
      return (res as List).map((e) => e['tag'] as String).toList();
    } catch (e) {
      debugPrint("RPC Error Tags: $e");
      return [];
    }
  }

  List<VideoModel> _mapVideos(List<dynamic> data) {
    final List<VideoModel> videos = [];
    for (var item in data) {
      try {
        videos.add(VideoModel.fromJson(item));
      } catch (e) {
        debugPrint("Error parsing video: $e. Data: $item");
      }
    }
    return videos;
  }
}
