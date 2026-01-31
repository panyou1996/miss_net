import 'package:flutter/material.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../../widgets/video_card.dart';
import '../../widgets/video_skeleton.dart';
import '../player_page.dart';
import '../../../core/utils/responsive_grid.dart';

class CategoryDetailPage extends StatefulWidget {
  final String title;
  final String? category;
  final String? actor;

  const CategoryDetailPage({
    super.key, 
    required this.title, 
    this.category,
    this.actor,
  }) : assert(category != null || actor != null);

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
  bool _hasPaginationError = false;
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
    if (_isBottom && !_isFetchingMore && !_hasReachedMax && !_hasPaginationError) {
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

    final result = await _repository.getRecentVideos(
      limit: _limit, 
      offset: 0, 
      category: widget.category,
      actor: widget.actor,
    );
    
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
      _hasPaginationError = false;
    });

    final result = await _repository.getRecentVideos(
      limit: _limit, 
      offset: _videos.length, 
      category: widget.category,
      actor: widget.actor,
    );

    if (mounted) {
      result.fold(
        (failure) => setState(() { 
          _isFetchingMore = false; 
          _hasPaginationError = true;
        }),
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

      final theme = Theme.of(context);

      return Scaffold(

        // backgroundColor: theme.scaffoldBackgroundColor, // default

        appBar: AppBar(

          title: Text(widget.title, style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold)),

          backgroundColor: theme.appBarTheme.backgroundColor,

          iconTheme: theme.iconTheme,

        ),

        body: _isLoading

            ? GridView.builder(

                padding: const EdgeInsets.all(10),

                              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(

                                crossAxisCount: ResponsiveGrid.getCrossAxisCount(context),

                                childAspectRatio: 1.5,

                                crossAxisSpacing: 10,

                                mainAxisSpacing: 10,

                              ),              itemCount: 8,

                itemBuilder: (context, index) => const VideoCardSkeleton(),

              )

            : _error != null

                ? Center(child: Text(_error!, style: TextStyle(color: theme.colorScheme.onSurface)))

                : _videos.isEmpty

                    ? Center(child: Text("No videos found", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.5))))

                    : RefreshIndicator(

                        onRefresh: _loadVideos,

                        color: Colors.red,

                        child: GridView.builder(

                          controller: _scrollController,

                          padding: const EdgeInsets.all(10),

                                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(

                                          crossAxisCount: ResponsiveGrid.getCrossAxisCount(context),

                                          childAspectRatio: 1.5,

                                          crossAxisSpacing: 10,

                                          mainAxisSpacing: 10,

                                        ),                        itemCount: _hasReachedMax ? _videos.length : _videos.length + 2,

                          itemBuilder: (context, index) {

                            if (index >= _videos.length) {

                              if (_hasReachedMax) return const SizedBox.shrink();

                              

                              if (_hasPaginationError) {

                                return Center(

                                  child: IconButton(

                                    icon: Icon(Icons.refresh, color: theme.iconTheme.color),

                                    onPressed: _fetchMoreVideos,

                                  ),

                                );

                              }

  

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

  