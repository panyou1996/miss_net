import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
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
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.red),
          useMaterial3: true,
          scaffoldBackgroundColor: Colors.black,
        ),
        home: const MainScreen(),
      ),
    );
  }
}