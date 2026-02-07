import 'package:flutter/foundation.dart';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:flutter_displaymode/flutter_displaymode.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'injection_container.dart' as di;
import 'presentation/blocs/home/home_bloc.dart';
import 'presentation/blocs/theme/theme_bloc.dart';
import 'presentation/pages/main/main_screen.dart';
import 'core/services/download_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // M3 Status Bar
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.dark, 
    statusBarBrightness: Brightness.light,
  ));

  try {
    if (!kIsWeb && Platform.isAndroid) {
      try {
        await FlutterDisplayMode.setHighRefreshRate();
      } catch (e) {
        debugPrint("DisplayMode Error: $e");
      }
    }

    await Supabase.initialize(
      url: 'https://gapmmwdbxzcglvvdhhiu.supabase.co',
      anonKey: 'sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0',
    ).timeout(const Duration(seconds: 5));

    await di.init();
    di.sl<DownloadService>().init().catchError((e) => debugPrint("DownloadService error: $e"));

  } catch (e) {
    debugPrint("App initialization failed: $e");
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  TextTheme _buildTextTheme(TextTheme base) {
    return GoogleFonts.robotoTextTheme(base).copyWith(
      displayLarge: GoogleFonts.robotoSerif( // Use Serif for Headlines
        fontSize: 57,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.25,
      ),
      displayMedium: GoogleFonts.robotoSerif(
        fontSize: 45,
        fontWeight: FontWeight.bold,
        letterSpacing: 0.0,
      ),
      headlineLarge: GoogleFonts.robotoSerif(
        fontSize: 32,
        fontWeight: FontWeight.bold,
        letterSpacing: -0.5, // tracking-tight
      ),
      // Body text uses Roboto
      bodyLarge: GoogleFonts.roboto(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.5,
      ),
      labelLarge: GoogleFonts.roboto(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        letterSpacing: 1.25, // tracking-wider for buttons/tags
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // HTML Prototype Palette
    const brandRed = Color(0xFFFF5A5F); // HTML Primary
    const lightBg = Color(0xFFF7F2FA);  // HTML Light BG
    const darkBg = Color(0xFF111111);   // HTML Dark BG
    const darkSurface = Color(0xFF1C1B1F); // HTML Dark Card

    return MultiBlocProvider(
      providers: [
        BlocProvider(create: (_) => di.sl<HomeBloc>()),
        BlocProvider(create: (_) => di.sl<ThemeBloc>()..add(LoadTheme())),
      ],
      child: DynamicColorBuilder(
        builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
          ColorScheme lightScheme;
          ColorScheme darkScheme;

          if (lightDynamic != null && darkDynamic != null) {
            lightScheme = lightDynamic.harmonized().copyWith(
              primary: brandRed,
              surface: lightBg,
              surfaceContainerLow: Colors.white, // Card Light
            );
            darkScheme = darkDynamic.harmonized().copyWith(
              primary: brandRed,
              surface: darkBg,
              surfaceContainerLow: darkSurface, // Card Dark
            );
          } else {
            lightScheme = ColorScheme.fromSeed(
              seedColor: brandRed, 
              brightness: Brightness.light,
              surface: lightBg,
              surfaceContainerLow: Colors.white,
            );
            darkScheme = ColorScheme.fromSeed(
              seedColor: brandRed, 
              brightness: Brightness.dark,
              surface: darkBg,
              surfaceContainerLow: darkSurface,
            );
          }

          return BlocBuilder<ThemeBloc, ThemeState>(
            builder: (context, state) {
              return MaterialApp(
                title: 'MissNet',
                debugShowCheckedModeBanner: false,
                themeMode: state.themeMode,
                
                theme: ThemeData(
                  useMaterial3: true,
                  colorScheme: lightScheme,
                  scaffoldBackgroundColor: lightScheme.surface,
                  textTheme: _buildTextTheme(ThemeData.light().textTheme),
                  cardTheme: CardTheme(
                    color: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                  ),
                  appBarTheme: const AppBarTheme(
                    backgroundColor: Colors.transparent,
                    elevation: 0,
                    centerTitle: true,
                  ),
                ),

                darkTheme: ThemeData(
                  useMaterial3: true,
                  colorScheme: darkScheme,
                  scaffoldBackgroundColor: darkBg,
                  textTheme: _buildTextTheme(ThemeData.dark().textTheme),
                  cardTheme: CardTheme(
                    color: darkSurface,
                    elevation: 0,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                  ),
                  appBarTheme: const AppBarTheme(
                    backgroundColor: Colors.transparent,
                    elevation: 0,
                    centerTitle: true,
                  ),
                ),

                home: const MainScreen(),
              );
            },
          );
        }
      ),
    );
  }
}
