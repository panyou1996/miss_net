import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:rxdart/rxdart.dart';
import '../../../domain/entities/video.dart';
import '../../../domain/repositories/video_repository.dart';

// Events
abstract class SearchEvent extends Equatable {
  const SearchEvent();
  @override
  List<Object> get props => [];
}

class SearchQueryChanged extends SearchEvent {
  final String query;
  final bool saveToHistory;
  const SearchQueryChanged(this.query, {this.saveToHistory = true});
  @override
  List<Object> get props => [query, saveToHistory];
}

class FetchSuggestions extends SearchEvent {
  final String query;
  const FetchSuggestions(this.query);
  @override
  List<Object> get props => [query];
}

class LoadSearchHistory extends SearchEvent {}
class ClearSearchHistory extends SearchEvent {}

// States
abstract class SearchState extends Equatable {
  const SearchState();
  @override
  List<Object> get props => [];
}

class SearchInitial extends SearchState {}
class SearchHistoryLoaded extends SearchState {
  final List<String> history;
  const SearchHistoryLoaded(this.history);
  @override
  List<Object> get props => [history];
}
class SearchLoading extends SearchState {}
class SearchLoaded extends SearchState {
  final List<Video> videos;
  const SearchLoaded(this.videos);
  @override
  List<Object> get props => [videos];
}
class SearchSuggestionsLoaded extends SearchState {
  final List<String> suggestions;
  const SearchSuggestionsLoaded(this.suggestions);
  @override
  List<Object> get props => [suggestions];
}
class SearchError extends SearchState {
  final String message;
  const SearchError(this.message);
  @override
  List<Object> get props => [message];
}

// Bloc
class SearchBloc extends Bloc<SearchEvent, SearchState> {
  final VideoRepository repository;
  String? _lastQuery;

  SearchBloc({required this.repository}) : super(SearchInitial()) {
    on<LoadSearchHistory>((event, emit) async {
      final result = await repository.getSearchHistory();
      result.fold(
        (f) => emit(const SearchHistoryLoaded([])),
        (history) => emit(SearchHistoryLoaded(history)),
      );
    });

    on<ClearSearchHistory>((event, emit) async {
      await repository.clearSearchHistory();
      emit(const SearchHistoryLoaded([]));
    });

    on<SearchQueryChanged>(
      (event, emit) async {
        if (event.query.isEmpty) {
          _lastQuery = null;
          add(LoadSearchHistory());
          return;
        }

        if (event.query == _lastQuery && state is SearchLoaded) {
          return;
        }

        _lastQuery = event.query;
        if (event.saveToHistory) {
          await repository.saveSearch(event.query);
        }

        emit(SearchLoading());
        final result = await repository.searchVideos(event.query);
        result.fold(
          (failure) => emit(SearchError(failure.message)),
          (videos) => emit(SearchLoaded(videos)),
        );
      },
      transformer: (events, mapper) {
        return events.debounceTime(const Duration(milliseconds: 500)).asyncExpand(mapper);
      },
    );

    on<FetchSuggestions>(
      (event, emit) async {
        if (event.query.isEmpty) {
          emit(SearchInitial());
          return;
        }
        final result = await repository.getSearchSuggestions(event.query);
        result.fold(
          (failure) => {},
          (suggestions) => emit(SearchSuggestionsLoaded(suggestions)),
        );
      },
      transformer: (events, mapper) {
        return events.debounceTime(const Duration(milliseconds: 300)).asyncExpand(mapper);
      },
    );
  }
}