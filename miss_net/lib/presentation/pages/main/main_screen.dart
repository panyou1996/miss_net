import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
    
    // iOS 26 High-End Glass Palette
    final glassColor = isDark ? Colors.black.withValues(alpha: 0.78) : Colors.white.withValues(alpha: 0.88);
    final borderColor = isDark ? Colors.white.withValues(alpha: 0.1) : Colors.black.withValues(alpha: 0.05);
    final shadowColor = Colors.black.withValues(alpha: 0.45);

    return PopScope(
      canPop: _selectedIndex == 0,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && _selectedIndex != 0) {
          setState(() => _selectedIndex = 0);
        }
      },
      child: Scaffold(
        extendBody: true,
        body: NotificationListener<ScrollNotification>(
          onNotification: (notification) {
            if (notification is ScrollUpdateNotification) {
              if (notification.scrollDelta! > 15 && _isNavbarVisible.value) {
                _isNavbarVisible.value = false;
              } else if (notification.scrollDelta! < -15 && !_isNavbarVisible.value) {
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
              
              // Refined Minimalist Island (No Background Selection Box)
              ValueListenableBuilder<bool>(
                valueListenable: _isNavbarVisible,
                builder: (context, isVisible, child) {
                  return AnimatedPositioned(
                    duration: const Duration(milliseconds: 600),
                    curve: Curves.easeInOutCubic,
                    left: 32,
                    right: 32,
                    bottom: isVisible ? 36 : -100,
                    child: child!,
                  );
                },
                child: Container(
                  height: 64,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(32),
                    boxShadow: [
                      BoxShadow(color: shadowColor, blurRadius: 40, offset: const Offset(0, 20)),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(32),
                    child: BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        decoration: BoxDecoration(
                          color: glassColor, 
                          borderRadius: BorderRadius.circular(32),
                          border: Border.all(color: borderColor, width: 0.5),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _buildNavItem(Icons.home_rounded, Icons.home_outlined, 0),
                            _buildNavItem(Icons.explore_rounded, Icons.explore_outlined, 1),
                            _buildNavItem(Icons.favorite_rounded, Icons.favorite_border_rounded, 2),
                            _buildNavItem(Icons.settings_rounded, Icons.settings_outlined, 3),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(IconData activeIcon, IconData inactiveIcon, int index) {
    final isSelected = _selectedIndex == index;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return GestureDetector(
      onTap: () {
        if (_selectedIndex != index) {
          HapticFeedback.mediumImpact();
          setState(() => _selectedIndex = index);
        }
      },
      behavior: HitTestBehavior.opaque,
      child: Container(
        width: 50,
        height: 50,
        alignment: Alignment.center,
        child: Stack(
          alignment: Alignment.center,
          children: [
            // Simplified: Just Color and Scale
            AnimatedScale(
              scale: isSelected ? 1.25 : 1.0,
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeOutBack,
              child: Icon(
                isSelected ? activeIcon : inactiveIcon,
                color: isSelected ? Colors.redAccent : (isDark ? Colors.white38 : Colors.black38),
                size: 26,
              ),
            ),
            
            if (isSelected)
              Positioned(
                bottom: 4,
                child: Container(
                  width: 4,
                  height: 4,
                  decoration: const BoxDecoration(
                    color: Colors.redAccent,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
