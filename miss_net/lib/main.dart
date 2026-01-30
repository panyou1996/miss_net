import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'injection_container.dart' as di;
import 'presentation/blocs/home/home_bloc.dart';
import 'presentation/pages/main/main_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Supabase
  // TODO: Replace with your actual Supabase URL and Anon Key
  await Supabase.initialize(
    url: 'https://gapmmwdbxzcglvvdhhiu.supabase.co',
    anonKey: 'sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0',
  );

  // Initialize Dependency Injection
  await di.init();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(create: (_) => di.sl<HomeBloc>()),
      ],
      child: MaterialApp(
        title: 'MissNet',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFFE50914), // Netflix Red
            brightness: Brightness.dark,
            primary: const Color(0xFFE50914),
            surface: const Color(0xFF121212),
          ),
          useMaterial3: true,
          scaffoldBackgroundColor: const Color(0xFF000000), // Pure Black for OLED
          textTheme: GoogleFonts.poppinsTextTheme(ThemeData.dark().textTheme),
          appBarTheme: const AppBarTheme(
            backgroundColor: Colors.transparent,
            elevation: 0,
            scrolledUnderElevation: 0,
          ),
        ),
        home: const MainScreen(),
      ),
    );
  }
}