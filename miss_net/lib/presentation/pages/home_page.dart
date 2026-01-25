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
  final List<String> _categories = ['new', 'monthly_hot', 'weekly_hot', 'uncensored'];
  final Map<String, String> _categoryLabels = {
    'new': 'Recent Updates',
    'monthly_hot': 'Monthly Hot',
    'weekly_hot': 'Weekly Hot',
    'uncensored': 'Uncensored',
  };
  bool _showBackToTop = false;

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
    if (!_scrollController.hasClients) return; // Guard clause

    if (_isBottom) {
      context.read<HomeBloc>().add(LoadMoreVideos());
    }
    
    // Show/Hide Back to Top Button
    if (_scrollController.offset > 500 && !_showBackToTop) {
      setState(() => _showBackToTop = true);
    } else if (_scrollController.offset <= 500 && _showBackToTop) {
      setState(() => _showBackToTop = false);
    }
  }

  bool get _isBottom {
    if (!_scrollController.hasClients) return false;
    final maxScroll = _scrollController.position.maxScrollExtent;
    final currentScroll = _scrollController.offset;
    return currentScroll >= (maxScroll * 0.9);
  }

  void _scrollToTop() {
    if (!_scrollController.hasClients) return; // Guard clause
    
    _scrollController.animateTo(
      0,
      duration: const Duration(milliseconds: 500),
      curve: Curves.easeInOut,
    );
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
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(50.0),
          child: Container(
            height: 50,
            alignment: Alignment.centerLeft,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              itemCount: _categories.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final category = _categories[index];
                return BlocBuilder<HomeBloc, HomeState>(
                  buildWhen: (previous, current) => 
                    current is HomeLoaded && previous is HomeLoaded 
                    ? previous.selectedCategory != current.selectedCategory 
                    : true,
                  builder: (context, state) {
                    final isSelected = state is HomeLoaded && state.selectedCategory == category;
                    return ChoiceChip(
                      label: Text(_categoryLabels[category] ?? category),
                      selected: isSelected,
                      onSelected: (selected) {
                        if (selected) {
                          context.read<HomeBloc>().add(ChangeCategory(category));
                          _scrollToTop();
                        }
                      },
                      selectedColor: Colors.red,
                      backgroundColor: Colors.grey[900],
                      labelStyle: TextStyle(
                        color: isSelected ? Colors.white : Colors.grey[400],
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(20),
                        side: BorderSide.none,
                      ),
                      showCheckmark: false,
                    );
                  },
                );
              },
            ),
          ),
        ),
      ),
      body: BlocBuilder<HomeBloc, HomeState>(
        builder: (context, state) {
          if (state is HomeLoading) {
            return const Center(child: CircularProgressIndicator(color: Colors.red));
          } else if (state is HomeError) {
            return Center(child: Text(state.message, style: const TextStyle(color: Colors.white)));
          } else if (state is HomeLoaded) {
            if (state.videos.isEmpty) {
               return const Center(
                 child: Text("No videos found in this category.", style: TextStyle(color: Colors.white54))
               );
            }
            return RefreshIndicator(
              color: Colors.red,
              backgroundColor: Colors.grey[900],
              onRefresh: () async {
                context.read<HomeBloc>().add(ChangeCategory(state.selectedCategory));
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
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => PlayerPage(video: video)),
                      );
                    },
                  );
                },
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
      floatingActionButton: _showBackToTop
          ? FloatingActionButton(
              onPressed: _scrollToTop,
              backgroundColor: Colors.red,
              mini: true,
              child: const Icon(Icons.arrow_upward, color: Colors.white),
            )
          : null,
    );
  }
}
