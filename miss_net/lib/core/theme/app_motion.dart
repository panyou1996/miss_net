import 'package:flutter/animation.dart';

class AppMotion {
  // Durations (M3 Standards)
  static const Duration short1 = Duration(milliseconds: 100);
  static const Duration short2 = Duration(milliseconds: 200);
  static const Duration medium1 = Duration(milliseconds: 300);
  static const Duration medium2 = Duration(milliseconds: 400);
  static const Duration long1 = Duration(milliseconds: 500);
  static const Duration long2 = Duration(milliseconds: 700);
  static const Duration long4 = Duration(milliseconds: 1000);

  // Curves (M3 Expressive)
  static const Curve standard = Curves.easeInOutCubic;
  static const Curve emphasized = Cubic(0.2, 0.0, 0.0, 1.0);
  static const Curve emphasizedDecelerate = Cubic(0.05, 0.7, 0.1, 1.0);
  static const Curve emphasizedAccelerate = Cubic(0.3, 0.0, 0.8, 0.15);
  
  // Custom Staggered Helper
  static Duration stagger(int index) => Duration(milliseconds: 50 * index);
}
