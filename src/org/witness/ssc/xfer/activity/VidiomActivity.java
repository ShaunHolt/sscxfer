package org.witness.ssc.xfer.activity;

/*
 * Vidiom  Activity interface 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 * http://www.infiniterecursion.com.au
 */

public abstract interface VidiomActivity {

	public abstract boolean isUploading();

	public abstract void startedUploading();

	public abstract void finishedUploading(boolean success);

	public void createNotification(String notification_text);
	
	public abstract void showProgress (String text, int percent);
}
