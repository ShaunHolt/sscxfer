package org.witness.ssc.xfer.activity;

import org.witness.ssc.xfer.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

/*
 * Preferences 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 * http://www.infiniterecursion.com.au
 */

public class PreferencesActivity extends PreferenceActivity {

	private static final String TAG = "RoboticEye-PreferencesActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		Log.d(TAG, " preferences onCreate");

	}
}
