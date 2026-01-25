import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/video_model.dart';

abstract class VideoDataSource {
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category});
  Future<List<VideoModel>> searchVideos(String query);
}

class SupabaseVideoDataSourceImpl implements VideoDataSource {
  final SupabaseClient supabase;

  SupabaseVideoDataSourceImpl(this.supabase);

  @override
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0, String? category}) async {
    var query = supabase
        .from('videos')
        .select()
        .order('created_at', ascending: false)
        .range(offset, offset + limit - 1);

    if (category != null && category != 'new') {
      // 'new' is treated as "all/recent" or specifically 'new' tag? 
      // Let's assume 'new' means no filter (show everything sorted by date), 
      // OR we can filter by 'new' tag if the scraper adds it.
      // Given the scraper adds 'new', 'uncensored', etc., we should filter.
      // BUT for the "Home" tab, we might want everything.
      // Let's implement strict filtering if category is provided.
      query = query.contains('tags', [category]);
    }

    final response = await query;

    return (response as List).map((e) => VideoModel.fromJson(e)).toList();
  }

  @override
  Future<List<VideoModel>> searchVideos(String query) async {
    final response = await supabase
        .from('videos')
        .select()
        .ilike('title', '%$query%') // Case-insensitive search
        .order('created_at', ascending: false)
        .limit(50);

    return (response as List).map((e) => VideoModel.fromJson(e)).toList();
  }
}
