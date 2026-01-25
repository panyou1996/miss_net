import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(title: const Text("Settings"), backgroundColor: Colors.black),
      body: ListView(
        children: const [
          ListTile(
            leading: Icon(Icons.info, color: Colors.white),
            title: Text("Version", style: TextStyle(color: Colors.white)),
            trailing: Text("1.2.0", style: TextStyle(color: Colors.grey)),
          ),
        ],
      ),
    );
  }
}
