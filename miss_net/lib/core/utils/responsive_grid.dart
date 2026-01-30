import 'package:flutter/material.dart';

class ResponsiveGrid {
  static int getCrossAxisCount(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    if (width > 1600) return 6;
    if (width > 1200) return 5;
    if (width > 900) return 4;
    if (width > 600) return 3;
    return 2;
  }
}
