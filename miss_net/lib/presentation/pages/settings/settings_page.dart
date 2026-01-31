import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../domain/repositories/video_repository.dart';
import '../../../injection_container.dart';
import '../../blocs/theme/theme_bloc.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  User? _user;
  bool _isLoading = false;
  String _version = "";
  bool _autoplayNext = false;
  
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
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final info = await PackageInfo.fromPlatform();
    final prefs = await SharedPreferences.getInstance();
    
    if (mounted) {
      setState(() {
        _version = info.version;
        _autoplayNext = prefs.getBool('autoplay_next') ?? false;
      });
    }
  }

  Future<void> _toggleAutoplay(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('autoplay_next', value);
    setState(() => _autoplayNext = value);
  }

  Future<void> _handleAuth() async {
    final emailController = TextEditingController();
    final passwordController = TextEditingController();
    bool isLogin = true;

    await showDialog(
      context: context,
      builder: (context) {
        final theme = Theme.of(context);
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              backgroundColor: theme.cardColor,
              title: Text(isLogin ? "Login" : "Register", style: TextStyle(color: theme.colorScheme.onSurface)),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: emailController,
                    style: TextStyle(color: theme.colorScheme.onSurface),
                    decoration: InputDecoration(
                      labelText: "Email", 
                      labelStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7)),
                      enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: theme.colorScheme.onSurface.withValues(alpha: 0.5))),
                    ),
                  ),
                  TextField(
                    controller: passwordController,
                    obscureText: true,
                    style: TextStyle(color: theme.colorScheme.onSurface),
                    decoration: InputDecoration(
                      labelText: "Password", 
                      labelStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7)),
                      enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: theme.colorScheme.onSurface.withValues(alpha: 0.5))),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextButton(
                    onPressed: () => setState(() => isLogin = !isLogin),
                    child: Text(isLogin ? "Create Account" : "I have an account", style: const TextStyle(color: Colors.red)),
                  ),
                ],
              ),
              actions: [
                TextButton(child: Text("Cancel", style: TextStyle(color: theme.colorScheme.onSurface)), onPressed: () => Navigator.pop(context)),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
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

  Future<void> _clearImageCache() async {
    setState(() => _isLoading = true);
    try {
      await DefaultCacheManager().emptyCache();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Image Cache Cleared")));
    } catch (e) {
      // ignore
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _clearHistory() async {
    final theme = Theme.of(context);
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: theme.cardColor,
        title: Text("Clear History?", style: TextStyle(color: theme.colorScheme.onSurface)),
        content: Text("This cannot be undone.", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text("Cancel", style: TextStyle(color: theme.colorScheme.onSurface))),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text("Clear", style: TextStyle(color: Colors.red))),
        ],
      ),
    );

    if (confirm == true) {
      await sl<VideoRepository>().clearHistory();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("History Cleared")));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text("Settings", style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : ListView(
              children: [
                _buildSectionHeader("Account"),
                ListTile(
                  leading: Icon(_user == null ? Icons.account_circle_outlined : Icons.check_circle, color: theme.iconTheme.color),
                  title: Text(_user == null ? "Login / Register" : "Logged in as ${_user!.email}", style: TextStyle(color: theme.colorScheme.onSurface)),
                  subtitle: _user == null ? Text("Sync your favorites to cloud", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))) : null,
                  trailing: _user != null ? IconButton(icon: Icon(Icons.logout, color: theme.iconTheme.color), onPressed: _logout) : Icon(Icons.arrow_forward_ios, color: theme.iconTheme.color?.withValues(alpha: 0.5), size: 16),
                  onTap: _user == null ? _handleAuth : null,
                ),
                if (_user != null)
                  ListTile(
                    leading: Icon(Icons.sync, color: theme.iconTheme.color),
                    title: Text("Sync Now", style: TextStyle(color: theme.colorScheme.onSurface)),
                    onTap: _sync,
                  ),

                const Divider(height: 30),
                
                _buildSectionHeader("Appearance"),
                SwitchListTile(
                  activeThumbColor: Colors.red,
                  secondary: Icon(isDark ? Icons.dark_mode : Icons.light_mode, color: theme.iconTheme.color),
                  title: Text("Dark Mode", style: TextStyle(color: theme.colorScheme.onSurface)),
                  value: isDark,
                  onChanged: (val) => context.read<ThemeBloc>().add(ToggleTheme()),
                ),

                const Divider(height: 30),
                
                _buildSectionHeader("Playback"),
                SwitchListTile(
                  activeThumbColor: Colors.red,
                  secondary: Icon(Icons.play_circle_outline, color: theme.iconTheme.color),
                  title: Text("Autoplay Next Video", style: TextStyle(color: theme.colorScheme.onSurface)),
                  value: _autoplayNext,
                  onChanged: _toggleAutoplay,
                ),

                const Divider(height: 30),

                _buildSectionHeader("Storage & Data"),
                ListTile(
                  leading: Icon(Icons.image, color: theme.iconTheme.color),
                  title: Text("Clear Image Cache", style: TextStyle(color: theme.colorScheme.onSurface)),
                  subtitle: Text("Free up space used by thumbnails", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))),
                  onTap: _clearImageCache,
                ),
                ListTile(
                  leading: Icon(Icons.history, color: theme.iconTheme.color),
                  title: Text("Clear Watch History", style: TextStyle(color: theme.colorScheme.onSurface)),
                  onTap: _clearHistory,
                ),

                const Divider(height: 30),

                _buildSectionHeader("About"),
                ListTile(
                  leading: Icon(Icons.info_outline, color: theme.iconTheme.color),
                  title: Text("Version", style: TextStyle(color: theme.colorScheme.onSurface)),
                  trailing: Text(_version.isNotEmpty ? _version : "Loading...", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))),
                ),
                ListTile(
                  leading: Icon(Icons.code, color: theme.iconTheme.color),
                  title: Text("Source Code", style: TextStyle(color: theme.colorScheme.onSurface)),
                  trailing: Icon(Icons.open_in_new, color: theme.iconTheme.color?.withValues(alpha: 0.5), size: 16),
                  onTap: () => launchUrl(Uri.parse("https://github.com/panyou1996/miss_net")),
                ),
                const SizedBox(height: 50),
              ],
            ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Text(
        title,
        style: const TextStyle(color: Colors.red, fontSize: 14, fontWeight: FontWeight.bold, letterSpacing: 1.0),
      ),
    );
  }
}
