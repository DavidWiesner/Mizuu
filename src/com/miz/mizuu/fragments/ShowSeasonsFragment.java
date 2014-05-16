package com.miz.mizuu.fragments;

import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jcifs.smb.SmbFile;

import com.miz.db.DbAdapterTvShow;
import com.miz.db.DbAdapterTvShowEpisode;
import com.miz.functions.AsyncTask;
import com.miz.functions.FileSource;
import com.miz.functions.IOUtils;
import com.miz.functions.MizLib;
import com.miz.mizuu.IdentifyTvShow;
import com.miz.mizuu.MizuuApplication;
import com.miz.mizuu.R;
import com.miz.mizuu.TvShow;
import com.miz.mizuu.TvShowEpisode;
import com.miz.service.DeleteFile;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Picasso.LoadedFrom;
import com.squareup.picasso.Target;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersBaseAdapter;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersGridView;

public class ShowSeasonsFragment extends Fragment {

	private int mImageThumbSize, mImageThumbSpacing, selectedEpisodeIndex, selectedFilter;
	private ArrayList<TvShowEpisode> allEpisodes = new ArrayList<TvShowEpisode>(), shownEpisodes = new ArrayList<TvShowEpisode>();
	private boolean prefsDisableEthernetWiFiCheck, isLoading = false, ignoreDeletedFiles, useGridView, showOldestEpisodeFirst;
	private StickyGridHeadersGridView mGridView;
	private ExpandableListView mListView;
	private SeasonsAdapterGrid mGridAdapter;
	private SeasonsAdapterList mListAdapter;
	private ArrayList<String> seasons = new ArrayList<String>();
	private SparseIntArray seasonEpisodeCount = new SparseIntArray();
	private ActionMode mActionMode;
	private ProgressBar pbar;
	private SharedPreferences settings;
	private boolean isPortrait, setBackground;
	private TvShow thisShow;
	private Picasso mPicasso;

	public static ShowSeasonsFragment newInstance(String showId, boolean setBackground) {
		ShowSeasonsFragment frag = new ShowSeasonsFragment();
		Bundle b = new Bundle();
		b.putString("showId", showId);
		b.putBoolean("setBackground", setBackground);
		frag.setArguments(b);		
		return frag;
	}

