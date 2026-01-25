import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';

// Events
abstract class HomeEvent extends Equatable {
  const HomeEvent();
  @override
  List<Object> get props => [];
}

class LoadRecentVideos extends HomeEvent {}

// States
abstract class HomeState extends Equatable {
  const HomeState();
  @override
  List<Object> get props => [];
}

class HomeInitial extends HomeState {}
class HomeLoading extends HomeState {}
class HomeLoaded extends HomeState {
  final List<Video> videos;
  const HomeLoaded(this.videos);
  @override
  List<Object> get props => [videos];
}
class HomeError extends HomeState {
  final String message;
  const HomeError(this.message);
  @override
  List<Object> get props => [message];
}

// Bloc
class HomeBloc extends Bloc<HomeEvent, HomeState> {
  final VideoRepository repository;

  HomeBloc({required this.repository}) : super(HomeInitial()) {
    on<LoadRecentVideos>((event, emit) async {
      emit(HomeLoading());
      final result = await repository.getRecentVideos();
      result.fold(
        (failure) => emit(HomeError(failure.message)),
        (videos) => emit(HomeLoaded(videos)),
      );
    });
  }
}
