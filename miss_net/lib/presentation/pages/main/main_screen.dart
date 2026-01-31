import 'dart:ui';
import 'package:flutter/material.dart';
import '../../../core/services/privacy_service.dart';
import '../../../injection_container.dart';
import '../../widgets/fade_indexed_stack.dart';
import '../home_page.dart';
import '../explore/explore_page.dart';
import '../favorites/favorites_page.dart';
import '../settings/settings_page.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> with WidgetsBindingObserver {
  int _selectedIndex = 0;
  final PrivacyService _privacy = sl<PrivacyService>();
  bool _isLocked = false;
  final ValueNotifier<bool> _isNavbarVisible = ValueNotifier<bool>(true);

  final List<Widget> _pages = [
    const HomePage(),
    const ExplorePage(),
    const FavoritesPage(),
    const SettingsPage(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAppLock();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _isNavbarVisible.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkAppLock();
    } else if (state == AppLifecycleState.paused) {
      if (_privacy.isAppLockEnabled) {
        setState(() => _isLocked = true);
      }
    }
  }

  Future<void> _checkAppLock() async {
    if (_privacy.isAppLockEnabled) {
      setState(() => _isLocked = true);
      final authenticated = await _privacy.authenticate();
      if (authenticated) {
        if (mounted) setState(() => _isLocked = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLocked) {
      return Scaffold(
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.lock_outline, size: 64, color: Colors.grey),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _checkAppLock,
                child: const Text("Unlock"),
              ),
            ],
          ),
        ),
      );
    }

    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    final glassColor = isDark ? Colors.black.withValues(alpha: 0.7) : Colors.white.withValues(alpha: 0.9);
    final borderColor = isDark ? Colors.white.withValues(alpha: 0.1) : Colors.black.withValues(alpha: 0.1);
    final shadowColor = isDark ? Colors.black.withValues(alpha: 0.3) : Colors.grey.withValues(alpha: 0.3);

    return Scaffold(
      extendBody: true,
      body: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (notification is ScrollUpdateNotification) {
            if (notification.scrollDelta! > 10 && _isNavbarVisible.value) {
              _isNavbarVisible.value = false;
            } else if (notification.scrollDelta! < -10 && !_isNavbarVisible.value) {
              _isNavbarVisible.value = true;
            }
          }
          return false;
        },
        child: Stack(
          children: [
            FadeIndexedStack(
              index: _selectedIndex,
              children: _pages,
            ),
            
            ValueListenableBuilder<bool>(
              valueListenable: _isNavbarVisible,
              builder: (context, isVisible, child) {
                return AnimatedPositioned(
                  duration: const Duration(milliseconds: 300),
                  curve: Curves.easeInOut,
                  left: 16,
                  right: 16,
                  bottom: isVisible ? 24 : -100,
                  child: child!,
                );
              },
              child: ClipRRect(
                borderRadius: BorderRadius.circular(24),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                  child: Container(
                    height: 64,
                    decoration: BoxDecoration(
                      color: glassColor, 
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(
                        color: borderColor,
                        width: 0.5,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: shadowColor,
                          blurRadius: 20,
                          offset: const Offset(0, 10),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _buildNavItem(context, Icons.home_rounded, Icons.home_outlined, "Home", 0),
                        _buildNavItem(context, Icons.explore_rounded, Icons.explore_outlined, "Explore", 1),
                        _buildNavItem(context, Icons.favorite_rounded, Icons.favorite_border_rounded, "Likes", 2),
                        _buildNavItem(context, Icons.settings_rounded, Icons.settings_outlined, "Settings", 3),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavItem(BuildContext context, IconData activeIcon, IconData inactiveIcon, String label, int index) {
    final isSelected = _selectedIndex == index;
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final inactiveColor = isDark ? Colors.white70 : Colors.black54;
    final activeTextColor = isDark ? Colors.white : Colors.black;
    final activeBg = isDark ? Colors.white.withValues(alpha: 0.1) : Colors.black.withValues(alpha: 0.1);

    return GestureDetector(
      onTap: () => setState(() => _selectedIndex = index),
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: isSelected
            ? BoxDecoration(
                color: activeBg,
                borderRadius: BorderRadius.circular(16),
              )
            : null,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isSelected ? activeIcon : inactiveIcon,
              color: isSelected ? Colors.redAccent : inactiveColor,
              size: 24,
            ),
            if (isSelected) ...[
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  color: activeTextColor,
                  fontWeight: FontWeight.w600,
                  fontSize: 12,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}