import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../category/category_detail_page.dart';
import '../../blocs/search/search_bloc.dart';
import '../search_delegate.dart';

class ExplorePage extends StatefulWidget {
  const ExplorePage({super.key});

  @override
  State<ExplorePage> createState() => _ExplorePageState();
}

class _ExplorePageState extends State<ExplorePage> {
  final VideoRepository _repository = sl<VideoRepository>();
  List<String> _popularActors = [];
  List<String> _popularTags = [];

  final List<Map<String, dynamic>> categories = const [
    {'title': '51 Eating Melon', 'category': '51cg', 'icon': FontAwesomeIcons.watermelonSlice, 'color': Colors.red},
    {'title': 'School', 'category': 'School', 'icon': FontAwesomeIcons.graduationCap, 'color': Colors.blue},
    {'title': 'Office', 'category': 'Office', 'icon': FontAwesomeIcons.briefcase, 'color': Colors.blueGrey},
    {'title': 'Mature', 'category': 'Mature', 'icon': FontAwesomeIcons.wineGlass, 'color': Colors.purple},
    {'title': 'Exclusive', 'category': 'Exclusive', 'icon': FontAwesomeIcons.gem, 'color': Colors.amber},
    {'title': 'Nympho', 'category': 'Nympho', 'icon': FontAwesomeIcons.heartPulse, 'color': Colors.pink},
    {'title': 'Voyeur', 'category': 'Voyeur', 'icon': FontAwesomeIcons.camera, 'color': Colors.teal},
    {'title': 'Sister', 'category': 'Sister', 'icon': FontAwesomeIcons.userGroup, 'color': Colors.orange},
    {'title': 'Story', 'category': 'Story', 'icon': FontAwesomeIcons.bookOpen, 'color': Colors.indigo},
    {'title': 'Subtitled', 'category': 'Subtitled', 'icon': FontAwesomeIcons.language, 'color': Colors.green},
    {'title': 'Uncensored', 'category': 'uncensored', 'icon': FontAwesomeIcons.fire, 'color': Colors.deepOrange},
    {'title': 'Amateur', 'category': 'Amateur', 'icon': FontAwesomeIcons.userNinja, 'color': Colors.brown},
    {'title': 'Big Tits', 'category': 'BigTits', 'icon': FontAwesomeIcons.circleExclamation, 'color': Colors.lime},
    {'title': 'Creampie', 'category': 'Creampie', 'icon': FontAwesomeIcons.droplet, 'color': Colors.lightBlue},
    {'title': 'Beautiful', 'category': 'Beautiful', 'icon': FontAwesomeIcons.wandMagicSparkles, 'color': Colors.deepPurple},
    {'title': 'Oral', 'category': 'Oral', 'icon': FontAwesomeIcons.lips, 'color': Colors.pinkAccent},
    {'title': 'Group', 'category': 'Group', 'icon': FontAwesomeIcons.users, 'color': Colors.cyan},
  ];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final actorsRes = await _repository.getPopularActors();
    final tagsRes = await _repository.getPopularTags();

    if (mounted) {
      setState(() {
        actorsRes.fold((l) => null, (r) => _popularActors = r);
        tagsRes.fold((l) => null, (r) => _popularTags = r);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? Colors.black : Colors.grey[50],
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            expandedHeight: 140,
            backgroundColor: Colors.transparent,
            stretch: true,
            title: Text(
              "Explore", 
              style: GoogleFonts.playfairDisplay(
                color: theme.colorScheme.onSurface, 
                fontWeight: FontWeight.w900, 
                letterSpacing: -1.5,
                fontSize: 32
              )
            ),
            actions: [
              IconButton(
                icon: Icon(Icons.search, color: theme.iconTheme.color),
                onPressed: () {
                  final searchBloc = sl<SearchBloc>();
                  showSearch(context: context, delegate: VideoSearchDelegate(searchBloc)).then((_) => searchBloc.close());
                },
              ),
            ],
          ),
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_popularActors.isNotEmpty) ...[
                  _buildSectionHeader("POPULAR ACTRESSES"),
                  SizedBox(
                    height: 130,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: _popularActors.length,
                      itemBuilder: (context, index) => _buildActorAvatar(context, _popularActors[index]),
                    ),
                  ),
                ],

                if (_popularTags.isNotEmpty) ...[
                  _buildSectionHeader("TRENDING TOPICS"),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: _popularTags.map((tag) => _buildTagChip(context, tag)).toList(),
                    ),
                  ),
                ],

                _buildSectionHeader("BROWSE CATEGORIES"),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 120),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 14,
                    mainAxisSpacing: 14,
                    childAspectRatio: 2.0,
                  ),
                  itemCount: categories.length,
                  itemBuilder: (context, index) => _buildCategoryCard(context, categories[index]),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 40, 16, 16),
      child: Text(
        title, 
        style: TextStyle(
          fontSize: 13, 
          fontWeight: FontWeight.w900, 
          letterSpacing: 2.0,
          color: Colors.redAccent.withValues(alpha: 0.8)
        )
      ),
    );
  }

  Widget _buildCategoryCard(BuildContext context, Map<String, dynamic> item) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final Color color = item['color'];

    return InkWell(
      onTap: () {
        Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: item['title']!, category: item['category']!)));
      },
      borderRadius: BorderRadius.circular(20),
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1C1C1E) : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.08)),
          boxShadow: [
            BoxShadow(color: color.withValues(alpha: 0.05), blurRadius: 15, offset: const Offset(0, 8)),
          ],
        ),
        child: Stack(
          children: [
            Positioned(
              right: -15,
              bottom: -15,
              child: Opacity(
                opacity: 0.05,
                child: FaIcon(item['icon'], size: 80, color: color),
              ),
            ),
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  FaIcon(item['icon'], size: 24, color: color.withValues(alpha: 0.8)),
                  const SizedBox(height: 10),
                  Text(
                    item['title'].toUpperCase(), 
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.w900, letterSpacing: 0.5, color: theme.colorScheme.onSurface.withValues(alpha: 0.9))
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActorAvatar(BuildContext context, String name) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    return Padding(
      padding: const EdgeInsets.only(right: 24),
      child: InkWell(
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: name, actor: name))),
        borderRadius: BorderRadius.circular(40),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(3),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [Colors.redAccent, Colors.redAccent.withValues(alpha: 0.2)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: CircleAvatar(
                radius: 36,
                backgroundColor: isDark ? const Color(0xFF1C1C1E) : Colors.white,
                child: Text(
                  name[0], 
                  style: GoogleFonts.playfairDisplay(color: Colors.redAccent, fontSize: 28, fontWeight: FontWeight.w900)
                ),
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: 80,
              child: Text(
                name, 
                textAlign: TextAlign.center, 
                maxLines: 1, 
                overflow: TextOverflow.ellipsis, 
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: -0.2)
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTagChip(BuildContext context, String tag) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    return InkWell(
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: tag, category: tag))),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1C1C1E) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1)),
        ),
        child: Text(
          "#$tag", 
          style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.8), fontSize: 13, fontWeight: FontWeight.w600)
        ),
      ),
    );
  }
}