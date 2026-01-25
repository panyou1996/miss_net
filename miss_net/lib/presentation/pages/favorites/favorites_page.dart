import 'package:flutter/material.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../../widgets/video_card.dart';
import '../player_page.dart';

class FavoritesPage extends StatefulWidget {
  const FavoritesPage({super.key});

  @override
  State<FavoritesPage> createState() => _FavoritesPageState();
}

class _FavoritesPageState extends State<FavoritesPage> {
  final VideoRepository _repository = sl<VideoRepository>();
  List<Video> _videos = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadFavorites();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _loadFavorites();
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
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text("My Favorites", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.black,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : _videos.isEmpty
              ? const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.favorite_border, color: Colors.grey, size: 64),
                      SizedBox(height: 16),
                      Text("Your favorites list is empty", style: TextStyle(color: Colors.white54)),
                    ],
                  ),
                )
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
                      onTap: () async {
                        await Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => PlayerPage(video: _videos[index])),
                        );
                        _loadFavorites();
                      },
                    );
                  },
                ),
    );
  }
}
