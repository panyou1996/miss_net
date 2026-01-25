import 'package:flutter/material.dart';

class FavoritesPage extends StatelessWidget {
  const FavoritesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(title: const Text("My Favorites"), backgroundColor: Colors.black),
      body: const Center(child: Text("Your favorites will appear here.", style: TextStyle(color: Colors.white))),
    );
  }
}
