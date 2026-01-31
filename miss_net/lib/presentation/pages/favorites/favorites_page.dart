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
    return Scaffold(
      appBar: AppBar(
        title: Text("My Favorites", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold)),
        backgroundColor: theme.appBarTheme.backgroundColor,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : _videos.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.favorite_border, color: theme.disabledColor, size: 64),
                      const SizedBox(height: 16),
                      Text("Your favorites list is empty", style: TextStyle(color: theme.hintColor)),
                    ],
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.all(10),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: ResponsiveGrid.getCrossAxisCount(context),
                    childAspectRatio: 1.5,
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                  ),
                  itemCount: _videos.length,
                  itemBuilder: (context, index) {
                    return OpenContainer(
                      transitionDuration: const Duration(milliseconds: 500),
                      openBuilder: (context, _) => PlayerPage(video: _videos[index]),
                      closedElevation: 0,
                      closedColor: theme.scaffoldBackgroundColor,
                      closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      closedBuilder: (context, openContainer) => VideoCard(
                        video: _videos[index],
                        onTap: () {
                          openContainer();
                          // Reload after return in case un-favorited
                          Future.delayed(const Duration(milliseconds: 500), _loadFavorites);
                        },
                      ),
                    );
                  },
                ),
    );
  }
}
