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
    return Theme.of(context).copyWith(
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        hintStyle: TextStyle(color: Colors.white54),
        border: InputBorder.none,
      ),
      textTheme: const TextTheme(
        titleLarge: TextStyle(color: Colors.white, fontSize: 18),
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
    
    return BlocBuilder<SearchBloc, SearchState>(
      bloc: searchBloc,
      builder: (context, state) {
        if (state is SearchLoading) {
          return const Center(child: CircularProgressIndicator(color: Colors.red));
        } else if (state is SearchError) {
          return Center(child: Text(state.message, style: const TextStyle(color: Colors.white)));
        } else if (state is SearchLoaded) {
          if (state.videos.isEmpty) {
            return const Center(child: Text("No results found.", style: TextStyle(color: Colors.white)));
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
    );
  }

  Widget _hotTag(BuildContext context, String label) {
    return ActionChip(
      label: Text(label),
      backgroundColor: Colors.grey[900],
      labelStyle: const TextStyle(color: Colors.white, fontSize: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      onPressed: () {
        query = label;
        showResults(context);
      },
    );
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    if (query.isEmpty) {
      return Container(
        color: Colors.black,
        child: ListView(
          children: [
            const Padding(
              padding: EdgeInsets.all(16.0),
              child: Text("Popular Categories", style: TextStyle(color: Colors.grey, fontWeight: FontWeight.bold)),
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
        ),
      );
    }

    searchBloc.add(FetchSuggestions(query));

    return BlocBuilder<SearchBloc, SearchState>(
      bloc: searchBloc,
      builder: (context, state) {
        if (state is SearchSuggestionsLoaded) {
          final suggestions = state.suggestions;
          if (suggestions.isEmpty) return Container(color: Colors.black);

          return Container(
            color: Colors.black,
            child: ListView.builder(
              itemCount: suggestions.length,
              itemBuilder: (context, index) {
                final suggestion = suggestions[index];
                return ListTile(
                  leading: const Icon(Icons.search, color: Colors.red),
                  title: Text(
                    suggestion,
                    style: const TextStyle(color: Colors.white),
                  ),
                  onTap: () {
                    query = suggestion;
                    showResults(context);
                  },
                );
              },
            ),
          );
        }
        return Container(color: Colors.black);
      },
    );
  }
}