package com.melegy.movies.moviesapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    String[] posters;
    Collection<Movie> movies;
    private ImageAdapter imageAdapter;
    private List<Movie> movies_list = new ArrayList<>();
    private int page_num = 1;
    private GridView gridview;

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        gridview = (GridView) view.findViewById(R.id.gridview);
        imageAdapter = new ImageAdapter(getActivity());
        gridview.setAdapter(imageAdapter);

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {

                Intent intent = new Intent(getActivity(), detailActivity.class);
                Movie mMovie = movies_list.get(position);
                intent.putExtra("movie", mMovie);
                startActivity(intent);

            }
        });

        gridview.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                page_num += 1;
                updateView();
            }
        });

        return view;
    }


    @Override
    public void onStart() {
        super.onStart();
        updateView();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Boolean updated = settings.getBoolean("updated", false);
        if(updated) {
            page_num = 1;
            gridview.setAdapter(null);
            if(movies != null) {
                movies.clear();
            }
            if (movies_list != null) {
                movies_list.clear();
            }
            posters = new String[0];
            gridview.invalidateViews();
            imageAdapter = null;
            imageAdapter = new ImageAdapter(getActivity());
            updateView();
            gridview.setAdapter(imageAdapter);
            updated =false;

        }else{
            updateView();
        }
    }

    private void updateView() {

        fetchMoviesTask task = new fetchMoviesTask();
        task.execute(String.valueOf(page_num));
    }

    public class fetchMoviesTask extends AsyncTask<String, Void, Collection<Movie>> {

        private final String LOG_TAG = fetchMoviesTask.class.getSimpleName();

        @Override
        protected Collection<Movie> doInBackground(String... params) {
            if (params.length == 0) {
                return null;
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String sort_type = prefs.getString(getString(R.string.pref_sort_type_key), "popularity.desc");
            String page_num = params[0];

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String moviesJSON = null;

            try {

                final String TMDB_URI_SCHEME = "http";
                final String TMDB_URI_AUTHORITY = "api.themoviedb.org";
                final String TMDB_URI_FIRST_PATH = "3";
                final String TMDB_URI_SECOND_PATH = "discover";
                final String TMDB_URI_THIRD_PATH = "movie";
                final String API_PARAM = "api_key";
                final String SORT_PARAM = "sort_by";
                final String PAGE_PARAM = "page";

                Uri.Builder builder = new Uri.Builder();
                builder.scheme(TMDB_URI_SCHEME)
                        .authority(TMDB_URI_AUTHORITY)
                        .appendPath(TMDB_URI_FIRST_PATH)
                        .appendPath(TMDB_URI_SECOND_PATH)
                        .appendPath(TMDB_URI_THIRD_PATH)
                        .appendQueryParameter(API_PARAM, sensitiveData.API_KEY)
                        .appendQueryParameter(SORT_PARAM, sort_type)
                        .appendQueryParameter(PAGE_PARAM, page_num);

                String myUrl = builder.build().toString();
                URL url = new URL(myUrl);
                Log.i("TYPE", sort_type);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder buffer = new StringBuilder();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                moviesJSON = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getMoviesData(moviesJSON);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }


        private Collection<Movie> getMoviesData(String moviesJSON)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String MDB_LIST = "results";

            JSONObject forecastJson = new JSONObject(moviesJSON);
            JSONArray moviesArray = forecastJson.getJSONArray(MDB_LIST);

            Collection<Movie> movies = new ArrayList<>();
            for (int i = 0; i < moviesArray.length(); i++) {
                JSONObject movieObj = moviesArray.getJSONObject(i);
                Movie movie = Movie.deserialize(movieObj);
                movies.add(movie);
            }
            movies_list.addAll(movies);

            return movies;

        }

        private String[] getPosters(Collection<Movie> movies) {
            String poster_size = "w185";
            String[] postersURLS = new String[movies.size()];
            int i = 0;
            for (Movie movie : movies) {
                Uri poster_url = movie.getPosterURI(poster_size, "poster");
                postersURLS[i] = poster_url.toString();
                i++;
            }
            return postersURLS;
        }

        @Override
        protected void onPostExecute(Collection<Movie> movies) {
            if (movies != null) {
                posters = new String[0];
                posters = getPosters(movies);
                imageAdapter.addPosters(posters);
            }
        }
    }

}
