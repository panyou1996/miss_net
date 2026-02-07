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

  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    statusBarBrightness: Brightness.dark,
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
    return GoogleFonts.interTextTheme(base).copyWith(
      displayLarge: GoogleFonts.playfairDisplay(
        fontSize: 57,
        fontWeight: FontWeight.w900, // Extra Bold for impact
        letterSpacing: -0.25,
        height: 1.1,
      ),
      displayMedium: GoogleFonts.playfairDisplay(
        fontSize: 45,
        fontWeight: FontWeight.w800,
        letterSpacing: 0.0,
        height: 1.15,
      ),
      displaySmall: GoogleFonts.playfairDisplay(
        fontSize: 36,
        fontWeight: FontWeight.bold,
        letterSpacing: 0.0,
        height: 1.2,
      ),
      headlineLarge: GoogleFonts.playfairDisplay(
        fontSize: 32,
        fontWeight: FontWeight.w800,
        letterSpacing: 0.0,
        height: 1.25,
      ),
      // Body text uses Inter (Clean, modern sans-serif)
      bodyLarge: GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.5,
        height: 1.5,
      ),
      bodyMedium: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.25,
        height: 1.43,
      ),
      labelLarge: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w600, // Semi-bold for buttons
        letterSpacing: 0.1,
        height: 1.43,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const brandRed = Color(0xFFE50914);

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
            lightScheme = lightDynamic.harmonized().copyWith(primary: brandRed);
            darkScheme = darkDynamic.harmonized().copyWith(primary: brandRed);
          } else {
            lightScheme = ColorScheme.fromSeed(seedColor: brandRed);
            darkScheme = ColorScheme.fromSeed(seedColor: brandRed, brightness: Brightness.dark);
          }

          darkScheme = darkScheme.copyWith(
            surface: Colors.black,
            onSurface: Colors.white,
            surfaceContainer: const Color(0xFF121212),
            surfaceContainerLow: const Color(0xFF1C1C1E),
          );

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
                  appBarTheme: const AppBarTheme(
                    backgroundColor: Colors.transparent,
                    elevation: 0,
                    centerTitle: true,
                  ),
                ),

                darkTheme: ThemeData(
                  useMaterial3: true,
                  colorScheme: darkScheme,
                  scaffoldBackgroundColor: Colors.black,
                  textTheme: _buildTextTheme(ThemeData.dark().textTheme),
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