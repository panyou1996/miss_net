import 'dart:ui';
import 'package:flutter/material.dart';
import '../home_page.dart';
import '../explore/explore_page.dart';
import '../favorites/favorites_page.dart';
import '../settings/settings_page.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _selectedIndex = 0;

  final List<Widget> _pages = [
    const HomePage(),
    const ExplorePage(),
    const FavoritesPage(),
    const SettingsPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true, // Important for content to show behind nav bar
      body: Stack(
        children: [
          // Content Layer
          IndexedStack(
            index: _selectedIndex,
            children: _pages,
          ),
          
          // Floating Glass Nav Bar Layer
          Positioned(
            left: 16,
            right: 16,
            bottom: 24, // Float from bottom
            child: ClipRRect(
              borderRadius: BorderRadius.circular(24), // Rounded pill shape
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10), // Glass blur
                child: Container(
                  height: 64,
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.7), // Translucent black
                    borderRadius: BorderRadius.circular(24),
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.1), // Subtle border
                      width: 0.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.3),
                        blurRadius: 20,
                        offset: const Offset(0, 10),
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _buildNavItem(Icons.home_rounded, Icons.home_outlined, "Home", 0),
                      _buildNavItem(Icons.explore_rounded, Icons.explore_outlined, "Explore", 1),
                      _buildNavItem(Icons.favorite_rounded, Icons.favorite_border_rounded, "Likes", 2),
                      _buildNavItem(Icons.settings_rounded, Icons.settings_outlined, "Settings", 3),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavItem(IconData activeIcon, IconData inactiveIcon, String label, int index) {
    final isSelected = _selectedIndex == index;
    return GestureDetector(
      onTap: () => setState(() => _selectedIndex = index),
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: isSelected
            ? BoxDecoration(
                color: Colors.white.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(16),
              )
            : null,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isSelected ? activeIcon : inactiveIcon,
              color: isSelected ? Colors.redAccent : Colors.white70,
              size: 24,
            ),
            if (isSelected) ...[
              const SizedBox(width: 8),
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white,
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
