import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/video_model.dart';

abstract class VideoDataSource {
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0});
  Future<List<VideoModel>> searchVideos(String query);
}

class SupabaseVideoDataSourceImpl implements VideoDataSource {
  final SupabaseClient supabase;

  SupabaseVideoDataSourceImpl(this.supabase);

  @override
  Future<List<VideoModel>> getRecentVideos({int limit = 20, int offset = 0}) async {
    final response = await supabase
        .from('videos')
        .select()
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
