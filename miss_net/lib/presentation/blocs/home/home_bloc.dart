import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:rxdart/rxdart.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';

// Events
abstract class HomeEvent extends Equatable {
  const HomeEvent();
  @override
  List<Object> get props => [];
}

class LoadRecentVideos extends HomeEvent {}

class LoadMoreVideos extends HomeEvent {}

class ChangeCategory extends HomeEvent {
  final String category;
  const ChangeCategory(this.category);
  @override
  List<Object> get props => [category];
}

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
  final bool hasReachedMax;
  final String selectedCategory;

  const HomeLoaded({
    required this.videos, 
    this.hasReachedMax = false,
    this.selectedCategory = 'new',
  });

  HomeLoaded copyWith({
    List<Video>? videos,
    bool? hasReachedMax,
    String? selectedCategory,
  }) {
    return HomeLoaded(
      videos: videos ?? this.videos,
      hasReachedMax: hasReachedMax ?? this.hasReachedMax,
      selectedCategory: selectedCategory ?? this.selectedCategory,
    );
  }

  @override
  List<Object> get props => [videos, hasReachedMax, selectedCategory];
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
    on<LoadRecentVideos>(_onLoadRecentVideos);
    on<LoadMoreVideos>(_onLoadMoreVideos, transformer: _throttleDroppable(const Duration(milliseconds: 100)));
  }

  EventTransformer<E> _throttleDroppable<E>(Duration duration) {
    return (events, mapper) {
      return events.throttleTime(duration).flatMap(mapper);
    };
  }

  Future<void> _onLoadRecentVideos(LoadRecentVideos event, Emitter<HomeState> emit) async {
    emit(HomeLoading());
    // Default to 'new' or no category logic
    final result = await repository.getRecentVideos(offset: 0, limit: 20); 
    result.fold(
      (failure) => emit(HomeError(failure.message)),
      (videos) => emit(HomeLoaded(videos: videos, hasReachedMax: videos.length < 20)),
    );
  }

  Future<void> _onLoadMoreVideos(LoadMoreVideos event, Emitter<HomeState> emit) async {
    if (state is HomeLoaded) {
      final currentState = state as HomeLoaded;
      if (currentState.hasReachedMax) return;

      final currentVideoCount = currentState.videos.length;
      final result = await repository.getRecentVideos(
        offset: currentVideoCount, 
        limit: 20,
      );

      result.fold(
        (failure) => emit(HomeError(failure.message)),
        (newVideos) {
          if (newVideos.isEmpty) {
            emit(currentState.copyWith(hasReachedMax: true));
          } else {
            emit(currentState.copyWith(
              videos: List.of(currentState.videos)..addAll(newVideos),
              hasReachedMax: false,
            ));
          }
        },
      );
    }
  }
}
