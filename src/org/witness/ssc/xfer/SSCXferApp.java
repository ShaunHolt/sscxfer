package org.witness.ssc.xfer;

/*
 * Main Vidiom Application 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 */

import android.app.Application;
import android.util.Log;

public class SSCXferApp extends Application {

	
	private static String TAG = "RoboticEye-MainApp";


	private boolean isUploading;

	/*
	 * On application startup, get the home position from the preferences.
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "*** onCreate called ***");


		isUploading = false;
	}

	public void onTerminate() {
		Log.i(TAG, "*** OnTerminate called ***");
		super.onTerminate();
	}


	public boolean isUploading() {
		return isUploading;
	}

	public void setUploading() {
		isUploading = true;
	}

	public void setNotUploading() {
		isUploading = false;
	}
}
