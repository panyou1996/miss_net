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
  late PageController _heroController;
  double _appBarOpacity = 0.0;
  double _heroPage = 0.0;

  @override
  void initState() {
    super.initState();
    context.read<HomeBloc>().add(LoadRecentVideos());
    _scrollController.addListener(_onScroll);
    _heroController = PageController(viewportFraction: 0.9);
    _heroController.addListener(() {
      setState(() => _heroPage = _heroController.page ?? 0.0);
    });
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    _heroController.dispose();
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

    final statusStyle = isDark 
        ? SystemUiOverlayStyle.light
        : (_appBarOpacity > 0.6 ? SystemUiOverlayStyle.dark : SystemUiOverlayStyle.light);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: statusStyle,
      child: Scaffold(
        extendBodyBehindAppBar: true,
        body: BlocBuilder<HomeBloc, HomeState>(
          builder: (context, state) {
            if (state is HomeLoading) {
              return _buildLoading(theme);
            } else if (state is HomeError) {
              return Center(child: Text(state.message, style: TextStyle(color: theme.colorScheme.error)));
            } else if (state is HomeLoaded) {
              final featuredVideos = state.sections.isNotEmpty 
                  ? state.sections.first.videos.take(5).toList().cast<Video>()
                  : <Video>[];

              return RefreshIndicator(
                onRefresh: () async {
                  context.read<HomeBloc>().add(LoadRecentVideos());
                },
                child: CustomScrollView(
                  controller: _scrollController,
                  physics: const BouncingScrollPhysics(),
                  slivers: [
                    SliverAppBar.large(
                      expandedHeight: 140,
                      backgroundColor: isDark ? Colors.black.withValues(alpha: 0.8) : Colors.white.withValues(alpha: 0.8),
                      stretch: true,
                      elevation: 0,
                      pinned: true,
                      flexibleSpace: FlexibleSpaceBar(
                        centerTitle: false,
                        titlePadding: const EdgeInsets.fromLTRB(20, 0, 16, 16),
                        title: Text(
                          "MissNet", 
                          style: GoogleFonts.playfairDisplay(
                            color: Colors.red, 
                            fontWeight: FontWeight.w900,
                            fontSize: 28,
                            letterSpacing: -1,
                          )
                        ),
                      ),
                      actions: [
                        IconButton(
                          icon: Icon(Icons.search, color: theme.iconTheme.color),
                          onPressed: () {
                            final searchBloc = sl<SearchBloc>();
                            showSearch(context: context, delegate: VideoSearchDelegate(searchBloc))
                                .then((_) => searchBloc.close());
                          },
                        ),
                        const SizedBox(width: 8),
                      ],
                    ),

                    if (featuredVideos.isNotEmpty)
                      SliverToBoxAdapter(
                        child: _buildM3Carousel(featuredVideos),
                      ),

                    SliverToBoxAdapter(
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

  Widget _buildLoading(ThemeData theme) {
    return CustomScrollView(
      slivers: [
        SliverAppBar.large(
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

  Widget _buildM3Carousel(List<Video> videos) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.65,
      margin: const EdgeInsets.only(bottom: 20),
      child: PageView.builder(
        controller: _heroController,
        itemCount: videos.length,
        itemBuilder: (context, index) {
          final double relativePosition = index - _heroPage;
          return _ParallaxCard(
            video: videos[index],
            offset: relativePosition,
          );
        },
      ),
    );
  }

  Widget _buildSectionHeader(String title, String category) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 32, 16, 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            title, 
            style: GoogleFonts.playfairDisplay(fontSize: 24, fontWeight: FontWeight.w900, letterSpacing: -0.5)
          ),
          TextButton(
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: title, category: category))),
            style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
            child: const Text("ALL", style: TextStyle(color: Colors.redAccent, fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 1.5)),
          ),
        ],
      ),
    );
  }

  Widget _buildEditorialSection(BuildContext context, HomeSection section, bool isLandscape) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader(section.title, section.category),
        SizedBox(
          height: 200, // Fixed height for 16:9 cards including text
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
                  width: 260, // Fixed width for 16:9 aspect ratio
                  child: OpenContainer(
                    transitionDuration: const Duration(milliseconds: 700),
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

  Widget _buildContinueWatching(BuildContext context, List<Video> videos) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 24, 16, 12),
          child: Text("REPLAY", style: TextStyle(color: Colors.redAccent.withValues(alpha: 0.8), fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 3)),
        ),
        SizedBox(
          height: 140,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: videos.length,
            itemBuilder: (context, index) {
              final video = videos[index];
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: SizedBox(
                  width: 180,
                  child: VideoCard(video: video, onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlayerPage(video: video)))),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ParallaxCard extends StatelessWidget {
  final Video video;
  final double offset;

  const _ParallaxCard({required this.video, required this.offset});

  @override
  Widget build(BuildContext context) {
    final double scale = 1.0 - (offset.abs() * 0.1); // Scale from 1.0 to 0.9
    final double parallaxOffset = offset * 100; // Image moves 100px relative to container

    final heroTag = "${video.id}_hero_parallax";

    return Transform.scale(
      scale: scale,
      child: OpenContainer(
        transitionDuration: const Duration(milliseconds: 800),
        openBuilder: (context, _) => PlayerPage(video: video, heroTag: heroTag),
        closedElevation: 10,
        closedColor: Colors.transparent,
        closedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        closedBuilder: (context, openContainer) => ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Parallax Image Layer
              Transform.translate(
                offset: Offset(parallaxOffset, 0),
                child: Transform.scale(
                  scale: 1.2, // Zoom in image to allow for parallax movement without gaps
                  child: CachedNetworkImage(
                    imageUrl: ImageProxy.getUrl(video.coverUrl ?? ""),
                    fit: BoxFit.cover,
                  ),
                ),
              ),
              
              // Gradient Overlay
              Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [Colors.black.withValues(alpha: 0.9), Colors.black.withValues(alpha: 0.2), Colors.transparent],
                    stops: const [0.0, 0.5, 0.8],
                  ),
                ),
              ),

              // Content Layer (Static relative to card)
              Positioned(
                bottom: 30,
                left: 20,
                right: 20,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      video.title,
                      maxLines: 2,
                      style: GoogleFonts.playfairDisplay(
                        color: Colors.white, 
                        fontSize: 28, 
                        fontWeight: FontWeight.w900,
                        shadows: [const Shadow(blurRadius: 10, offset: Offset(0, 5))],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(child: _heroButton(onPressed: openContainer, icon: Icons.play_arrow_rounded, label: "PLAY", isPrimary: true)),
                        const SizedBox(width: 12),
                        Expanded(child: _heroButton(onPressed: () {}, icon: Icons.add, label: "LIST", isPrimary: false)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _heroButton({required VoidCallback onPressed, required IconData icon, required String label, required bool isPrimary}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: InkWell(
          onTap: onPressed,
          child: Container(
            height: 44,
            decoration: BoxDecoration(
              color: isPrimary ? Colors.white : Colors.white.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, color: isPrimary ? Colors.black : Colors.white, size: 20),
                const SizedBox(width: 6),
                Text(label, style: TextStyle(color: isPrimary ? Colors.black : Colors.white, fontSize: 13, fontWeight: FontWeight.w900, letterSpacing: 1)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}