import 'dart:async';
import 'package:flutter/material.dart';
import 'package:animations/animations.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../../widgets/video_card.dart';
import '../player_page.dart';
import '../../../core/utils/responsive_grid.dart';

class FavoritesPage extends StatefulWidget {
  const FavoritesPage({super.key});

  @override
  State<FavoritesPage> createState() => _FavoritesPageState();
}

class _FavoritesPageState extends State<FavoritesPage> {
  final VideoRepository _repository = sl<VideoRepository>();
  List<Video> _videos = [];
  bool _isLoading = true;
  StreamSubscription? _subscription;

  @override
  void initState() {
    super.initState();
    _loadFavorites();
    _subscription = _repository.favoritesStream.listen((_) {
      _loadFavorites();
    });
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _loadFavorites() async {
    final result = await _repository.getFavorites();
    if (mounted) {
      result.fold(
        (failure) => setState(() => _isLoading = false),
        (videos) => setState(() {
          _videos = videos;
          _isLoading = false;
        }),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? Colors.black : Colors.grey[50],
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : CustomScrollView(
              slivers: [
                SliverAppBar.large(
                  expandedHeight: 120,
                  backgroundColor: Colors.transparent,
                  title: Text("My Favorites", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold, letterSpacing: -1)),
                ),
                if (_videos.isEmpty)
                  SliverFillRemaining(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.favorite_border_rounded, color: theme.disabledColor.withValues(alpha: 0.3), size: 80),
                          const SizedBox(height: 20),
                          Text("No favorites yet", style: TextStyle(color: theme.hintColor, fontSize: 16, fontWeight: FontWeight.w500)),
                        ],
                      ),
                    ),
                  )
                else
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 120),
                    sliver: SliverGrid(
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: ResponsiveGrid.getCrossAxisCount(context),
                        childAspectRatio: 1.4,
                        crossAxisSpacing: 16,
                        mainAxisSpacing: 16,
                      ),
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final video = _videos[index];
                          return OpenContainer(
                            transitionDuration: const Duration(milliseconds: 500),
                            openBuilder: (context, _) => PlayerPage(video: video),
                            closedElevation: 0,
                            closedColor: Colors.transparent,
                            closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                            closedBuilder: (context, openContainer) => VideoCard(
                              video: video,
                              onTap: () {
                                openContainer();
                                Future.delayed(const Duration(milliseconds: 500), _loadFavorites);
                              },
                            ),
                          );
                        },
                        childCount: _videos.length,
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}
