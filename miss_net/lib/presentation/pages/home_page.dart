import 'dart:ui';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:animations/animations.dart';
import '../blocs/home/home_bloc.dart';
import '../pages/player_page.dart';
import '../widgets/video_card.dart';
import '../widgets/video_skeleton.dart';
import '../../domain/entities/video.dart';
import '../../domain/entities/home_section.dart';
import '../../core/utils/image_proxy.dart';
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
  final ScrollController _scrollController = ScrollController();
  double _appBarOpacity = 0.0;

  @override
  void initState() {
    super.initState();
    context.read<HomeBloc>().add(LoadRecentVideos());
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    final double offset = _scrollController.offset;
    final double opacity = (offset / 300).clamp(0.0, 1.0);
    if (opacity != _appBarOpacity) {
      setState(() => _appBarOpacity = opacity);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    // Fix Status Bar Contrast Dynamically
    final statusStyle = isDark 
        ? (_appBarOpacity > 0.5 ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.light)
        : (_appBarOpacity > 0.5 ? SystemUiOverlayStyle.dark : SystemUiOverlayStyle.light);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: statusStyle,
      child: Scaffold(
        extendBodyBehindAppBar: true,
        appBar: PreferredSize(
          preferredSize: const Size.fromHeight(kToolbarHeight),
          child: ClipRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: _appBarOpacity * 25, sigmaY: _appBarOpacity * 25),
              child: AppBar(
                backgroundColor: isDark 
                    ? Colors.black.withValues(alpha: _appBarOpacity * 0.75)
                    : Colors.white.withValues(alpha: _appBarOpacity * 0.75),
                elevation: 0,
                centerTitle: true,
                title: Opacity(
                  opacity: _appBarOpacity,
                  child: Text(
                    "MissNet", 
                    style: GoogleFonts.playfairDisplay(
                      color: Colors.red, 
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.2,
                    )
                  ),
                ),
                actions: [
                  IconButton(
                    icon: Icon(Icons.search, color: _appBarOpacity > 0.5 ? theme.iconTheme.color : Colors.white),
                    onPressed: () {
                      final searchBloc = sl<SearchBloc>();
                      showSearch(context: context, delegate: VideoSearchDelegate(searchBloc))
                          .then((_) => searchBloc.close());
                    },
                  )
                ],
              ),
            ),
          ),
        ),
        body: BlocBuilder<HomeBloc, HomeState>(
          builder: (context, state) {
            if (state is HomeLoading) {
              return _buildLoading(theme);
            } else if (state is HomeError) {
              return Center(child: Text(state.message, style: TextStyle(color: theme.colorScheme.error)));
            } else if (state is HomeLoaded) {
              return RefreshIndicator(
                onRefresh: () async {
                  context.read<HomeBloc>().add(LoadRecentVideos());
                },
                child: CustomScrollView(
                  controller: _scrollController,
                  slivers: [
                    // 1. Hero Banner
                    if (state.featuredVideo != null)
                      SliverToBoxAdapter(
                        child: _buildImmersiveHero(state.featuredVideo!),
                      ),

                    // 2. Content Sections with Editorial Rhythm
                    SliverToBoxAdapter(
                      child: Transform.translate(
                        offset: const Offset(0, -40),
                        child: Column(
                          children: [
                            if (state.continueWatching.isNotEmpty)
                              _buildContinueWatching(context, state.continueWatching),
                            
                            // Use different scales for different sections
                            ...state.sections.asMap().entries.map((entry) {
                              final idx = entry.key;
                              final section = entry.value;
                              // Magazine Rhythm: alternate between landscape and portrait
                              final bool isLandscape = idx % 2 == 0;
                              return _buildEditorialSection(context, section, isLandscape);
                            }),
                          ],
                        ),
                      ),
                    ),
                    
                    const SliverToBoxAdapter(child: SizedBox(height: 120)),
                  ],
                ),
              );
            }
            return const SizedBox.shrink();
          },
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 32, 16, 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            title, 
            style: GoogleFonts.playfairDisplay(
              fontSize: 26, 
              fontWeight: FontWeight.w900, 
              letterSpacing: -0.5
            )
          ),
          const Spacer(),
          Text(
            "View All", 
            style: TextStyle(
              color: Colors.redAccent, 
              fontSize: 12, 
              fontWeight: FontWeight.bold,
              letterSpacing: 1
            )
          ),
        ],
      ),
    );
  }

  Widget _buildEditorialSection(BuildContext context, HomeSection section, bool isLandscape) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader(section.title),
        SizedBox(
          height: isLandscape ? 180 : 260, // Vary height
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: section.videos.length,
            itemBuilder: (context, index) {
              final video = section.videos[index];
              final hTag = "${video.id}_${section.title}_$index";
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: SizedBox(
                  width: isLandscape ? 280 : 170, // Vary width
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 600),
                    openBuilder: (context, _) => PlayerPage(video: video, heroTag: hTag),
                    closedElevation: 0,
                    closedColor: Colors.transparent,
                    closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    closedBuilder: (context, openContainer) => VideoCard(
                      video: video,
                      heroTag: hTag,
                      onTap: openContainer,
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildImmersiveHero(Video video) {
    final heroTag = "${video.id}_banner";
    final theme = Theme.of(context);
    
    return OpenContainer(
      transitionDuration: const Duration(milliseconds: 800),
      openBuilder: (context, _) => PlayerPage(video: video, heroTag: heroTag),
      closedElevation: 0,
      closedColor: theme.scaffoldBackgroundColor,
      closedBuilder: (context, openContainer) => GestureDetector(
        onTap: openContainer,
        child: Stack(
          children: [
            Container(
              height: MediaQuery.of(context).size.height * 0.7,
              width: double.infinity,
              foregroundDecoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: [
                    Colors.black.withValues(alpha: 0.98),
                    Colors.black.withValues(alpha: 0.4),
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.1),
                  ],
                  stops: const [0.0, 0.45, 0.7, 1.0],
                ),
              ),
              child: Hero(
                tag: heroTag,
                child: video.coverUrl != null
                    ? CachedNetworkImage(imageUrl: ImageProxy.getUrl(video.coverUrl!), fit: BoxFit.cover)
                    : Container(color: Colors.grey[900]),
              ),
            ),
            Positioned(
              bottom: 80,
              left: 24,
              right: 24,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    video.title,
                    maxLines: 2,
                    style: GoogleFonts.playfairDisplay(
                      color: Colors.white, 
                      fontSize: 38, // Massive Editorial Title
                      fontWeight: FontWeight.w900,
                      height: 1.0,
                      letterSpacing: -1,
                      shadows: [Shadow(color: Colors.black45, blurRadius: 20, offset: Offset(0, 10))],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      _heroBadge("ULTRA HD"),
                      const SizedBox(width: 8),
                      Text(video.duration ?? "", style: const TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 1)),
                    ],
                  ),
                  const SizedBox(height: 32),
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: _heroButton(
                          onPressed: openContainer,
                          icon: Icons.play_arrow_rounded,
                          label: "Watch Now",
                          isPrimary: true,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 1,
                        child: _heroButton(
                          onPressed: () {},
                          icon: Icons.add,
                          label: "List",
                          isPrimary: false,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _heroBadge(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: Colors.white.withValues(alpha: 0.2)),
      ),
      child: Text(text, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
    );
  }

  Widget _heroButton({required VoidCallback onPressed, required IconData icon, required String label, required bool isPrimary}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: InkWell(
          onTap: onPressed,
          child: Container(
            height: 48,
            decoration: BoxDecoration(
              color: isPrimary ? Colors.white : Colors.white.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(14),
              border: isPrimary ? null : Border.all(color: Colors.white.withValues(alpha: 0.1)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, color: isPrimary ? Colors.black : Colors.white, size: 24),
                const SizedBox(width: 8),
                Text(label, style: TextStyle(color: isPrimary ? Colors.black : Colors.white, fontSize: 15, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSection(BuildContext context, HomeSection section) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
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
                  style: TextStyle(
                    color: theme.colorScheme.onSurface, 
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
                Icon(Icons.arrow_forward_ios, color: theme.colorScheme.onSurface.withValues(alpha: 0.4), size: 14),
              ],
            ),
          ),
        ),
        SizedBox(
          height: 160,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: section.videos.length,
            itemBuilder: (context, index) {
              final video = section.videos[index];
              final hTag = "${video.id}_${section.title}_$index";
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: SizedBox(
                  width: 200,
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 500),
                    openBuilder: (context, _) => PlayerPage(video: video, heroTag: hTag),
                    closedElevation: 0,
                    closedColor: theme.scaffoldBackgroundColor,
                    closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    closedBuilder: (context, openContainer) => VideoCard(
                      video: video,
                      heroTag: hTag,
                      onTap: openContainer,
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildContinueWatching(BuildContext context, List<Video> videos) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            "Continue Watching",
            style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.bold),
          ),
        ),
        SizedBox(
          height: 140,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            itemCount: videos.length,
            itemBuilder: (context, index) {
              final video = videos[index];
              final hTag = "${video.id}_cw_$index";
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: SizedBox(
                  width: 180,
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 500),
                    openBuilder: (context, _) => PlayerPage(video: video, heroTag: hTag),
                    closedElevation: 0,
                    closedColor: theme.scaffoldBackgroundColor,
                    closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    closedBuilder: (context, openContainer) => VideoCard(
                      video: video,
                      heroTag: hTag,
                      onTap: openContainer,
                    ),
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