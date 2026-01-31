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

  final List<Map<String, String>> categories = const [
    {'title': '51 Eating Melon', 'category': '51cg', 'icon': '🍉'},
    {'title': 'School', 'category': 'School', 'icon': '🏫'},
    {'title': 'Office', 'category': 'Office', 'icon': '💼'},
    {'title': 'Mature', 'category': 'Mature', 'icon': '🍷'},
    {'title': 'Exclusive', 'category': 'Exclusive', 'icon': '💎'},
    {'title': 'Nympho', 'category': 'Nympho', 'icon': '🤤'},
    {'title': 'Voyeur', 'category': 'Voyeur', 'icon': '📷'},
    {'title': 'Sister', 'category': 'Sister', 'icon': '👩‍❤️‍👩'},
    {'title': 'Story', 'category': 'Story', 'icon': '📖'},
    {'title': 'Subtitled', 'category': 'Subtitled', 'icon': '🔤'},
    {'title': 'Uncensored', 'category': 'uncensored', 'icon': '🔥'},
    {'title': 'Amateur', 'category': 'Amateur', 'icon': '👧'},
    {'title': 'Big Tits', 'category': 'BigTits', 'icon': '🍈'},
    {'title': 'Creampie', 'category': 'Creampie', 'icon': '💦'},
    {'title': 'Beautiful', 'category': 'Beautiful', 'icon': '✨'},
    {'title': 'Oral', 'category': 'Oral', 'icon': '👅'},
    {'title': 'Group', 'category': 'Group', 'icon': '👨‍👩‍👧‍👦'},
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
    return Scaffold(
      appBar: AppBar(
        title: Text("Explore", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold)),
        backgroundColor: theme.appBarTheme.backgroundColor,
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
      body: SingleChildScrollView(
        padding: const EdgeInsets.only(bottom: 100), // Space for floating nav
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_popularActors.isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text("Popular Actresses", style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              SizedBox(
                height: 100,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _popularActors.length,
                  itemBuilder: (context, index) => _buildActorAvatar(context, _popularActors[index]),
                ),
              ),
            ],

            if (_popularTags.isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
                child: Text("Trending Tags", style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _popularTags.map((tag) => _buildTagChip(context, tag)).toList(),
                ),
              ),
            ],

            Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
              child: Text("Browse Categories", style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.bold)),
            ),
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 16),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 3.0,
              ),
              itemCount: categories.length,
              itemBuilder: (context, index) {
                final item = categories[index];
                return InkWell(
                  onTap: () {
                    Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: item['title']!, category: item['category']!)));
                  },
                  child: Container(
                    decoration: BoxDecoration(
                      color: theme.cardColor,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: theme.dividerColor.withValues(alpha: 0.1)),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(item['icon']!, style: const TextStyle(fontSize: 18)),
                        const SizedBox(width: 8),
                        Text(item['title']!, style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActorAvatar(BuildContext context, String name) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(right: 16),
      child: InkWell(
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: name, actor: name))),
        child: Column(
          children: [
            CircleAvatar(
              radius: 32,
              backgroundColor: theme.cardColor,
              child: Text(name[0], style: TextStyle(color: theme.colorScheme.primary, fontSize: 24, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: 70,
              child: Text(name, textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontSize: 11)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTagChip(BuildContext context, String tag) {
    final theme = Theme.of(context);
    return ActionChip(
      label: Text(tag),
      backgroundColor: theme.cardColor,
      labelStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7), fontSize: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: BorderSide(color: theme.dividerColor.withValues(alpha: 0.1))),
      onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: tag, category: tag))),
    );
  }
}
