import 'package:get_it/get_it.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'core/services/video_resolver.dart';
import 'data/datasources/video_datasource.dart';
import 'data/repositories/video_repository_impl.dart';
import 'domain/repositories/video_repository.dart';
import 'presentation/blocs/home/home_bloc.dart';
import 'presentation/blocs/search/search_bloc.dart';

final sl = GetIt.instance;

Future<void> init() async {
  // Blocs
  sl.registerFactory(() => HomeBloc(repository: sl()));
  sl.registerFactory(() => SearchBloc(repository: sl()));

  // Repositories
  sl.registerLazySingleton<VideoRepository>(
    () => VideoRepositoryImpl(remoteDataSource: sl()),
  );

  // Data Sources
  sl.registerLazySingleton<VideoDataSource>(
    () => SupabaseVideoDataSourceImpl(sl()),
  );

  // External
  // Supabase client is initialized in main.dart, but we register the instance here
  sl.registerLazySingleton(() => Supabase.instance.client);
  
  // Services
  sl.registerFactory(() => VideoResolver());
}
