import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  User? _user;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _user = Supabase.instance.client.auth.currentUser;
    Supabase.instance.client.auth.onAuthStateChange.listen((data) {
      if (mounted) {
        setState(() {
          _user = data.session?.user;
        });
      }
    });
  }

  Future<void> _handleAuth() async {
    final emailController = TextEditingController();
    final passwordController = TextEditingController();
    bool isLogin = true;

    await showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              backgroundColor: Colors.grey[900],
              title: Text(isLogin ? "Login" : "Register", style: const TextStyle(color: Colors.white)),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: emailController,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(labelText: "Email", labelStyle: TextStyle(color: Colors.white70)),
                  ),
                  TextField(
                    controller: passwordController,
                    obscureText: true,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(labelText: "Password", labelStyle: TextStyle(color: Colors.white70)),
                  ),
                  const SizedBox(height: 10),
                  TextButton(
                    onPressed: () => setState(() => isLogin = !isLogin),
                    child: Text(isLogin ? "Create Account" : "I have an account", style: const TextStyle(color: Colors.red)),
                  ),
                ],
              ),
              actions: [
                TextButton(child: const Text("Cancel"), onPressed: () => Navigator.pop(context)),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                  onPressed: () async {
                    Navigator.pop(context);
                    _processAuth(emailController.text, passwordController.text, isLogin);
                  },
                  child: Text(isLogin ? "Login" : "Sign Up"),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _processAuth(String email, String password, bool isLogin) async {
    setState(() => _isLoading = true);
    try {
      if (isLogin) {
        await Supabase.instance.client.auth.signInWithPassword(email: email, password: password);
      } else {
        await Supabase.instance.client.auth.signUp(email: email, password: password);
      }
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Success!")));
      // Auto sync after login
      sl<VideoRepository>().syncData();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Error: $e")));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _logout() async {
    await Supabase.instance.client.auth.signOut();
  }

  Future<void> _sync() async {
    setState(() => _isLoading = true);
    await sl<VideoRepository>().syncData();
    setState(() => _isLoading = false);
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Sync Completed")));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(title: const Text("Settings", style: TextStyle(color: Colors.white)), backgroundColor: Colors.transparent),
      body: ListView(
        children: [
          const Padding(
            padding: EdgeInsets.all(16.0),
            child: Text("Account", style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
          ),
          ListTile(
            leading: Icon(_user == null ? Icons.account_circle_outlined : Icons.check_circle, color: Colors.white),
            title: Text(_user == null ? "Login / Register" : "Logged in as ${_user!.email}", style: const TextStyle(color: Colors.white)),
            subtitle: _user == null ? const Text("Sync your favorites to cloud", style: TextStyle(color: Colors.grey)) : null,
            trailing: _user != null ? IconButton(icon: const Icon(Icons.logout, color: Colors.white), onPressed: _logout) : null,
            onTap: _user == null ? _handleAuth : null,
          ),
          if (_user != null)
            ListTile(
              leading: _isLoading ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.cloud_sync, color: Colors.white),
              title: const Text("Sync Now", style: TextStyle(color: Colors.white)),
              onTap: _isLoading ? null : _sync,
            ),
            
          const Divider(color: Colors.grey),
          const Padding(
            padding: EdgeInsets.all(16.0),
            child: Text("About", style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
          ),
          const ListTile(
            leading: Icon(Icons.info_outline, color: Colors.white),
            title: Text("Version", style: TextStyle(color: Colors.white)),
            trailing: Text("1.2.0", style: TextStyle(color: Colors.grey)),
          ),
        ],
      ),
    );
  }
}
