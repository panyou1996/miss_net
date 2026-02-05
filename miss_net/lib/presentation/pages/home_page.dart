import 'dart:ui';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:animations/animations.dart';
import 'package:google_fonts/google_fonts.dart';
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
    final double opacity = (offset / 350).clamp(0.0, 1.0);
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
        ? SystemUiOverlayStyle.light
        : (_appBarOpacity > 0.6 ? SystemUiOverlayStyle.dark : SystemUiOverlayStyle.light);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: statusStyle,
      child: Scaffold(
        extendBodyBehindAppBar: true,
        appBar: PreferredSize(
          preferredSize: const Size.fromHeight(kToolbarHeight),
          child: ClipRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: _appBarOpacity * 30, sigmaY: _appBarOpacity * 30),
              child: AppBar(
                backgroundColor: isDark 
                    ? Colors.black.withValues(alpha: _appBarOpacity * 0.8)
                    : Colors.white.withValues(alpha: _appBarOpacity * 0.8),
                elevation: 0,
                centerTitle: true,
                title: Opacity(
                  opacity: _appBarOpacity,
                  child: Text(
                    "MissNet", 
                    style: GoogleFonts.playfairDisplay(
                      color: Colors.red, 
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.5,
                    )
                  ),
                ),
                actions: [
                  IconButton(
                    icon: Icon(Icons.search, color: (isDark || _appBarOpacity < 0.5) ? Colors.white : Colors.black),
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
        body: Stack(
          children: [
            // Background Typography Decoration
            Positioned(
              top: 400,
              right: -50,
              child: Opacity(
                opacity: 0.03,
                child: Text(
                  "EDITION",
                  style: GoogleFonts.playfairDisplay(
                    fontSize: 120,
                    fontWeight: FontWeight.w900,
                    color: theme.colorScheme.onSurface,
                  ),
                ),
              ),
            ),
            
            BlocBuilder<HomeBloc, HomeState>(
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
                      physics: const BouncingScrollPhysics(),
                      slivers: [
                        if (state.featuredVideo != null)
                          SliverToBoxAdapter(child: _buildImmersiveHero(state.featuredVideo!)),

                        SliverToBoxAdapter(
                          child: Transform.translate(
                            offset: const Offset(0, -50),
                            child: Column(
                              children: [
                                if (state.continueWatching.isNotEmpty)
                                  _buildContinueWatching(context, state.continueWatching),
                                
                                ...state.sections.asMap().entries.map((entry) {
                                  final idx = entry.key;
                                  final section = entry.value;
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
          ],
        ),
      ),
    );
  }

  Widget _buildLoading(ThemeData theme) {
    return CustomScrollView(
      slivers: [
        SliverAppBar(
          backgroundColor: theme.scaffoldBackgroundColor,
          title: Text("MissNet", style: GoogleFonts.playfairDisplay(color: Colors.red, fontWeight: FontWeight.bold)),
        ),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) => const SectionSkeleton(title: ""),
            childCount: 3,
          ),
        ),
      ],
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 48, 16, 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            title, 
            style: GoogleFonts.playfairDisplay(
              fontSize: 28, 
              fontWeight: FontWeight.w900, 
              letterSpacing: -0.8
            )
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.redAccent.withValues(alpha: 0.3)),
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Text(
              "ALL", 
              style: TextStyle(color: Colors.redAccent, fontSize: 10, fontWeight: FontWeight.w900, letterSpacing: 1.5)
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEditorialSection(BuildContext context, HomeSection section, bool isLandscape) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader(section.title),
        SizedBox(
          height: isLandscape ? 190 : 280,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            physics: const BouncingScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: section.videos.length,
            itemBuilder: (context, index) {
              final video = section.videos[index];
              final hTag = "${video.id}_${section.title}_$index";
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10.0),
                child: SizedBox(
                  width: isLandscape ? 300 : 180,
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 700),
                    openBuilder: (context, _) => PlayerPage(video: video, heroTag: hTag),
                    closedElevation: 0,
                    closedColor: Colors.transparent,
                    closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
              height: MediaQuery.of(context).size.height * 0.72,
              width: double.infinity,
              foregroundDecoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: [
                    Colors.black.withValues(alpha: 0.98),
                    Colors.black.withValues(alpha: 0.4),
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.15),
                  ],
                  stops: const [0.0, 0.4, 0.75, 1.0],
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
              bottom: 100,
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
                      fontSize: 42, 
                      fontWeight: FontWeight.w900,
                      height: 1.0,
                      letterSpacing: -1.2,
                      shadows: [Shadow(color: Colors.black45, blurRadius: 25, offset: Offset(0, 10))],
                    ),
                  ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      _heroBadge("MASTERCLASS"),
                      const SizedBox(width: 12),
                      Text(video.duration ?? "", style: const TextStyle(color: Colors.white70, fontSize: 14, fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                    ],
                  ),
                  const SizedBox(height: 36),
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: _heroButton(onPressed: openContainer, icon: Icons.play_arrow_rounded, label: "WATCH NOW", isPrimary: true),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 1,
                        child: _heroButton(onPressed: () {}, icon: Icons.add, label: "LIST", isPrimary: false),
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
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: Colors.white.withValues(alpha: 0.2), width: 0.5),
      ),
      child: Text(text, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w900, letterSpacing: 1)),
    );
  }

  Widget _heroButton({required VoidCallback onPressed, required IconData icon, required String label, required bool isPrimary}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
        child: InkWell(
          onTap: onPressed,
          child: Container(
            height: 54,
            decoration: BoxDecoration(
              color: isPrimary ? Colors.white : Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(16),
              border: isPrimary ? null : Border.all(color: Colors.white.withValues(alpha: 0.15)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, color: isPrimary ? Colors.black : Colors.white, size: 26),
                const SizedBox(width: 10),
                Text(label, style: TextStyle(color: isPrimary ? Colors.black : Colors.white, fontSize: 14, fontWeight: FontWeight.w900, letterSpacing: 1.2)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContinueWatching(BuildContext context, List<Video> videos) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 16, 16),
          child: Text(
            "REPLAY",
            style: TextStyle(color: Colors.redAccent.withValues(alpha: 0.8), fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 3.0),
          ),
        ),
        SizedBox(
          height: 150,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: videos.length,
            itemBuilder: (context, index) {
              final video = videos[index];
              final hTag = "${video.id}_cw_$index";
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10.0),
                child: SizedBox(
                  width: 200,
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 600),
                    openBuilder: (context, _) => PlayerPage(video: video, heroTag: hTag),
                    closedElevation: 0,
                    closedColor: Colors.transparent,
                    closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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