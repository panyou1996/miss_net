import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/services/privacy_service.dart';
import '../downloads/downloads_page.dart';
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
  bool _incognito = false;
  bool _appLock = false;
  final PrivacyService _privacy = sl<PrivacyService>();
  
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
        _incognito = _privacy.isIncognito;
        _appLock = _privacy.isAppLockEnabled;
      });
    }
  }

  Future<void> _toggleAutoplay(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('autoplay_next', value);
    setState(() => _autoplayNext = value);
  }

  Future<void> _toggleIncognito(bool value) async {
    await _privacy.setIncognito(value);
    setState(() => _incognito = value);
  }

  Future<void> _toggleAppLock(bool value) async {
    if (value) {
      final success = await _privacy.authenticate();
      if (!success) {
        if(mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Authentication Failed. Please set up a Screen Lock (PIN/Pattern/Fingerprint) in your device settings first.")));
        return;
      }
    }
    await _privacy.setAppLock(value);
    setState(() => _appLock = value);
  }

  Future<void> _handleAuth() async {
    final emailController = TextEditingController();
    final passwordController = TextEditingController();
    bool isLogin = true;

    await showDialog(
      context: context,
      builder: (context) {
        final theme = Theme.of(context);
        return BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: StatefulBuilder(
            builder: (context, setState) {
              return AlertDialog(
                backgroundColor: theme.cardColor.withValues(alpha: 0.9),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                title: Text(isLogin ? "Login" : "Register", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold)),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: emailController,
                      style: TextStyle(color: theme.colorScheme.onSurface),
                      decoration: InputDecoration(
                        labelText: "Email", 
                        labelStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7)),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: passwordController,
                      obscureText: true,
                      style: TextStyle(color: theme.colorScheme.onSurface),
                      decoration: InputDecoration(
                        labelText: "Password", 
                        labelStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7)),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
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
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                    onPressed: () async {
                      Navigator.pop(context);
                      _processAuth(emailController.text, passwordController.text, isLogin);
                    },
                    child: Text(isLogin ? "Login" : "Sign Up"),
                  ),
                ],
              );
            },
          ),
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
      builder: (context) => BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: AlertDialog(
          backgroundColor: theme.cardColor.withValues(alpha: 0.9),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: Text("Clear History?", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.bold)),
          content: Text("This cannot be undone.", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.7))),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: Text("Cancel", style: TextStyle(color: theme.colorScheme.onSurface))),
            TextButton(onPressed: () => Navigator.pop(context, true), child: const Text("Clear", style: TextStyle(color: Colors.red))),
          ],
        ),
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
    final groupBg = theme.colorScheme.surfaceContainerLow;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.red))
          : CustomScrollView(
              slivers: [
                SliverAppBar.large(
                  expandedHeight: 140,
                  backgroundColor: isDark ? Colors.black.withValues(alpha: 0.8) : Colors.white.withValues(alpha: 0.8),
                  pinned: true,
                  elevation: 0,
                  flexibleSpace: FlexibleSpaceBar(
                    centerTitle: false,
                    titlePadding: const EdgeInsets.fromLTRB(20, 0, 16, 16),
                    title: Text(
                      "Settings", 
                      style: GoogleFonts.playfairDisplay(
                        color: theme.colorScheme.onSurface, 
                        fontWeight: FontWeight.w900, 
                        fontSize: 28,
                        letterSpacing: -1
                      )
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildSectionHeader("Account"),
                      _buildGroup(theme, groupBg, [
                        ListTile(
                          leading: Icon(_user == null ? Icons.account_circle_outlined : Icons.check_circle, color: theme.iconTheme.color),
                          title: Text(_user == null ? "Login / Register" : "Logged in as ${_user!.email}", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          subtitle: _user == null ? Text("Sync your favorites to cloud", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))) : null,
                          trailing: _user != null ? IconButton(icon: Icon(Icons.logout, color: theme.iconTheme.color), onPressed: _logout) : Icon(Icons.arrow_forward_ios, color: theme.iconTheme.color?.withValues(alpha: 0.5), size: 16),
                          onTap: _user == null ? _handleAuth : null,
                        ),
                        if (_user != null)
                          ListTile(
                            leading: Icon(Icons.sync, color: theme.iconTheme.color),
                            title: Text("Sync Now", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                            onTap: _sync,
                          ),
                      ]),

                      _buildSectionHeader("Appearance"),
                      _buildGroup(theme, groupBg, [
                        SwitchListTile(
                          activeColor: Colors.red,
                          secondary: Icon(isDark ? Icons.dark_mode : Icons.light_mode, color: theme.iconTheme.color),
                          title: Text("Dark Mode", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          value: isDark,
                          onChanged: (val) => context.read<ThemeBloc>().add(ToggleTheme()),
                        ),
                      ]),
                      
                      _buildSectionHeader("Privacy & Security"),
                      _buildGroup(theme, groupBg, [
                        SwitchListTile(
                          activeColor: Colors.red,
                          secondary: Icon(Icons.visibility_off, color: theme.iconTheme.color),
                          title: Text("Incognito Mode", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          subtitle: Text("Don't save watch history", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6), fontSize: 12)),
                          value: _incognito,
                          onChanged: _toggleIncognito,
                        ),
                        SwitchListTile(
                          activeColor: Colors.red,
                          secondary: Icon(Icons.fingerprint, color: theme.iconTheme.color),
                          title: Text("App Lock", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          subtitle: Text("Require authentication on start", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6), fontSize: 12)),
                          value: _appLock,
                          onChanged: _toggleAppLock,
                        ),
                      ]),
                      
                      _buildSectionHeader("Playback"),
                      _buildGroup(theme, groupBg, [
                        SwitchListTile(
                          activeColor: Colors.red,
                          secondary: Icon(Icons.play_circle_outline, color: theme.iconTheme.color),
                          title: Text("Autoplay Next Video", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          value: _autoplayNext,
                          onChanged: _toggleAutoplay,
                        ),
                      ]),

                      _buildSectionHeader("Storage & Data"),
                      _buildGroup(theme, groupBg, [
                        ListTile(
                          leading: Icon(Icons.download_done, color: theme.iconTheme.color),
                          title: Text("Downloads", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          trailing: const Icon(Icons.arrow_forward_ios, size: 16, color: Colors.grey),
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DownloadsPage())),
                        ),
                        ListTile(
                          leading: Icon(Icons.image, color: theme.iconTheme.color),
                          title: Text("Clear Image Cache", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          subtitle: Text("Free up space used by thumbnails", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))),
                          onTap: _clearImageCache,
                        ),
                        ListTile(
                          leading: Icon(Icons.history, color: theme.iconTheme.color),
                          title: Text("Clear Watch History", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          onTap: _clearHistory,
                        ),
                      ]),

                      _buildSectionHeader("About"),
                      _buildGroup(theme, groupBg, [
                        ListTile(
                          leading: Icon(Icons.info_outline, color: theme.iconTheme.color),
                          title: Text("Version", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          trailing: Text(_version.isNotEmpty ? _version : "Loading...", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.6))),
                        ),
                        ListTile(
                          leading: Icon(Icons.code, color: theme.iconTheme.color),
                          title: Text("Source Code", style: TextStyle(color: theme.colorScheme.onSurface, fontWeight: FontWeight.w600)),
                          trailing: Icon(Icons.open_in_new, color: theme.iconTheme.color?.withValues(alpha: 0.5), size: 16),
                          onTap: () => launchUrl(Uri.parse("https://github.com/panyou1996/miss_net")),
                        ),
                      ]),
                      const SizedBox(height: 100),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildGroup(ThemeData theme, Color? bgColor, List<Widget> children) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(16), // Consistent with other pages
        border: Border.all(color: theme.dividerColor.withValues(alpha: 0.05)),
      ),
      child: Column(
        children: children.asMap().entries.map((entry) {
          final idx = entry.key;
          final widget = entry.value;
          final isLast = idx == children.length - 1;
          
          if (isLast) return widget;
          return Column(
            children: [
              widget,
              Padding(
                padding: const EdgeInsets.only(left: 56), // Aligned divider
                child: Divider(height: 1, color: theme.dividerColor.withValues(alpha: 0.08)),
              ),
            ],
          );
        }).toList(),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 24, 16, 8),
      child: Text(
        title.toUpperCase(),
        style: TextStyle(color: Theme.of(context).colorScheme.primary, fontSize: 13, fontWeight: FontWeight.w900, letterSpacing: 1.2),
      ),
    );
  }
}