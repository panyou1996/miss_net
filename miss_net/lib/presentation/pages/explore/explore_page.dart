import 'package:flutter/material.dart';

class ExplorePage extends StatelessWidget {
  const ExplorePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(title: const Text("Explore"), backgroundColor: Colors.black),
      body: const Center(child: Text("Categories coming soon...", style: TextStyle(color: Colors.white))),
    );
  }
}
