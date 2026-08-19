import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'core/services/video_resolver.dart';
import 'core/services/privacy_service.dart';
import 'core/services/download_service.dart';
import 'core/services/cast_service.dart';
import 'data/datasources/local_video_datasource.dart';
import 'data/datasources/video_datasource.dart';
import 'data/repositories/video_repository_impl.dart';
import 'domain/repositories/video_repository.dart';
import 'presentation/blocs/home/home_bloc.dart';
import 'presentation/blocs/search/search_bloc.dart';
import 'presentation/blocs/theme/theme_bloc.dart';

final sl = GetIt.instance;

Future<void> init() async {
  // Blocs
  sl.registerFactory(() => HomeBloc(repository: sl()));
  sl.registerFactory(() => SearchBloc(repository: sl()));
  sl.registerFactory(() => ThemeBloc());

  // Repositories
  sl.registerLazySingleton<VideoRepository>(
    () => VideoRepositoryImpl(
      remoteDataSource: sl(),
      localDataSource: sl(),
      privacyService: sl(),
    ),
  );

  // Data Sources
  sl.registerLazySingleton<VideoDataSource>(
    () => SupabaseVideoDataSourceImpl(sl()),
  );
  sl.registerLazySingleton<LocalVideoDataSource>(
    () => LocalVideoDataSourceImpl(sl()),
  );

  // External
  final sharedPreferences = await SharedPreferences.getInstance();
  sl.registerLazySingleton(() => sharedPreferences);
  
  sl.registerLazySingleton(() => Supabase.instance.client);
  
  // Services
  sl.registerFactory(() => VideoResolver());
  sl.registerLazySingleton(() => PrivacyService(sl()));
  sl.registerLazySingleton(() => DownloadService());
  sl.registerLazySingleton(() => CastService());
}
