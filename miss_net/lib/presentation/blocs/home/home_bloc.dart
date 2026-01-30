import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/entities/home_section.dart';
import '../../../domain/repositories/video_repository.dart';

// Events
abstract class HomeEvent extends Equatable {
  const HomeEvent();
  @override
  List<Object?> get props => [];
}

class LoadRecentVideos extends HomeEvent {}

// States
abstract class HomeState extends Equatable {
  const HomeState();
  @override
  List<Object?> get props => [];
}

class HomeInitial extends HomeState {}
class HomeLoading extends HomeState {}
class HomeLoaded extends HomeState {
  final List<HomeSection> sections;
  final Video? featuredVideo;
  final List<Video> continueWatching;

  const HomeLoaded({
    required this.sections,
    this.featuredVideo,
    this.continueWatching = const [],
  });

  @override
  List<Object?> get props => [sections, featuredVideo, continueWatching];
}
class HomeError extends HomeState {
  final String message;
  const HomeError(this.message);
  @override
  List<Object?> get props => [message];
}

// Bloc
class HomeBloc extends Bloc<HomeEvent, HomeState> {
  final VideoRepository repository;

  HomeBloc({required this.repository}) : super(HomeInitial()) {
    on<LoadRecentVideos>(_onLoadRecentVideos);
  }

  Future<void> _onLoadRecentVideos(LoadRecentVideos event, Emitter<HomeState> emit) async {
    emit(HomeLoading());
    
    // Fetch multiple sections in parallel including History
    final results = await Future.wait([
      repository.getRecentVideos(limit: 15, category: 'new'),
      repository.getRecentVideos(limit: 10, category: 'monthly_hot'),
      repository.getRecentVideos(limit: 10, category: 'weekly_hot'),
      repository.getRecentVideos(limit: 10, category: 'uncensored'),
      repository.getHistory(),
    ]);

    final List<HomeSection> sections = [];
    Video? featured;
    List<Video> history = [];

    // Process 'New' for Banner and Row
    results[0].fold(
      (f) => null,
      (videos) {
        if (videos.isNotEmpty) {
          featured = videos.first;
          sections.add(HomeSection(title: "New Releases", category: 'new', videos: videos));
        }
      }
    );

    // Process others (1, 2, 3)
    final titles = ["Monthly Hot", "Weekly Hot", "Uncensored"];
    final categories = ["monthly_hot", "weekly_hot", "uncensored"];

    for (int i = 1; i <= 3; i++) {
      results[i].fold(
        (f) => null,
        (videos) {
          if (videos.isNotEmpty) {
            sections.add(HomeSection(title: titles[i-1], category: categories[i-1], videos: videos));
          }
        }
      );
    }

    // Process History (Index 4)
    results[4].fold((f) => null, (videos) => history = videos);

    if (sections.isEmpty) {
      emit(const HomeError("Failed to load content"));
    } else {
      emit(HomeLoaded(sections: sections, featuredVideo: featured, continueWatching: history));
    }
  }
}
