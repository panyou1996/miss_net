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
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text("Explore", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.black,
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: Colors.white),
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
              const Padding(
                padding: EdgeInsets.all(16.0),
                child: Text("Popular Actresses", style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              SizedBox(
                height: 100,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _popularActors.length,
                  itemBuilder: (context, index) => _buildActorAvatar(_popularActors[index]),
                ),
              ),
            ],

            if (_popularTags.isNotEmpty) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 24, 16, 16),
                child: Text("Trending Tags", style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _popularTags.map((tag) => _buildTagChip(tag)).toList(),
                ),
              ),
            ],

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 24, 16, 16),
              child: Text("Browse Categories", style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
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
                      color: Colors.grey[900],
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(item['icon']!, style: const TextStyle(fontSize: 18)),
                        const SizedBox(width: 8),
                        Text(item['title']!, style: const TextStyle(color: Colors.white70, fontWeight: FontWeight.w600)),
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

  Widget _buildActorAvatar(String name) {
    return Padding(
      padding: const EdgeInsets.only(right: 16),
      child: InkWell(
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: name, actor: name))),
        child: Column(
          children: [
            CircleAvatar(
              radius: 32,
              backgroundColor: Colors.grey[800],
              child: Text(name[0], style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: 70,
              child: Text(name, textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white70, fontSize: 11)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTagChip(String tag) {
    return ActionChip(
      label: Text(tag),
      backgroundColor: Colors.grey[900],
      labelStyle: const TextStyle(color: Colors.white70, fontSize: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: const BorderSide(color: Colors.white10)),
      onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CategoryDetailPage(title: tag, category: tag))),
    );
  }
}
