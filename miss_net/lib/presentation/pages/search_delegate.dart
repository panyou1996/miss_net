import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:url_launcher/url_launcher.dart';
import '../blocs/search/search_bloc.dart';
import '../pages/player_page.dart';
import '../widgets/video_card.dart';
import '../../core/utils/responsive_grid.dart';

class VideoSearchDelegate extends SearchDelegate {
  final SearchBloc searchBloc;

  VideoSearchDelegate(this.searchBloc);

  @override
  ThemeData appBarTheme(BuildContext context) {
    final theme = Theme.of(context);
    return theme.copyWith(
      appBarTheme: theme.appBarTheme.copyWith(
        backgroundColor: theme.scaffoldBackgroundColor,
        foregroundColor: theme.colorScheme.onSurface,
      ),
      inputDecorationTheme: InputDecorationTheme(
        hintStyle: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.5)),
        border: InputBorder.none,
      ),
    );
  }

  @override
  List<Widget>? buildActions(BuildContext context) {
    return [
      IconButton(
        icon: const Icon(Icons.clear),
        onPressed: () {
          query = '';
          searchBloc.add(const SearchQueryChanged(''));
        },
      ),
    ];
  }

  @override
  Widget? buildLeading(BuildContext context) {
    return IconButton(
      icon: const Icon(Icons.arrow_back),
      onPressed: () {
        close(context, null);
      },
    );
  }

  @override
  Widget buildResults(BuildContext context) {
    searchBloc.add(SearchQueryChanged(query));
    final theme = Theme.of(context);
    
    return Container(
      color: theme.scaffoldBackgroundColor,
      child: BlocBuilder<SearchBloc, SearchState>(
        bloc: searchBloc,
        builder: (context, state) {
          if (state is SearchLoading) {
            return const Center(child: CircularProgressIndicator(color: Colors.red));
          } else if (state is SearchError) {
            return Center(child: Text(state.message, style: TextStyle(color: theme.colorScheme.onSurface)));
          } else if (state is SearchLoaded) {
            if (state.videos.isEmpty) {
              return Center(child: Text("No results found.", style: TextStyle(color: theme.colorScheme.onSurface.withValues(alpha: 0.5))));
            }
            return GridView.builder(
              padding: const EdgeInsets.all(10),
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: ResponsiveGrid.getCrossAxisCount(context),
                childAspectRatio: 1.5,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
              ),
              itemCount: state.videos.length,
              itemBuilder: (context, index) {
                final video = state.videos[index];
                return VideoCard(
                  video: video,
                  onTap: () {
                    if (kIsWeb) {
                       launchUrl(Uri.parse(video.sourceUrl));
                    } else {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => PlayerPage(video: video)),
                      );
                    }
                  },
                );
              },
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }

  Widget _hotTag(BuildContext context, String label) {
    final theme = Theme.of(context);
    return ActionChip(
      label: Text(label),
      backgroundColor: theme.cardColor,
      labelStyle: TextStyle(color: theme.colorScheme.onSurface, fontSize: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: BorderSide(color: theme.dividerColor.withValues(alpha: 0.1))),
      onPressed: () {
        query = label;
        showResults(context);
      },
    );
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    final theme = Theme.of(context);
    if (query.isEmpty) {
      searchBloc.add(LoadSearchHistory());
      return Container(
        color: theme.scaffoldBackgroundColor,
        child: BlocBuilder<SearchBloc, SearchState>(
          bloc: searchBloc,
          builder: (context, state) {
            final List<String> history = (state is SearchHistoryLoaded) ? state.history : [];
            
            return ListView(
              children: [
                if (history.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text("Recent Searches", style: TextStyle(color: theme.hintColor, fontWeight: FontWeight.bold)),
                        IconButton(
                          icon: const Icon(Icons.delete_sweep_outlined, size: 20),
                          onPressed: () => searchBloc.add(ClearSearchHistory()),
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Wrap(
                      spacing: 10,
                      children: history.map((h) => ActionChip(
                        label: Text(h),
                        backgroundColor: theme.cardColor,
                        onPressed: () {
                          query = h;
                          showResults(context);
                        },
                      )).toList(),
                    ),
                  ),
                ],
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text("Popular Categories", style: TextStyle(color: theme.hintColor, fontWeight: FontWeight.bold)),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Wrap(
                    spacing: 10,
                    children: [
                      _hotTag(context, "School"),
                      _hotTag(context, "Office"),
                      _hotTag(context, "Mature"),
                      _hotTag(context, "Exclusive"),
                      _hotTag(context, "Nympho"),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      );
    }

    searchBloc.add(FetchSuggestions(query));

    return Container(
      color: theme.scaffoldBackgroundColor,
      child: BlocBuilder<SearchBloc, SearchState>(
        bloc: searchBloc,
        builder: (context, state) {
          if (state is SearchSuggestionsLoaded) {
            final suggestions = state.suggestions;
            if (suggestions.isEmpty) return const SizedBox.shrink();

            return ListView.builder(
              itemCount: suggestions.length,
              itemBuilder: (context, index) {
                final suggestion = suggestions[index];
                return ListTile(
                  leading: const Icon(Icons.search, color: Colors.red),
                  title: Text(
                    suggestion,
                    style: TextStyle(color: theme.colorScheme.onSurface),
                  ),
                  onTap: () {
                    query = suggestion;
                    showResults(context);
                  },
                );
              },
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}