	public ShowSeasonsFragment() {}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);

		setBackground = getArguments().getBoolean("setBackground");

		settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

		prefsDisableEthernetWiFiCheck = settings.getBoolean("prefsDisableEthernetWiFiCheck", false);
		ignoreDeletedFiles = settings.getBoolean("prefsIgnoredFilesEnabled", false);
		IOUtils.debug(this, settings.getString("prefsSeasonsLayout", "<non>"), getString(R.string.listView), getString(R.string.gridView));
		useGridView = settings.getString("prefsSeasonsLayout", getString(R.string.listView)).equals(getString(R.string.gridView)) ? true : false;
		showOldestEpisodeFirst = settings.getString("prefsEpisodesOrder", getString(R.string.oldestFirst)).equals(getString(R.string.oldestFirst)) ? true : false;
		if (MizLib.isGoogleTV(getActivity()))
			useGridView = false;

		mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);
		if (!MizLib.isPortrait(getActivity()) && MizLib.isTablet(getActivity()))
			mImageThumbSize = (int) (getResources().getDimensionPixelSize(R.dimen.backdrop_thumbnail_width) * 0.75);
		else
			mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.backdrop_thumbnail_width);

		if (useGridView)
			mGridAdapter = new SeasonsAdapterGrid(getActivity());
		else
			mListAdapter = new SeasonsAdapterList(getActivity());

		mPicasso = MizuuApplication.getPicasso(getActivity());

		Cursor cursor = MizuuApplication.getTvDbAdapter().getShow(getArguments().getString("showId"));
		if (cursor.moveToFirst()) {
			thisShow = new TvShow(
					getActivity(),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_ID)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_TITLE)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_PLOT)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_RATING)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_GENRES)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_ACTORS)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_CERTIFICATION)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_FIRST_AIRDATE)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_RUNTIME)),
					false,
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShow.KEY_SHOW_EXTRA1))
					);
		}
		cursor.close();

		if (thisShow == null && isAdded()) {
			getActivity().finish();
			return;
		}

		loadEpisodes();

		addAllEpisodes();

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("tvshow-episode-changed"));
	}

	@Override
	public void onResume() {
		super.onResume();
		isPortrait = MizLib.isPortrait(getActivity());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (useGridView)
			return inflater.inflate(R.layout.seasons_grid, container, false);
		return inflater.inflate(R.layout.seasons_list, container, false);
	}

	private AbsListView getCollectionView() {
		if (useGridView)
			return mGridView;
		return mListView;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		if (setBackground && !MizLib.isPortrait(getActivity()) && MizLib.isTablet(getActivity()))
			view.findViewById(R.id.container).setBackgroundColor(Color.parseColor("#101010"));

		pbar = (ProgressBar) view.findViewById(R.id.progressbar);
		if (useGridView)
			mGridView = (StickyGridHeadersGridView) view.findViewById(R.id.gridView);
		else
			mListView = (ExpandableListView) view.findViewById(R.id.listView);

		if (useGridView) {
			mGridView.setAreHeadersSticky(false);
			mGridView.setAdapter(mGridAdapter);
		} else {
			mListView.setAdapter(mListAdapter);
			mListView.setGroupIndicator(null);
		}

		if (useGridView)
			mGridView.setAdapter(mGridAdapter);
		else
			mListView.setAdapter(mListAdapter);

		getCollectionView().setFastScrollEnabled(true);
		getCollectionView().setDrawSelectorOnTop(true);
		getCollectionView().setSelector(R.drawable.buttonstyle);
		if (!useGridView) {
			mListView.setOnChildClickListener(new OnChildClickListener() {
				@Override
				public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
					int position = getEpisodeNumber(groupPosition, childPosition);

					if (position >= 0) {
						if (mActionMode != null)
							mActionMode.finish();

						if (!isPortrait) {
							mListView.setItemChecked(MizLib.flatPosition(mListView, groupPosition, childPosition), true);
							selectedEpisodeIndex = position;
						}

						selectEpisode(shownEpisodes.get(position));
					}

					return true;
				}
			});
		} else {
			mGridView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					if (position >= 0) {
						if (mActionMode != null)
							mActionMode.finish();

						if (!isPortrait) {
							getCollectionView().setItemChecked(position, true);
							selectedEpisodeIndex = position;
						}

						selectEpisode(shownEpisodes.get(position));
					}
				}
			});
		}
		getCollectionView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				if (position >= 0) {
					if (!useGridView && ExpandableListView.getPackedPositionType(arg3) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) // This is a group, let's dismiss it
						return false;

					if (mActionMode != null)
						return false;

					getCollectionView().setItemChecked(position, true);

					if (!useGridView) {
						int pos = getEpisodeNumber(ExpandableListView.getPackedPositionGroup(arg3), ExpandableListView.getPackedPositionChild(mListView.getExpandableListPosition(position)));
						selectedEpisodeIndex = pos;
						selectEpisode(shownEpisodes.get(pos));
					} else {
						selectedEpisodeIndex = position;
						selectEpisode(shownEpisodes.get(position));
					}
					mActionMode = getActivity().startActionMode(mActionModeCallback);
				}
				return true;
			}
		});

		loadEpisodes(0, true);
	}

	private OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
		@Override
		public void onGlobalLayout() {
			if (mGridAdapter.getNumColumns() == 0) {
				final int numColumns = (int) Math.floor(
						mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
				mGridView.setColumnWidth(mImageThumbSize);
				if (numColumns > 0) {
					final int columnWidth = (mGridView.getWidth() / numColumns) - mImageThumbSpacing;
					mGridAdapter.setNumColumns(numColumns);
					mGridAdapter.setItemHeight(columnWidth);
				}
			}
		}
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.filter:
			showFilterDialog();
			break;
		}
		return false;
	}

	private void showFilterDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.filters)
		.setItems(R.array.actionlist_episodes, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				selectedFilter = which;
				loadEpisodes(selectedFilter, true);
			}
		});
		builder.show();
	}

	class CoverItem implements Target {
		TextView title, number;
		ImageView cover, watched, selectedOverlay;
		RelativeLayout layout;

		@Override
		public void onBitmapFailed(Drawable arg0) {
			Picasso.with(getActivity()).load("file://" + thisShow.getBackdrop()).placeholder(R.drawable.gray).error(R.drawable.nobackdrop).into(cover);
		}
		@Override
		public void onBitmapLoaded(Bitmap arg0, LoadedFrom arg1) {
			cover.setImageBitmap(arg0);
			ObjectAnimator.ofFloat(cover, "alpha", 0f, 1f).setDuration(200).start();
		}
		@Override
		public void onPrepareLoad(Drawable arg0) {
			cover.setImageDrawable(arg0);
		}
	}

	static class RowItem {
		TextView title, episodeCount;
		ImageView watched;
	}

	private class SeasonsAdapterGrid extends BaseAdapter implements StickyGridHeadersBaseAdapter {

		private int mItemHeight = 0;
		private int mNumColumns = 0;
		private GridView.LayoutParams mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		private LayoutInflater inflater;

		public SeasonsAdapterGrid(Context c) {
			inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public boolean areAllItemsEnabled() {
			return true;
		}

		@Override
		public boolean isEnabled(int position) {
			return true;
		}

		@Override
		public int getCount() {
			int count = 0;

			for (int i = 0; i < seasons.size(); i++)
				count += seasonEpisodeCount.get(Integer.valueOf(seasons.get(i)));

			return count;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			CoverItem holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.episode_grid_item, parent, false);
				holder = new CoverItem();
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.watched = (ImageView) convertView.findViewById(R.id.watched);
				holder.title = (TextView) convertView.findViewById(R.id.text);
				holder.number = (TextView) convertView.findViewById(R.id.episodeText);
				holder.layout = (RelativeLayout) convertView.findViewById(R.id.relativeLayout1);
				holder.selectedOverlay = (ImageView) convertView.findViewById(R.id.selectedOverlay);
				convertView.setTag(holder);
			} else {
				holder = (CoverItem) convertView.getTag();
			}

			holder.title.setText(shownEpisodes.get(position).getTitle());
			holder.title.setVisibility(TextView.VISIBLE);
			holder.number.setText(shownEpisodes.get(position).getEpisode());
			holder.number.setVisibility(TextView.VISIBLE);

			if (shownEpisodes.get(position).hasWatched())
				holder.watched.setVisibility(View.VISIBLE);
			else
				holder.watched.setVisibility(View.GONE);

			// Check the height matches our calculated column width
			if (holder.layout.getLayoutParams().height != mItemHeight) {
				holder.layout.setLayoutParams(mImageViewLayoutParams);
			}

			if (selectedEpisodeIndex == position && !isPortrait) {
				holder.selectedOverlay.setVisibility(View.VISIBLE);
			} else
				holder.selectedOverlay.setVisibility(View.GONE);

			mPicasso.load("file://" + shownEpisodes.get(position).getEpisodePhoto()).placeholder(R.drawable.gray).error(R.drawable.nobackdrop).config(MizuuApplication.getBitmapConfig()).into(holder);

			return convertView;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void registerDataSetObserver(DataSetObserver observer) {}

		@Override
		public void unregisterDataSetObserver(DataSetObserver observer) {}

		@Override
		public int getCountForHeader(int header) {
			return seasonEpisodeCount.get(Integer.valueOf(seasons.get(header)));
		}

		@Override
		public int getNumHeaders() {
			return seasonEpisodeCount.size();
		}

		@Override
		public View getHeaderView(final int position, View convertView, ViewGroup parent) {
			RowItem holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.seasons_row_header, parent, false);
				holder = new RowItem();
				holder.title = (TextView) convertView.findViewById(R.id.seasonTitle);
				holder.title.setVisibility(View.VISIBLE);
				holder.episodeCount = (TextView) convertView.findViewById(R.id.episodeCount);
				holder.watched = (ImageView) convertView.findViewById(R.id.watchSeason);
				convertView.setTag(holder);
			} else {
				holder = (RowItem) convertView.getTag();
			}

			if (seasons.get(position).equals("00")) {
				holder.title.setText(R.string.stringSpecials);	
			} else {
				holder.title.setText(getString(R.string.showSeason) + " " + seasons.get(position));
			}

			holder.watched.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					markSeasonAsWatched(seasons.get(position));
				}
			});

			int size = seasonEpisodeCount.get(Integer.valueOf(seasons.get(position)));
			holder.episodeCount.setText(size + " " + getResources().getQuantityString(R.plurals.episodes, size, size));

			convertView.setClickable(false);
			convertView.setFocusable(false);

			return convertView;
		}

		public void setItemHeight(int height) {
			if (height == mItemHeight) {
				return;
			}
			mItemHeight = height;
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, (int) (mItemHeight * 0.5625));
		}

		public void setNumColumns(int numColumns) {
			mNumColumns = numColumns;
		}

		public int getNumColumns() {
			return mNumColumns;
		}
	}

	private class SeasonsAdapterList extends BaseExpandableListAdapter {

		private LayoutInflater inflater;

		public SeasonsAdapterList(Context c) {
			inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public Object getChild(int groupPosition, int childPosition) {
			return null;
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return 0;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

			int position = getEpisodeNumber(groupPosition, childPosition);

			CoverItem holder;

			if (convertView == null) {

				convertView = inflater.inflate(R.layout.episode_list, parent, false);
				holder = new CoverItem();
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.watched = (ImageView) convertView.findViewById(R.id.watched);
				holder.title = (TextView) convertView.findViewById(R.id.text);
				holder.number = (TextView) convertView.findViewById(R.id.episodeText);
				holder.layout = (RelativeLayout) convertView.findViewById(R.id.relativeLayout1);
				if (useGridView)
					holder.selectedOverlay = (ImageView) convertView.findViewById(R.id.selectedOverlay);
				convertView.setTag(holder);
			} else {
				holder = (CoverItem) convertView.getTag();
			}

			holder.title.setText(shownEpisodes.get(position).getTitle());
			holder.title.setVisibility(TextView.VISIBLE);
			holder.number.setText(getString(R.string.showEpisode) + " " + shownEpisodes.get(position).getEpisode());
			holder.number.setVisibility(TextView.VISIBLE);

			if (shownEpisodes.get(position).hasWatched())
				holder.watched.setVisibility(View.VISIBLE);
			else
				holder.watched.setVisibility(View.GONE);

			mPicasso.load("file://" + shownEpisodes.get(position).getEpisodePhoto()).placeholder(R.drawable.gray).error(R.drawable.nobackdrop).config(MizuuApplication.getBitmapConfig()).into(holder.cover);

			return convertView;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return seasonEpisodeCount.get(Integer.valueOf(seasons.get(groupPosition)));
		}

		@Override
		public Object getGroup(int groupPosition) {
			return null;
		}

		@Override
		public int getGroupCount() {
			return seasons.size();
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			RowItem holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.seasons_row_header, parent, false);
				holder = new RowItem();
				holder.title = (TextView) convertView.findViewById(R.id.seasonTitle);
				holder.episodeCount = (TextView) convertView.findViewById(R.id.episodeCount);
				holder.watched = (ImageView) convertView.findViewById(R.id.watchSeason);
				convertView.setTag(holder);
			} else {
				holder = (RowItem) convertView.getTag();
			}

			if (seasons.get(groupPosition).equals("00")) {
				holder.title.setText(R.string.stringSpecials);	
			} else {
				holder.title.setText(getString(R.string.showSeason) + " " + seasons.get(groupPosition));
			}

			holder.watched.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					markSeasonAsWatched(seasons.get(groupPosition));
				}
			});

			int size = seasonEpisodeCount.get(Integer.valueOf(seasons.get(groupPosition)));
			holder.episodeCount.setText(size + " " + getResources().getQuantityString(R.plurals.episodes, size, size));

			return convertView;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

	}

	private int getEpisodeNumber(int groupPosition, int childPosition) {
		int position = 0;

		if (groupPosition > 0) {
			for (int i = 0; i < groupPosition; i++) {
				position += seasonEpisodeCount.get(Integer.valueOf(seasons.get(i)));
			}
		}

		position += childPosition;

		return position;
	}

	private void markSeasonAsWatched(final String season) {
		// Create and open database
		DbAdapterTvShowEpisode db = MizuuApplication.getTvEpisodeDbAdapter();

		if (db.markSeasonAsWatched(thisShow.getId(), season)) {
			Toast.makeText(getActivity(), getString(R.string.markedAsWatched), Toast.LENGTH_SHORT).show();

			final ArrayList<TvShowEpisode> previouslyUnwatched = new ArrayList<TvShowEpisode>();		
			for (int i = 0; i < shownEpisodes.size(); i++)
				if (shownEpisodes.get(i).getSeason().equals(season)) {
					if (!shownEpisodes.get(i).hasWatched())
						previouslyUnwatched.add(shownEpisodes.get(i));
					shownEpisodes.get(i).setHasWatched(true);
				}

			notifyDataSetChanged(true);

			new Thread() {
				@Override
				public void run() {
					if (previouslyUnwatched.size() > 0)
						MizLib.markEpisodeAsWatched(previouslyUnwatched, getActivity(), true);
				}
			}.start();

		} else {
			Toast.makeText(getActivity(), getString(R.string.errorOccured), Toast.LENGTH_SHORT).show();
		}
	}

	private void loadEpisodes() {
		allEpisodes.clear();

		// Create and open database
		DbAdapterTvShowEpisode dbHelper = MizuuApplication.getTvEpisodeDbAdapter();

		Cursor cursor = dbHelper.getAllEpisodes(getArguments().getString("showId"), showOldestEpisodeFirst ? DbAdapterTvShowEpisode.OLDEST_FIRST : DbAdapterTvShowEpisode.NEWEST_FIRST);
		while (cursor.moveToNext()) {
			allEpisodes.add(new TvShowEpisode(getActivity(),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_ROWID)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_SHOW_ID)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_FILEPATH)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_TITLE)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_PLOT)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_SEASON)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_AIRDATE)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_DIRECTOR)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_WRITER)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_GUESTSTARS)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EPISODE_RATING)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_HAS_WATCHED)),
					cursor.getString(cursor.getColumnIndex(DbAdapterTvShowEpisode.KEY_EXTRA_1))
					));
		}

		cursor.close();

		Collections.sort(allEpisodes, new Comparator<TvShowEpisode>() {
			@Override
			public int compare(TvShowEpisode o1, TvShowEpisode o2) {

				int first = Integer.valueOf(o1.getSeason() + o1.getEpisode());
				int second = Integer.valueOf(o2.getSeason() + o2.getEpisode());
				int res = 0; // Let's start off assuming they're equal

				if (first < second)
					res = -1;
				else				
					res = 1;

				return showOldestEpisodeFirst ? res : res * -1;
			}
		});

		if (allEpisodes.size() == 0) {
			getActivity().finish();
			return;
		}
	}

	private void loadEpisodes(final int type, final boolean resetSelection) {
		if (!isLoading && isAdded())
			if (resetSelection)
				setLoading(true);

		seasonEpisodeCount.clear();
		seasons.clear();

		EpisodeLoader loader = new EpisodeLoader();

		loader.setResetSelection(resetSelection);
		loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, type);
	}

	private class EpisodeLoader extends AsyncTask<Integer, Object, Object> {

		private boolean resetSelection;

		public void setResetSelection(boolean resetSelection) {
			this.resetSelection = resetSelection;
		}

		@Override
		protected Object doInBackground(Integer... params) {
			shownEpisodes.clear();

			int type = params[0];

			switch (type) {
			case 0: // All episodes
				addAllEpisodes();
				break;
			case 1: // Available files
				addAvailableFiles();
				break;
			case 2: // Watched episodes
				addWatched(true);
				break;
			case 3: // Unwatched episodes
				addWatched(false);
				break;
			}

			return null;
		}

		@Override
		protected void onPostExecute(Object result) {
			if (isAdded()) {
				notifyDataSetChanged(true);

				if (useGridView)
					mGridView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

				if (resetSelection) {

					int expandedGroup = 0;

					if (!useGridView) {
						if (seasons.size() > 1) // Check if it at least contains two seasons
							expandedGroup = seasons.get(0).equals("00") ? 1 : 0;
						mListView.expandGroup(expandedGroup);
					}

					if (MizLib.isTablet(getActivity()) && !MizLib.isPortrait(getActivity())) {						
						if (shownEpisodes.size() > 0) {
							selectEpisode(shownEpisodes.get(0));

							if (!useGridView) {
								try {
									mListView.setItemChecked(MizLib.flatPosition(mListView, expandedGroup, 0), true);
									mListView.smoothScrollToPosition(0);
								} catch (Exception e) {
									mListView.setItemChecked(MizLib.flatPosition(mListView, 0, 0), true);
									mListView.smoothScrollToPosition(0);
								}
							}
						}
					}

					setLoading(false);
				}
			}
		}
	}

	private void setLoading(boolean isLoading) {
		if (isLoading) {
			this.isLoading = true;
			pbar.setVisibility(View.VISIBLE);
			getCollectionView().setVisibility(View.GONE);
		} else {
			this.isLoading = false;
			pbar.setVisibility(View.GONE);
			getCollectionView().setVisibility(View.VISIBLE);
		}
	}

	private void addAllEpisodes() {
		int tempCount = 0;

		ArrayList<TvShowEpisode> tempEpisodes = new ArrayList<TvShowEpisode>(allEpisodes);

		for (int i = 0; i< tempEpisodes.size(); i++) {
			if (seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason())) == 0) {
				seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), 1);
				seasons.add(tempEpisodes.get(i).getSeason());
			} else {
				tempCount = seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason()));
				seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), tempCount + 1);
			}
			shownEpisodes.add(tempEpisodes.get(i));
		}

		tempEpisodes.clear();
		tempEpisodes = null;
	}

	private void addAvailableFiles() {
		ArrayList<FileSource> filesources = MizLib.getFileSources(MizLib.TYPE_SHOWS, true);	

		int tempCount = 0;

		ArrayList<TvShowEpisode> tempEpisodes = new ArrayList<TvShowEpisode>(allEpisodes);

		for (int i = 0; i< tempEpisodes.size(); i++) {
			if (tempEpisodes.get(i).isNetworkFile()) {
				if (isAdded())
					if (MizLib.isWifiConnected(getActivity(), prefsDisableEthernetWiFiCheck)) {
						FileSource source = null;

						for (int j = 0; j < filesources.size(); j++)
							if (tempEpisodes.get(i).getFilepath().contains(filesources.get(j).getFilepath())) {
								source = filesources.get(j);
								continue;
							}

						if (source == null)
							continue;

						try {
							final SmbFile file = new SmbFile(
									MizLib.createSmbLoginString(
											URLEncoder.encode(source.getDomain(), "utf-8"),
											URLEncoder.encode(source.getUser(), "utf-8"),
											URLEncoder.encode(source.getPassword(), "utf-8"),
											tempEpisodes.get(i).getFilepath(),
											false
											));
							if (file.exists()) {
								if (seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason())) == 0) {
									seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), 1);
									seasons.add(tempEpisodes.get(i).getSeason());
								} else {
									tempCount = seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason()));
									seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), tempCount + 1);
								}
								shownEpisodes.add(tempEpisodes.get(i));
							}
						} catch (Exception e) {}  // Do nothing - the file isn't available (either MalformedURLException or SmbException)
					}
			} else if (tempEpisodes.get(i).isUpnpFile()) {
				if (MizLib.exists(tempEpisodes.get(i).getFilepath())) {
					if (seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason())) == 0) {
						seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), 1);
						seasons.add(tempEpisodes.get(i).getSeason());
					} else {
						tempCount = seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason()));
						seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), tempCount + 1);
					}
					shownEpisodes.add(tempEpisodes.get(i));
				}
			} else {	
				if (new File(tempEpisodes.get(i).getFilepath()).exists()) {
					if (seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason())) == 0) {
						seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), 1);
						seasons.add(tempEpisodes.get(i).getSeason());
					} else {
						tempCount = seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason()));
						seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), tempCount + 1);
					}
					shownEpisodes.add(tempEpisodes.get(i));
				}
			}
		}

		tempEpisodes.clear();
		tempEpisodes = null;
	}

	private void addWatched(boolean hasWatched) {
		int tempCount = 0;

		ArrayList<TvShowEpisode> tempEpisodes = new ArrayList<TvShowEpisode>(allEpisodes);

		for (int i = 0; i< tempEpisodes.size(); i++) {
			if (tempEpisodes.get(i).hasWatched() == hasWatched) {
				if (seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason())) == 0) {
					seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), 1);
					seasons.add(tempEpisodes.get(i).getSeason());
				} else {
					tempCount = seasonEpisodeCount.get(Integer.valueOf(tempEpisodes.get(i).getSeason()));
					seasonEpisodeCount.append(Integer.valueOf(tempEpisodes.get(i).getSeason()), tempCount + 1);
				}
				shownEpisodes.add(tempEpisodes.get(i));
			}
		}

		tempEpisodes.clear();
		tempEpisodes = null;
	}

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.episode_details, menu);

			try {
				if (shownEpisodes.get(selectedEpisodeIndex).hasWatched()) {
					menu.findItem(R.id.watched).setTitle(R.string.stringMarkAsUnwatched);
				} else {
					menu.findItem(R.id.watched).setTitle(R.string.stringMarkAsWatched);
				}
			} catch (Exception e) {}

			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return true; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menuDeleteEpisode:
				deleteEpisode();
				break;
			case R.id.watched:
				watched(true);
				break;
			case R.id.identify:
				identifyEpisode();
			}

			mode.finish();

			return true;
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;

			// Unselected the item
			if (!(!MizLib.isPortrait(getActivity()) && MizLib.isTablet(getActivity()))) {
				getCollectionView().clearFocus();
				getCollectionView().clearChoices();
				getCollectionView().invalidate();
			}
		}
	};

	private void identifyEpisode() {
		Intent i = new Intent();
		i.setClass(getActivity(), IdentifyTvShow.class);
		i.putExtra("rowId", shownEpisodes.get(selectedEpisodeIndex).getRowId());
		i.putExtra("files", new String[]{shownEpisodes.get(selectedEpisodeIndex).getFilepath()});
		i.putExtra("isShow", false);
		startActivity(i);
	}

	private void deleteEpisode() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		View dialogLayout = getActivity().getLayoutInflater().inflate(R.layout.delete_file_dialog_layout, null);
		final CheckBox cb = (CheckBox) dialogLayout.findViewById(R.id.deleteFile);
		cb.setChecked(PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("prefsAlwaysDeleteFile", true));

		builder.setTitle(getString(R.string.removeEpisode) + " S" + shownEpisodes.get(selectedEpisodeIndex).getSeason() + "E" + shownEpisodes.get(selectedEpisodeIndex).getEpisode())
		.setView(dialogLayout)
		.setCancelable(false)
		.setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {	
				// Create and open database
				DbAdapterTvShowEpisode dbHelper = MizuuApplication.getTvEpisodeDbAdapter();
				boolean deleted = false;
				if (ignoreDeletedFiles)
					deleted = dbHelper.ignoreEpisode(shownEpisodes.get(selectedEpisodeIndex).getRowId());
				else
					deleted = dbHelper.deleteEpisode(shownEpisodes.get(selectedEpisodeIndex).getRowId());

				if (deleted) {
					try {
						// Delete episode images
						File episodePhoto = new File(MizLib.getTvShowEpisodeFolder(getActivity()), shownEpisodes.get(selectedEpisodeIndex).getShowId() + "_S" + shownEpisodes.get(selectedEpisodeIndex).getSeason() + "E" + shownEpisodes.get(selectedEpisodeIndex).getEpisode() + ".jpg");
						if (episodePhoto.exists()) {
							MizLib.deleteFile(episodePhoto);
						}
					} catch (NullPointerException e) {} // No file to delete

					if (dbHelper.getEpisodeCount(shownEpisodes.get(selectedEpisodeIndex).getShowId()) == 0) { // No more episodes for this show
						DbAdapterTvShow dbShow = MizuuApplication.getTvDbAdapter();
						boolean deletedShow = dbShow.deleteShow(shownEpisodes.get(selectedEpisodeIndex).getShowId());

						if (deletedShow) {
							MizLib.deleteFile(new File(MizLib.getTvShowThumbFolder(getActivity()), shownEpisodes.get(selectedEpisodeIndex).getShowId() + ".jpg"));
							MizLib.deleteFile(new File(MizLib.getTvShowBackdropFolder(getActivity()), shownEpisodes.get(selectedEpisodeIndex).getShowId() + "_tvbg.jpg"));
						}
					}

					if (cb.isChecked()) {
						Intent deleteIntent = new Intent(getActivity(), DeleteFile.class);
						deleteIntent.putExtra("filepath", shownEpisodes.get(selectedEpisodeIndex).getFilepath());
						getActivity().startService(deleteIntent);
					}

					notifyDatasetChanges();
					getActivity().finish();
					return;
				} else {
					Toast.makeText(getActivity(), getString(R.string.failedToRemoveEpisode), Toast.LENGTH_SHORT).show();
				}
			}
		})
		.setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		})
		.show();
	}

	private void watched(boolean showToast) {
		// Create and open database
		DbAdapterTvShowEpisode db = MizuuApplication.getTvEpisodeDbAdapter();

		shownEpisodes.get(selectedEpisodeIndex).setHasWatched(!shownEpisodes.get(selectedEpisodeIndex).hasWatched()); // Reverse the hasWatched boolean

		if (db.updateSingleItem(Long.valueOf(shownEpisodes.get(selectedEpisodeIndex).getRowId()), DbAdapterTvShowEpisode.KEY_HAS_WATCHED, shownEpisodes.get(selectedEpisodeIndex).getHasWatched())) {
			if (showToast)
				if (shownEpisodes.get(selectedEpisodeIndex).hasWatched()) {
					Toast.makeText(getActivity(), getString(R.string.markedAsWatched), Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(getActivity(), getString(R.string.markedAsUnwatched), Toast.LENGTH_SHORT).show();
				}

			notifyDataSetChanged(true);
		} else {
			if (showToast)
				Toast.makeText(getActivity(), getString(R.string.errorOccured), Toast.LENGTH_SHORT).show();
		}

		new Thread() {
			@Override
			public void run() {
				ArrayList<TvShowEpisode> episode = new ArrayList<TvShowEpisode>();
				episode.add(shownEpisodes.get(selectedEpisodeIndex));
				MizLib.markEpisodeAsWatched(episode, getActivity(), false);
			}
		}.start();
	}

	private void notifyDatasetChanges() {
		LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent("mizuu-shows-update"));
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, final Intent intent) {
			if (intent.filterEquals(new Intent("tvshow-episode-changed"))) {
				loadEpisodes();
				loadEpisodes(selectedFilter, false);
			}
		}
	};

	private void notifyDataSetChanged(boolean invalidateViews) {
		if (useGridView)
			mGridAdapter.notifyDataSetChanged();
		else
			mListAdapter.notifyDataSetChanged();
		if (invalidateViews && useGridView)
			getCollectionView().invalidateViews(); // Force a re-layout of the collection view, apparently this is required with the StickyHeaders views
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMessageReceiver);
	}

	private void selectEpisode(TvShowEpisode episode) {
		Intent i = new Intent("mizuu-show-episode-selected");
		i.putExtra("showId", episode.getShowId());
		i.putExtra("rowId", episode.getRowId());
		LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(i);
	}
}