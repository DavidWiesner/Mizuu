package com.miz.service;

import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import com.battlelancer.seriesguide.api.Action;
import com.battlelancer.seriesguide.api.Episode;
import com.battlelancer.seriesguide.api.SeriesGuideExtension;
import com.miz.mizuu.MizuuApplication;
import com.miz.mizuu.TvShowEpisodeDetails;

/**
 * Created by david on 03.12.14.
 */
public class SeriesGuidePlayExtension extends SeriesGuideExtension {
	public SeriesGuidePlayExtension() {
		super("Mizuu Play Extension");
	}


	@Override
	protected void onRequest(final int episodeIdentifier, final Episode episode) {
		Integer showTvdbId = episode.getShowTvdbId();
		Cursor cursor = MizuuApplication.getTvEpisodeDbAdapter().getEpisode(showTvdbId.toString(),
		                                                                    episode.getSeason(),
		                                                                    episode.getNumber());
		if(cursor.getCount() != 0){
			Intent intent = new Intent(this, TvShowEpisodeDetails.class);
			intent.putExtra("showId", showTvdbId.toString());
			intent.putExtra("season", episode.getSeason());
			intent.putExtra("episode", episode.getNumber());
			Log.w(getClass().getSimpleName(), "onRequest " + intent.getExtras().toString());
			DatabaseUtils.dumpCursor(cursor);
			publishAction(new Action.Builder("View on Mizuu", episodeIdentifier)
					              .viewIntent(intent)
					              .build());
		}
	}
}
