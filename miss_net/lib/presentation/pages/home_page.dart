import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:url_launcher/url_launcher.dart';
import '../blocs/home/home_bloc.dart';
import '../pages/player_page.dart';
import '../widgets/video_card.dart';

import '../../injection_container.dart';
import '../blocs/search/search_bloc.dart';
import 'search_delegate.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    context.read<HomeBloc>().add(LoadRecentVideos());
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_isBottom) {
      context.read<HomeBloc>().add(LoadMoreVideos());
    }
  }

  bool get _isBottom {
    if (!_scrollController.hasClients) return false;
    final maxScroll = _scrollController.position.maxScrollExtent;
    final currentScroll = _scrollController.offset;
    return currentScroll >= (maxScroll * 0.9);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text("MissNet", style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: Colors.white),
            onPressed: () {
              final searchBloc = sl<SearchBloc>();
              showSearch(
                context: context,
                delegate: VideoSearchDelegate(searchBloc),
              ).then((_) => searchBloc.close());
            },
          )
        ],
      ),
      body: BlocBuilder<HomeBloc, HomeState>(
        builder: (context, state) {
          if (state is HomeLoading) {
            return const Center(child: CircularProgressIndicator(color: Colors.red));
          } else if (state is HomeError) {
            return Center(child: Text(state.message, style: const TextStyle(color: Colors.white)));
          } else if (state is HomeLoaded) {
            return RefreshIndicator(
              onRefresh: () async {
                context.read<HomeBloc>().add(LoadRecentVideos());
              },
              child: GridView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.all(10),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  childAspectRatio: 1.5,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                ),
                itemCount: state.hasReachedMax 
                    ? state.videos.length 
                    : state.videos.length + 1, // Add 1 for spinner
                itemBuilder: (context, index) {
                  if (index >= state.videos.length) {
                    return const Center(
                      child: SizedBox(
                        width: 24, 
                        height: 24, 
                        child: CircularProgressIndicator(color: Colors.red, strokeWidth: 2)
                      )
                    );
                  }
                  final video = state.videos[index];
                  return VideoCard(
                    video: video,
                    onTap: () {
                      if (kIsWeb) {
                         // On Web, open source URL
                         launchUrl(Uri.parse(video.sourceUrl));
                      } else {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => PlayerPage(video: video)),
                        );
                      }
                    },
                  );
                },
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}
