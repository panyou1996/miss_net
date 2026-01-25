import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../blocs/home/home_bloc.dart';
import '../pages/player_page.dart';
import '../widgets/video_card.dart';
import '../../domain/entities/video.dart';
import '../../domain/entities/home_section.dart';
import 'category/category_detail_page.dart';

import '../../injection_container.dart';
import '../blocs/search/search_bloc.dart';
import 'search_delegate.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  @override
  void initState() {
    super.initState();
    context.read<HomeBloc>().add(LoadRecentVideos());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
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
              child: CustomScrollView(
                slivers: [
                  // 1. Transparent AppBar
                  SliverAppBar(
                    expandedHeight: 0,
                    floating: true,
                    backgroundColor: Colors.black.withOpacity(0.5),
                    title: const Text("MissNet", style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
                    actions: [
                      IconButton(
                        icon: const Icon(Icons.search, color: Colors.white),
                        onPressed: () {
                          final searchBloc = sl<SearchBloc>();
                          showSearch(context: context, delegate: VideoSearchDelegate(searchBloc))
                              .then((_) => searchBloc.close());
                        },
                      )
                    ],
                  ),

                  // 2. Hero Banner
                  if (state.featuredVideo != null)
                    SliverToBoxAdapter(
                      child: _buildHeroBanner(state.featuredVideo!),
                    ),

                  // 3. Horizontal Sections
                  SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final section = state.sections[index];
                        return _buildSection(section);
                      },
                      childCount: state.sections.length,
                    ),
                  ),
                  
                  const SliverToBoxAdapter(child: SizedBox(height: 50)),
                ],
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }

  Widget _buildHeroBanner(Video video) {
    return GestureDetector(
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: video))),
      child: Stack(
        children: [
          Container(
            height: 400,
            width: double.infinity,
            foregroundDecoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.bottomCenter,
                end: Alignment.topCenter,
                colors: [
                  Colors.black,
                  Colors.black.withOpacity(0.5),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.3, 0.6],
              ),
            ),
            child: video.coverUrl != null
                ? CachedNetworkImage(imageUrl: video.coverUrl!, fit: BoxFit.cover)
                : Container(color: Colors.grey[900]),
          ),
          Positioned(
            bottom: 40,
            left: 20,
            right: 20,
            child: Column(
              children: [
                Text(
                  video.title,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 15),
                ElevatedButton.icon(
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: video))),
                  icon: const Icon(Icons.play_arrow),
                  label: const Text("Play Now"),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: Colors.black,
                    padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 10),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSection(HomeSection section) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: InkWell(
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => CategoryDetailPage(title: section.title, category: section.category),
                ),
              );
            },
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  section.title,
                  style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const Icon(Icons.arrow_forward_ios, color: Colors.grey, size: 16),
              ],
            ),
          ),
        ),
        SizedBox(
          height: 160,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            itemCount: section.videos.length,
            itemBuilder: (context, index) {
              final video = section.videos[index];
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: SizedBox(
                  width: 200,
                  child: VideoCard(
                    video: video,
                    onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: video))),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
