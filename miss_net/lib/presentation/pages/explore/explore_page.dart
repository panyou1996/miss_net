import 'package:flutter/material.dart';
import '../category/category_detail_page.dart';

class ExplorePage extends StatelessWidget {
  const ExplorePage({super.key});

  final List<Map<String, String>> categories = const [
    {'title': 'School', 'category': 'School', 'icon': '🏫'},
    {'title': 'Office', 'category': 'Office', 'icon': '💼'},
    {'title': 'VR', 'category': 'VR', 'icon': '👓'},
    {'title': 'Mature', 'category': 'Mature', 'icon': '🍷'},
    {'title': 'Subtitled', 'category': 'Subtitled', 'icon': '🔤'},
    {'title': 'Uncensored', 'category': 'uncensored', 'icon': '🔥'},
    {'title': 'New', 'category': 'new', 'icon': '🆕'},
    {'title': 'Monthly Hot', 'category': 'monthly_hot', 'icon': '📈'},
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text("Explore Categories", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.black,
      ),
      body: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          crossAxisSpacing: 16,
          mainAxisSpacing: 16,
          childAspectRatio: 2.5,
        ),
        itemCount: categories.length,
        itemBuilder: (context, index) {
          final item = categories[index];
          return InkWell(
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => CategoryDetailPage(title: item['title']!, category: item['category']!),
                ),
              );
            },
            child: Container(
              decoration: BoxDecoration(
                color: Colors.grey[900],
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.white.withOpacity(0.1)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(item['icon']!, style: const TextStyle(fontSize: 20)),
                  const SizedBox(width: 10),
                  Text(
                    item['title']!,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}