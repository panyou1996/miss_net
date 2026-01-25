import 'package:flutter/material.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../../widgets/video_card.dart';
import '../player_page.dart';

class CategoryDetailPage extends StatefulWidget {
  final String title;
  final String category;

  const CategoryDetailPage({super.key, required this.title, required this.category});

  @override
  State<CategoryDetailPage> createState() => _CategoryDetailPageState();
}

class _CategoryDetailPageState extends State<CategoryDetailPage> {
  final VideoRepository _repository = sl<VideoRepository>();
  List<Video> _videos = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadVideos();
  }

  Future<void> _loadVideos() async {
    final result = await _repository.getRecentVideos(limit: 50, category: widget.category);
    if (mounted) {
      result.fold(
        (failure) => setState(() { _error = failure.message; _isLoading = false; }),
        (videos) => setState(() { _videos = videos; _isLoading = false; }),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(widget.title, style: const TextStyle(color: Colors.white)),
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : _error != null
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.white)))
              : _videos.isEmpty
                  ? const Center(child: Text("No videos found", style: TextStyle(color: Colors.white54)))
                  : GridView.builder(
                      padding: const EdgeInsets.all(10),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 2,
                        childAspectRatio: 1.5,
                        crossAxisSpacing: 10,
                        mainAxisSpacing: 10,
                      ),
                      itemCount: _videos.length,
                      itemBuilder: (context, index) {
                        return VideoCard(
                          video: _videos[index],
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => PlayerPage(video: _videos[index])),
                          ),
                        );
                      },
                    ),
    );
  }
}