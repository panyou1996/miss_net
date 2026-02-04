import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'injection_container.dart' as di;
import 'presentation/blocs/home/home_bloc.dart';
import 'presentation/blocs/theme/theme_bloc.dart';
import 'presentation/pages/main/main_screen.dart';
import 'core/services/download_service.dart';

Future<void> main() async {
  // Ensure Flutter is ready
  WidgetsFlutterBinding.ensureInitialized();

  // Robust Initialization
  try {
    // 1. Initialize Supabase with a shorter timeout for mobile networks
    await Supabase.initialize(
      url: 'https://gapmmwdbxzcglvvdhhiu.supabase.co',
      anonKey: 'sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0',
    ).timeout(const Duration(seconds: 5));

    // 2. DI
    await di.init();

    // 3. Services (Async non-blocking)
    di.sl<DownloadService>().init().catchError((e) => debugPrint("DownloadService error: $e"));

  } catch (e) {
    debugPrint("App initialization failed: $e");
  }

  // Always run app to avoid white screen
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(create: (_) => di.sl<HomeBloc>()),
        BlocProvider(create: (_) => di.sl<ThemeBloc>()..add(LoadTheme())),
      ],
      child: BlocBuilder<ThemeBloc, ThemeState>(
        builder: (context, state) {
          return MaterialApp(
            title: 'MissNet',
            debugShowCheckedModeBanner: false,
            themeMode: state.themeMode,
            
            // Light Theme
            theme: ThemeData(
              colorScheme: ColorScheme.fromSeed(
                seedColor: const Color(0xFFE50914),
                brightness: Brightness.light,
                primary: const Color(0xFFE50914),
                surface: Colors.white,
                onSurface: Colors.black,
              ),
              useMaterial3: true,
              scaffoldBackgroundColor: Colors.white,
              textTheme: GoogleFonts.poppinsTextTheme(ThemeData.light().textTheme),
              appBarTheme: const AppBarTheme(
                backgroundColor: Colors.white,
                foregroundColor: Colors.black,
                elevation: 0,
                scrolledUnderElevation: 0,
              ),
              cardColor: Colors.grey[100],
              iconTheme: const IconThemeData(color: Colors.black),
            ),

            // Dark Theme
            darkTheme: ThemeData(
              colorScheme: ColorScheme.fromSeed(
                seedColor: const Color(0xFFE50914),
                brightness: Brightness.dark,
                primary: const Color(0xFFE50914),
                surface: const Color(0xFF121212),
                onSurface: Colors.white,
              ),
              useMaterial3: true,
              scaffoldBackgroundColor: const Color(0xFF000000),
              textTheme: GoogleFonts.poppinsTextTheme(ThemeData.dark().textTheme),
              appBarTheme: const AppBarTheme(
                backgroundColor: Colors.black,
                foregroundColor: Colors.white,
                elevation: 0,
                scrolledUnderElevation: 0,
              ),
              cardColor: Colors.grey[900],
              iconTheme: const IconThemeData(color: Colors.white),
            ),

            home: const MainScreen(),
          );
        },
      ),
    );
  }
}