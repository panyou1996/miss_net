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
  final ScrollController _scrollController = ScrollController();
  
  List<Video> _videos = [];
  bool _isLoading = true;
  bool _isFetchingMore = false;
  bool _hasReachedMax = false;
  String? _error;
  
  static const int _limit = 20;

  @override
  void initState() {
    super.initState();
    _loadVideos();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_isBottom && !_isFetchingMore && !_hasReachedMax) {
      _fetchMoreVideos();
    }
  }

  bool get _isBottom {
    if (!_scrollController.hasClients) return false;
    final maxScroll = _scrollController.position.maxScrollExtent;
    final currentScroll = _scrollController.offset;
    return currentScroll >= (maxScroll * 0.9);
  }

  Future<void> _loadVideos() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    final result = await _repository.getRecentVideos(limit: _limit, offset: 0, category: widget.category);
    
    if (mounted) {
      result.fold(
        (failure) => setState(() { _error = failure.message; _isLoading = false; }),
        (videos) => setState(() { 
          _videos = videos; 
          _isLoading = false; 
          _hasReachedMax = videos.length < _limit;
        }),
      );
    }
  }

  Future<void> _fetchMoreVideos() async {
    setState(() {
      _isFetchingMore = true;
    });

    final result = await _repository.getRecentVideos(
      limit: _limit, 
      offset: _videos.length, 
      category: widget.category
    );

    if (mounted) {
      result.fold(
        (failure) => setState(() { _isFetchingMore = false; }),
        (newVideos) => setState(() {
          if (newVideos.isEmpty) {
            _hasReachedMax = true;
          } else {
            _videos.addAll(newVideos);
            _hasReachedMax = newVideos.length < _limit;
          }
          _isFetchingMore = false;
        }),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(widget.title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : _error != null
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.white)))
              : _videos.isEmpty
                  ? const Center(child: Text("No videos found", style: TextStyle(color: Colors.white54)))
                  : RefreshIndicator(
                      onRefresh: _loadVideos,
                      color: Colors.red,
                      child: GridView.builder(
                        controller: _scrollController,
                        padding: const EdgeInsets.all(10),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          childAspectRatio: 1.5,
                          crossAxisSpacing: 10,
                          mainAxisSpacing: 10,
                        ),
                        itemCount: _hasReachedMax ? _videos.length : _videos.length + 2, // +2 for bottom space/loader
                        itemBuilder: (context, index) {
                          if (index >= _videos.length) {
                            if (_hasReachedMax) return const SizedBox.shrink();
                            return const Center(
                              child: Padding(
                                padding: EdgeInsets.all(8.0),
                                child: CircularProgressIndicator(color: Colors.red, strokeWidth: 2),
                              ),
                            );
                          }
                          return VideoCard(
                            video: _videos[index],
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => PlayerPage(video: _videos[index])),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}
