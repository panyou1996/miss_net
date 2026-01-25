import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:url_launcher/url_launcher.dart';
import '../blocs/search/search_bloc.dart';
import '../pages/player_page.dart';
import '../widgets/video_card.dart';

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
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              childAspectRatio: 0.7,
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

  @override
  Widget buildSuggestions(BuildContext context) {
    return Container(color: Colors.black);
  }
}
