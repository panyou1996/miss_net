import 'package:flutter/material.dart';
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
    {'title': '51 Eating Melon', 'category': '51cg', 'icon': '🍉', 'color': Colors.red},
    {'title': 'School', 'category': 'School', 'icon': '🏫', 'color': Colors.blue},
    {'title': 'Office', 'category': 'Office', 'icon': '💼', 'color': Colors.blueGrey},
    {'title': 'Mature', 'category': 'Mature', 'icon': '🍷', 'color': Colors.purple},
    {'title': 'Exclusive', 'category': 'Exclusive', 'icon': '💎', 'color': Colors.amber},
    {'title': 'Nympho', 'category': 'Nympho', 'icon': '🤤', 'color': Colors.pink},
    {'title': 'Voyeur', 'category': 'Voyeur', 'icon': '📷', 'color': Colors.teal},
    {'title': 'Sister', 'category': 'Sister', 'icon': '👩‍❤️‍👩', 'color': Colors.orange},
    {'title': 'Story', 'category': 'Story', 'icon': '📖', 'color': Colors.indigo},
    {'title': 'Subtitled', 'category': 'Subtitled', 'icon': '🔤', 'color': Colors.green},
    {'title': 'Uncensored', 'category': 'uncensored', 'icon': '🔥', 'color': Colors.deepOrange},
    {'title': 'Amateur', 'category': 'Amateur', 'icon': '👧', 'color': Colors.brown},
    {'title': 'Big Tits', 'category': 'BigTits', 'icon': '🍈', 'color': Colors.lime},
    {'title': 'Creampie', 'category': 'Creampie', 'icon': '💦', 'color': Colors.lightBlue},
    {'title': 'Beautiful', 'category': 'Beautiful', 'icon': '✨', 'color': Colors.deepPurple},
    {'title': 'Oral', 'category': 'Oral', 'icon': '👅', 'color': Colors.pinkAccent},
    {'title': 'Group', 'category': 'Group', 'icon': '👨‍👩‍👧‍👦', 'color': Colors.cyan},
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
            expandedHeight: 120,
            backgroundColor: Colors.transparent,
            title: Text("Explore", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold, letterSpacing: -1)),
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
                  _buildSectionHeader("Popular Actresses"),
                  SizedBox(
                    height: 120,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: _popularActors.length,
                      itemBuilder: (context, index) => _buildActorAvatar(context, _popularActors[index]),
                    ),
                  ),
                ],

                if (_popularTags.isNotEmpty) ...[
                  _buildSectionHeader("Trending Tags"),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: _popularTags.map((tag) => _buildTagChip(context, tag)).toList(),
                    ),
                  ),
                ],

                _buildSectionHeader("Browse Categories"),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 120),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 2.2,
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
      padding: const EdgeInsets.fromLTRB(20, 32, 16, 16),
      child: Text(
        title, 
        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900, letterSpacing: -0.5)
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
      borderRadius: BorderRadius.circular(16),
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1C1C1E) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.05)),
          boxShadow: [
            BoxShadow(color: color.withValues(alpha: 0.1), blurRadius: 10, offset: const Offset(0, 4)),
          ],
        ),
        child: Stack(
          children: [
            Positioned(
              right: -10,
              bottom: -10,
              child: Opacity(
                opacity: 0.1,
                child: Text(item['icon'], style: const TextStyle(fontSize: 60)),
              ),
            ),
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(item['icon'], style: const TextStyle(fontSize: 24)),
                  const SizedBox(height: 4),
                  Text(
                    item['title'], 
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: theme.colorScheme.onSurface.withValues(alpha: 0.8))
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
      padding: const EdgeInsets.only(right: 20),
      child: InkWell(
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: name, actor: name))),
        borderRadius: BorderRadius.circular(40),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(2),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: Colors.red.withValues(alpha: 0.5), width: 1.5),
              ),
              child: CircleAvatar(
                radius: 34,
                backgroundColor: isDark ? const Color(0xFF1C1C1E) : Colors.grey[200],
                child: Text(name[0], style: const TextStyle(color: Colors.redAccent, fontSize: 24, fontWeight: FontWeight.w900)),
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: 80,
              child: Text(name, textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
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
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1C1C1E) : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1)),
        ),
        child: Text(
          "#$tag", 
          style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontSize: 12, fontWeight: FontWeight.bold)
        ),
      ),
    );
  }
}
