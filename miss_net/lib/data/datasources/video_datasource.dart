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
    // 1. Start with the filter builder
    var query = supabase.from('videos').select();

    // 2. Apply filters BEFORE ordering/ranging
    if (category != null && category != 'new') {
      query = query.contains('tags', [category]);
    }

    // 3. Apply Order and Range (Pagination)
    // Note: order() changes the return type to PostgrestTransformBuilder, so we chain it at the end.
    final response = await query
        .order('created_at', ascending: false)
        .range(offset, offset + limit - 1);

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
