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

    return Scaffold(
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
            
            // The Refined Floating Island
            ValueListenableBuilder<bool>(
              valueListenable: _isNavbarVisible,
              builder: (context, isVisible, child) {
                return AnimatedPositioned(
                  duration: const Duration(milliseconds: 600),
                  curve: Curves.easeInOutCubic,
                  left: 28,
                  right: 24,
                  bottom: isVisible ? 36 : -100,
                  child: child!,
                );
              },
              child: Container(
                height: 70,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(35),
                  boxShadow: [
                    BoxShadow(color: shadowColor, blurRadius: 40, offset: const Offset(0, 20)),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(35),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      decoration: BoxDecoration(
                        color: glassColor, 
                        borderRadius: BorderRadius.circular(35),
                        border: Border.all(color: borderColor, width: 0.5),
                      ),
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          // Fluid "Liquid Mercury" Indicator
                          _buildFluidIndicator(context),
                          
                          // Nav Items
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              _buildNavItem(Icons.home_rounded, Icons.home_outlined, 0),
                              _buildNavItem(Icons.explore_rounded, Icons.explore_outlined, 1),
                              _buildNavItem(Icons.favorite_rounded, Icons.favorite_border_rounded, 2),
                              _buildNavItem(Icons.settings_rounded, Icons.settings_outlined, 3),
                            ],
                          ),
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
    );
  }

  Widget _buildFluidIndicator(BuildContext context) {
    final double width = MediaQuery.of(context).size.width - 64; 
    final double itemWidth = width / 4;
    
    return AnimatedPositioned(
      duration: const Duration(milliseconds: 550),
      curve: Curves.easeOutQuart, // Much smoother than elastic
      left: _selectedIndex * itemWidth + (itemWidth - 56) / 2,
      child: Container(
        width: 56,
        height: 48,
        decoration: BoxDecoration(
          color: Colors.red.withValues(alpha: 0.08), // Lower saturation
          borderRadius: BorderRadius.circular(20), // Squircle-like
          boxShadow: [
            BoxShadow(
              color: Colors.red.withValues(alpha: 0.1),
              blurRadius: 20,
              spreadRadius: 2,
            )
          ],
          border: Border.all(color: Colors.red.withValues(alpha: 0.15), width: 0.5),
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
          HapticFeedback.mediumImpact(); // Stronger but concise haptic
          setState(() => _selectedIndex = index);
        }
      },
      behavior: HitTestBehavior.opaque,
      child: Container(
        width: 60,
        height: 60,
        alignment: Alignment.center,
        child: Stack(
          alignment: Alignment.center,
          children: [
            // Active Soft Glow
            if (isSelected)
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0.0, end: 1.0),
                duration: const Duration(milliseconds: 500),
                builder: (context, value, child) {
                  return Opacity(
                    opacity: value * 0.35,
                    child: Container(
                      width: 20,
                      height: 20,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: Colors.redAccent.withValues(alpha: 0.4), 
                            blurRadius: 25 * value, 
                            spreadRadius: 8 * value
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            
            // Animated Icon
            AnimatedScale(
              scale: isSelected ? 1.15 : 1.0,
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeOutBack,
              child: Icon(
                isSelected ? activeIcon : inactiveIcon,
                color: isSelected ? Colors.redAccent : (isDark ? Colors.white38 : Colors.black38),
                size: 24,
              ),
            ),
            
            // Indicator Breathing Dot
            if (isSelected)
              Positioned(
                bottom: 10,
                child: Container(
                  width: 3,
                  height: 3,
                  decoration: BoxDecoration(
                    color: Colors.redAccent.withValues(alpha: 0.8),
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