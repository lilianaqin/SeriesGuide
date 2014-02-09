/*
 * Copyright 2014 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.battlelancer.seriesguide.ui;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.loaders.TmdbMovieDetailsLoader;
import com.battlelancer.seriesguide.loaders.TmdbMovieDetailsLoader.MovieDetails;
import com.battlelancer.seriesguide.settings.TmdbSettings;
import com.battlelancer.seriesguide.ui.dialogs.MovieCheckInDialogFragment;
import com.battlelancer.seriesguide.util.ImageDownloader;
import com.battlelancer.seriesguide.util.ServiceUtils;
import com.battlelancer.seriesguide.util.ShareUtils;
import com.battlelancer.seriesguide.util.Utils;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.androidutils.CheatSheet;
import com.uwetrottmann.tmdb.entities.Movie;

/**
 * Displays details about one movie including plot, ratings, trailers and a poster.
 */
public class MovieDetailsFragment extends SherlockFragment implements
        LoaderManager.LoaderCallbacks<MovieDetails> {

    public static MovieDetailsFragment newInstance(int tmdbId) {
        MovieDetailsFragment f = new MovieDetailsFragment();

        Bundle args = new Bundle();
        args.putInt(InitBundle.TMDB_ID, tmdbId);
        f.setArguments(args);

        return f;
    }

    public interface InitBundle {

        String TMDB_ID = "tmdbid";
    }

    private static final String TAG = "Movie Details";

    private static final int LOADER_ID = R.layout.movie_details_fragment;

    private ImageDownloader mImageDownloader;

    private String mBaseUrl;

    @InjectView(R.id.textViewMovieTitle) TextView mMovieTitle;

    @InjectView(R.id.textViewMovieDate) TextView mMovieReleaseDate;

    @InjectView(R.id.textViewMovieDescription) TextView mMovieDescription;

    @InjectView(R.id.imageViewMoviePoster) ImageView mMoviePosterBackground;

    @InjectView(R.id.containerMovieButtons) View mButtonContainer;

    @InjectView(R.id.buttonMovieCheckIn) ImageButton mCheckinButton;

    @InjectView(R.id.buttonMovieWatched) ImageButton mWatchedButton;

    @InjectView(R.id.buttonMovieCollected) ImageButton mCollectedButton;

    @InjectView(R.id.buttonMovieComments) Button mCommentsButton;

    @InjectView(R.id.progressBar) View mProgressBar;

    @InjectView(R.id.dividerHorizontalMovieDetails) View mDivider;

    private MovieDetails mMovieDetails;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.movie_details_fragment, container, false);
        ButterKnife.inject(this, v);

        mProgressBar.setVisibility(View.VISIBLE);

        // important action buttons
        mButtonContainer.setVisibility(View.GONE);

        // comments button
        mDivider.setVisibility(View.GONE);
        mCommentsButton.setVisibility(View.GONE);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        int tmdbId = getArguments().getInt(InitBundle.TMDB_ID);
        if (tmdbId == 0) {
            getSherlockActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        // fix padding for translucent system bars
        if (AndroidUtils.isKitKatOrHigher()) {
            SystemBarTintManager.SystemBarConfig config
                    = ((MovieDetailsActivity) getActivity()).getSystemBarTintManager().getConfig();
            ViewGroup contentContainer = (ViewGroup) getView().findViewById(
                    R.id.contentContainerMovie);
            contentContainer.setClipToPadding(false);
            contentContainer.setPadding(0, 0, config.getPixelInsetRight(),
                    config.getPixelInsetBottom());
            ViewGroup.MarginLayoutParams layoutParams
                    = (ViewGroup.MarginLayoutParams) contentContainer.getLayoutParams();
            layoutParams.setMargins(0, config.getPixelInsetTop(true), 0, 0);
            contentContainer.setLayoutParams(layoutParams);
        }

        mImageDownloader = ImageDownloader.getInstance(getActivity());

        mBaseUrl = TmdbSettings.getImageBaseUrl(getActivity()) + TmdbSettings.POSTER_SIZE_SPEC_W342;

        Bundle args = new Bundle();
        args.putInt(InitBundle.TMDB_ID, tmdbId);
        getLoaderManager().initLoader(LOADER_ID, args, this);

        setHasOptionsMenu(true);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();

        ButterKnife.reset(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mMovieDetails != null) {
            boolean isLightTheme = SeriesGuidePreferences.THEME == R.style.SeriesGuideThemeLight;
            inflater.inflate(
                    isLightTheme ? R.menu.movie_details_menu_light : R.menu.movie_details_menu,
                    menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        if (mMovieDetails != null) {
            // If the nav drawer is open, hide action items related to the
            // content view
            boolean isDrawerOpen = ((BaseNavDrawerActivity) getActivity()).isDrawerOpen();

            boolean isEnableShare = mMovieDetails.movie() != null;
            MenuItem shareItem = menu.findItem(R.id.menu_movie_share);
            shareItem.setEnabled(isEnableShare);
            shareItem.setVisible(isEnableShare && !isDrawerOpen);

            boolean isEnableImdb = mMovieDetails.movie() != null
                    && !TextUtils.isEmpty(mMovieDetails.movie().imdb_id);
            MenuItem imdbItem = menu.findItem(R.id.menu_open_imdb);
            imdbItem.setEnabled(isEnableImdb);
            imdbItem.setVisible(isEnableImdb);

            boolean isEnableYoutube = mMovieDetails.trailers() != null &&
                    mMovieDetails.trailers().youtube.size() > 0;
            MenuItem youtubeItem = menu.findItem(R.id.menu_open_youtube);
            youtubeItem.setEnabled(isEnableYoutube);
            youtubeItem.setVisible(isEnableYoutube && !isDrawerOpen);
        }

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_movie_share) {
            ShareUtils.shareMovie(getActivity(), mMovieDetails.movie().id,
                    mMovieDetails.movie().title);
            fireTrackerEvent("Share");
            return true;
        }
        if (itemId == R.id.menu_open_imdb) {
            ServiceUtils.openImdb(mMovieDetails.movie().imdb_id, TAG, getActivity());
            return true;
        }
        if (itemId == R.id.menu_open_youtube) {
            ServiceUtils.openYoutube(mMovieDetails.trailers().youtube.get(0).source, TAG,
                    getActivity());
            return true;
        }
        if (itemId == R.id.menu_open_google_play) {
            ServiceUtils.searchGooglePlay(mMovieDetails.movie().title, TAG, getActivity());
            return true;
        }
        if (itemId == R.id.menu_open_trakt) {
            ServiceUtils.openTraktMovie(getActivity(), mMovieDetails.movie().id, TAG);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<MovieDetails> onCreateLoader(int loaderId, Bundle args) {
        if (args != null) {
            int tmdbId = args.getInt(InitBundle.TMDB_ID);
            if (tmdbId != 0) {
                return new TmdbMovieDetailsLoader(getActivity(), tmdbId);
            }
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<MovieDetails> loader, MovieDetails details) {
        if (details != null) {
            mMovieDetails = details;
            onPopulateMovieDetails(details);
            // add menu items only available once the movie is
            getSherlockActivity().supportInvalidateOptionsMenu();
        }
    }

    @Override
    public void onLoaderReset(Loader<MovieDetails> loader) {
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void onPopulateMovieDetails(MovieDetails details) {
        mProgressBar.setVisibility(View.GONE);

        final Movie movie = details.movie();

        if (movie != null) {
            // set non-content views visible
            mDivider.setVisibility(View.VISIBLE);

            mMovieTitle.setText(movie.title);

            if (movie.release_date != null) {
                mMovieReleaseDate.setText(DateUtils.formatDateTime(getActivity(),
                        movie.release_date.getTime(),
                        DateUtils.FORMAT_SHOW_DATE));
            } else {
                mMovieReleaseDate.setText("");
            }

            mMovieDescription.setText(movie.overview);

            if (!TextUtils.isEmpty(movie.poster_path)) {
                if (AndroidUtils.isJellyBeanOrHigher()) {
                    mMoviePosterBackground.setImageAlpha(30);
                } else {
                    mMoviePosterBackground.setAlpha(30);
                }

                String posterPath = mBaseUrl + movie.poster_path;
                mImageDownloader.download(posterPath, mMoviePosterBackground, false);
            }

            mButtonContainer.setVisibility(View.VISIBLE);
            CheatSheet.setup(mCheckinButton);
            if (!TextUtils.isEmpty(movie.imdb_id)) {
                mCheckinButton.setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // display a check-in dialog
                                MovieCheckInDialogFragment f = MovieCheckInDialogFragment
                                        .newInstance(movie.imdb_id, movie.title);
                                f.show(getFragmentManager(), "checkin-dialog");
                                fireTrackerEvent("Check-In");
                            }
                        });
            } else {
                mCheckinButton.setEnabled(false);
            }

            mCommentsButton.setVisibility(View.VISIBLE);
            mCommentsButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent i = new Intent(getActivity(), TraktShoutsActivity.class);
                    i.putExtras(TraktShoutsActivity.createInitBundleMovie(movie.title, movie.id));
                    startActivity(i);
                    fireTrackerEvent("Comments");
                }
            });
        }
    }

    private void fireTrackerEvent(String label) {
        Utils.trackAction(getActivity(), TAG, label);
    }
}
