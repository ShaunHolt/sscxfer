package org.witness.ssc.xfer.activity;

import java.io.File;
import java.util.ArrayList;

import org.witness.ssc.xfer.R;
import org.witness.ssc.xfer.SSCXferApp;
import org.witness.ssc.xfer.utils.DBUtils;
import org.witness.ssc.xfer.utils.DatabaseHelper;
import org.witness.ssc.xfer.utils.PublishingUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/*
 * Vidiom Library Activity 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 * http://www.infiniterecursion.com.au
 */

public class LibraryActivity extends ListActivity implements SSCXferActivity {

	// Database
	DBUtils dbutils;
	private String[] video_absolutepath;
	private String[] video_filename;
	private Integer[] video_ids;
	private String[] hosted_urls;

	private boolean videos_available;

	// Context MENU
	private static final int MENU_ITEM_1 = Menu.FIRST;
	private static final int MENU_ITEM_2 = MENU_ITEM_1 + 1;
	private static final int MENU_ITEM_3 = MENU_ITEM_2 + 1;
	private static final int MENU_ITEM_4 = MENU_ITEM_3 + 1;
	private static final int MENU_ITEM_5 = MENU_ITEM_4 + 1;
	private static final int MENU_ITEM_6 = MENU_ITEM_5 + 1;
	private static final int MENU_ITEM_7 = MENU_ITEM_6 + 1;
	private static final int MENU_ITEM_8 = MENU_ITEM_7 + 1;
	private static final int MENU_ITEM_9 = MENU_ITEM_8 + 1;

	// options MENU
	private static final int MENU_ITEM_10 = MENU_ITEM_9 + 1;
	private static final int MENU_ITEM_11 = MENU_ITEM_10 + 1;

	// Context MENU
	private static final int MENU_ITEM_12 = MENU_ITEM_11 + 1;
	private static final int MENU_ITEM_13 = MENU_ITEM_12 + 1;
	// options MENU
	private static final int MENU_ITEM_14 = MENU_ITEM_13 + 1;
	

	private static final String TAG = "RoboticEye-Library";
	private static final int NOTIFICATION_ID = 42;
	private PublishingUtils pu;

	private Handler handler;
	private String emailPreference;
	private SimpleCursorAdapter listAdapter;
	private Cursor libraryCursor;

	private Thread thread_vb;
	private Thread thread_ftp;
	private Thread thread_youtube;

	private SSCXferApp mainapp;

	private String movieurl;
	private String moviefilename;
	private String hosted_url;
	private long sdrecord_id;

	private SharedPreferences prefs;

	private Resources res;

	private boolean importPreference;

	ProgressBar progressBar;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		res = getResources();
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		emailPreference = prefs.getString("emailPreference", null);
		importPreference = prefs.getBoolean("importPreference", true);

		Log.d(TAG, " onCreate ");
		dbutils = new DBUtils(getBaseContext());
		pu = new PublishingUtils(getResources(), dbutils);
		handler = new Handler();
		thread_vb = null;
		thread_ftp = null;
		thread_youtube = null;

	
	}

	private class ImporterThread implements Runnable {

		public void run() {
			// Kick off the importing of existing videos.
			scanForExistingVideosAndImport();

			reloadList();

			// Set importPreference to false, to stop it running again.
			Editor editor = prefs.edit();
			editor.putBoolean("importPreference", false);
			editor.commit();
		}

		public void scanForExistingVideosAndImport() {
			boolean mExternalStorageAvailable = false;
			boolean mExternalStorageWriteable = false;
			File rootfolder = Environment.getExternalStorageDirectory();

			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				mExternalStorageAvailable = mExternalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				mExternalStorageAvailable = true;
				mExternalStorageWriteable = false;
			} else {
				mExternalStorageAvailable = mExternalStorageWriteable = false;
			}

			// OK, start
			if (mExternalStorageAvailable) {
				Log.d(TAG, "Starting import. OUR Directory path is : "
						+ Environment.getExternalStorageDirectory()
								.getAbsolutePath()
						+ res.getString(R.string.rootSDcardFolder));
				directoryScanRecurse(rootfolder);
			}

			Log.d(TAG, " Import FINISHED !");
		}

		public void directoryScanRecurse(File directory) {

			if (directory == null) {
				return;
			}

			// Recursive routine for finding existing videos.
			// Dont import from our own directory.
			// Log.d(TAG, "Scanning directory " + directory.getAbsolutePath());
			if (!directory.getAbsolutePath().equals(
					Environment.getExternalStorageDirectory().getAbsolutePath()
							+ res.getString(R.string.rootSDcardFolder))) {

				File[] files = directory.listFiles();

				if (files == null) {
					return;
				}

				for (File f : files) {
					if (f.isDirectory()) {
						// recurse
						directoryScanRecurse(f);
					} else if (f.isFile()) {
						// Check its a video file
						String name = f.getName();
						if (name.matches(".*\\.mp4")
								|| name.matches(".*\\.3gp")) {
							// found a video file.
							// add it to the library.
							// Log.d(TAG, "Found " + f.getAbsolutePath());

							// Check this path isnt already in DB
							String[] fp = new String[] { f.getAbsolutePath() };
							boolean alreadyInDB = dbutils.checkFilePathInDB(fp);

							// If not, insert a record into the DB
							if (!alreadyInDB) {
								String filename = f.getName();
								dbutils
										.createSDFileRecordwithNewVideoRecording(
												f.getAbsolutePath(), filename,
												0, "unknown", "", "");
							}
						}

					}

				}

			} else {
				// shouldnt log the directories files.

				// Log.w(TAG, "Not logging.");
				return;
			}

			return;
		}

	}

	public void onResume() {
		super.onResume();

		mainapp = (SSCXferApp) getApplication();
		Log.d(TAG, " onResume ");
		setContentView(R.layout.library_layout);

		makeCursorAndAdapter();

		registerForContextMenu(getListView());

		if (importPreference) {
			ImporterThread importer = new ImporterThread();
			importer.run();
		}

		

	}

	private class VideoFilesSimpleCursorAdapter extends SimpleCursorAdapter {

		public VideoFilesSimpleCursorAdapter(Context context, int layout,
				Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);

		}

		@Override
		public void setViewImage(ImageView v, String s) {

			/*
			 * String[] thumbColumns = { MediaStore.Video.Thumbnails.DATA,
			 * MediaStore.Video.Thumbnails.VIDEO_ID };
			 * 
			 * Cursor thumbCursor = managedQuery(
			 * MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, thumbColumns,
			 * MediaStore.Video.Thumbnails.VIDEO_ID + "=" + id, null, null);
			 */
			//Log.d(TAG, "We have " + s);

			String[] mediaColumns = { MediaStore.Video.Media._ID,
					MediaStore.Video.Media.DATA, MediaStore.Video.Media.TITLE,
					MediaStore.Video.Media.MIME_TYPE };

			Cursor thumb_cursor = managedQuery(
					MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaColumns,
					MediaStore.Video.Media.DATA + " = ? ", new String[] { s },
					null);

			if (thumb_cursor.moveToFirst()) {
				// Found entry for video via file path. Now we have its
				// video_id, we can check for a cache thumbnail or request to
				// generate one.

				// XXX Check cache, and load from filepath, using code above.
				/*
				Log
						.d(
								TAG,
								" We have id "
										+ thumb_cursor
												.getLong(thumb_cursor
														.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));
				 */
				Bitmap bm = MediaStore.Video.Thumbnails
						.getThumbnail(
								getContentResolver(),
								thumb_cursor
										.getLong(thumb_cursor
												.getColumnIndexOrThrow(MediaStore.Video.Media._ID)),
								MediaStore.Video.Thumbnails.MICRO_KIND, null);
				if (bm != null && v != null) {
					v.setImageBitmap(bm);
				}

			} else {
				// set default icon
				v.setImageResource(R.drawable.icon);
			}

		}
	}

	private void makeCursorAndAdapter() {
		dbutils.genericWriteOpen();

		// This query is for videofiles only, no joins.

		// libraryCursor = dbutils.generic_write_db.query(
		// DatabaseHelper.SDFILERECORD_TABLE_NAME, null, null, null, null,
		// null, DatabaseHelper.SDFileRecord.DEFAULT_SORT_ORDER);

		// SELECT
		/*
		String join_sql = " SELECT a.filename as filename , a.filepath as filepath, a.length_secs as length_secs , a.created_datetime as created_datetime, a._id as _id, a.title as title, a.description as description, "
				+ " b.host_uri as host_uri , b.host_video_url as host_video_url FROM "
				+ " videofiles a LEFT OUTER JOIN hosts b ON "
				+ " a._id = b.sdrecord_id "
				+ " ORDER BY a.created_datetime DESC ";
*/
		String join_sql = " SELECT a.filename as filename , a.filepath as filepath, a.created_datetime as created_datetime, a._id as _id,"
				+ " b.host_uri as host_uri , b.host_video_url as host_video_url FROM "
				+ " videofiles a LEFT OUTER JOIN hosts b ON "
				+ " a._id = b.sdrecord_id "
				+ " ORDER BY a.created_datetime DESC ";
		
		libraryCursor = dbutils.generic_write_db.rawQuery(join_sql, null);

		if (libraryCursor.moveToFirst()) {
			ArrayList<Integer> video_ids_al = new ArrayList<Integer>();
			ArrayList<String> video_paths_al = new ArrayList<String>();
			ArrayList<String> video_filenames_al = new ArrayList<String>();
			ArrayList<String> hosted_urls_al = new ArrayList<String>();

			do {
				long video_id = libraryCursor
						.getLong(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord._ID));
				video_ids_al.add((int) video_id);

				String video_path = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILEPATH));
				video_paths_al.add(video_path);

				String video_filename = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILENAME));
				video_filenames_al.add(video_filename);

				String hosted_url = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.HostDetails.HOST_VIDEO_URL));
				hosted_urls_al.add(hosted_url);

			} while (libraryCursor.moveToNext());

			video_ids = video_ids_al.toArray(new Integer[video_ids_al.size()]);
			video_absolutepath = video_paths_al
					.toArray(new String[video_paths_al.size()]);
			video_filename = video_filenames_al
					.toArray(new String[video_filenames_al.size()]);
			hosted_urls = hosted_urls_al.toArray(new String[hosted_urls_al
					.size()]);

			videos_available = true;

		} else {

			videos_available = false;

		}

		// Make Cursor Adapter

		String[] from = new String[] {
				DatabaseHelper.SDFileRecord.FILENAME,
			//	DatabaseHelper.SDFileRecord.LENGTH_SECS,
				DatabaseHelper.SDFileRecord.CREATED_DATETIME,

				// Linked HOSTs details
				DatabaseHelper.HostDetails.HOST_VIDEO_URL,

//				DatabaseHelper.SDFileRecord.TITLE,
//				DatabaseHelper.SDFileRecord.DESCRIPTION,
				DatabaseHelper.SDFileRecord.FILEPATH, };

		int[] to = new int[] { android.R.id.text1, 
				//android.R.id.text2,
				R.id.text3, R.id.text4,
				// , R.id.text5, R.id.text6,
				R.id.videoThumbnailimageView };

		listAdapter = new VideoFilesSimpleCursorAdapter(this,
				R.layout.library_list_item, libraryCursor, from, to);

		listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor,
					int columnIndex) {
				// Transform the text4 specifically, from a blank entry
				// repr. into a string saying "not uploaded yet"
				if (columnIndex == cursor
						.getColumnIndexOrThrow(DatabaseHelper.HostDetails.HOST_VIDEO_URL)) {
					String url = cursor
							.getString(cursor
									.getColumnIndexOrThrow(DatabaseHelper.HostDetails.HOST_VIDEO_URL));
					TextView host_details = (TextView) view
							.findViewById(R.id.text4);

					if ("".equals(url) || url == null || url.length() <= 0) {
						url = "Not uploaded yet.";
					}
					host_details.setText(url);

					return true;
				}

				// Transform the text3 specifically, from time in millis to text
				// repr.
				if (columnIndex == cursor
						.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME)) {
					long time_in_mills = cursor
							.getLong(cursor
									.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME));
					TextView datetime = (TextView) view
							.findViewById(R.id.text3);
					datetime.setText(PublishingUtils.showDate(time_in_mills));
					return true;
				}
				return false;
			}
		});

		setListAdapter(listAdapter);

		dbutils.close();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, " onDestroy ");
		libraryCursor.close();
		dbutils.close();
	}

	@Override
	public void onPause() {

		super.onDestroy();
		Log.d(TAG, "On pause");

		if (thread_vb != null) {
			Log.d(TAG, "Interrupting videobin thread");
			thread_vb.interrupt();
		}
		if (thread_ftp != null) {
			Log.d(TAG, "Interrupting ftp thread");
			thread_ftp.interrupt();
		}
		if (thread_youtube != null) {
			Log.d(TAG, "Interrupting youtube thread");
			thread_youtube.interrupt();
		}

	}

	@Override
	protected void onListItemClick(ListView l, View v, final int position,
			long id) {
		super.onListItemClick(l, v, position, id);

		// play this selection.
		String movieurl = video_absolutepath[(int) position];
		Log.d(TAG, " operation on " + movieurl);

		pu.launchVideoPlayer(this, movieurl);
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {

		// PLAY
		menu.add(0, MENU_ITEM_1, 0, R.string.library_menu_play);

		// TITLE / DESCRIPTION
		menu.add(0, MENU_ITEM_8, 0, R.string.rename_video);

		// PUBLISHING OPTIONS
		menu.add(0, MENU_ITEM_3, 0, R.string.menu_publish_to_videobin);

		menu.add(0, MENU_ITEM_7, 0, R.string.menu_youtube);

	//	menu.add(0, MENU_ITEM_6, 0, R.string.menu_ftp);

		menu.add(0, MENU_ITEM_4, 0, R.string.menu_send_via_email);

		menu.add(0, MENU_ITEM_9, 0, R.string.menu_send_hosted_url_via_email);

		menu.add(0, MENU_ITEM_12, 0, R.string.menu_launch_hosted_url);

		// DELETE
		menu.add(0, MENU_ITEM_2, 0, R.string.library_menu_delete);

	}

	public boolean onContextItemSelected(MenuItem item) {

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		Log
				.d(TAG, " got " + item.getItemId() + " at position "
						+ info.position);

		if (!videos_available) {
			return true;
		}

		movieurl = video_absolutepath[info.position];
		sdrecord_id = video_ids[info.position];
		moviefilename = video_filename[info.position];
		hosted_url = hosted_urls[info.position];
		Log.d(TAG, " operation on " + movieurl + " id " + sdrecord_id
				+ " filename " + moviefilename + " hosted url " + hosted_url);

		switch (item.getItemId()) {

		case MENU_ITEM_1:
			// play
			pu.launchVideoPlayer(this, movieurl);
			break;

		case MENU_ITEM_2:
			// delete

			// ask if sure they want to delete ?
			AlertDialog delete_dialog = new AlertDialog.Builder(this)
					.setMessage(R.string.really_delete_video)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

									// deleting files,
									if (!pu.deleteVideo(movieurl)) {
										Log.w(TAG, "Cant delete file "
												+ movieurl);

									}
									// and removing DB records!
									if (dbutils.deleteSDFileRecord(sdrecord_id) == -1) {
										Log.w(TAG, "Cant delete record "
												+ sdrecord_id);
									}

									reloadList();

								}
							}).setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

								}
							}).show();

			break;

		case MENU_ITEM_3:
			// publish to video bin
			String[] strs_vb = dbutils
					.getTitleAndDescriptionFromID(new String[] { Long
							.toString(sdrecord_id) });
			// grab thread
			thread_vb = pu.videoUploadToVideoBin(this, handler, movieurl,
					strs_vb[0], strs_vb[1] + "\n"
							+ getString(R.string.uploaded_by_),
					emailPreference, sdrecord_id);
			break;

		case MENU_ITEM_4:
			// email
			pu.launchEmailIntentWithCurrentVideo(this, movieurl);
			break;

	

		case MENU_ITEM_6:
			// FTP server upload
			thread_ftp = pu.videoUploadToFTPserver(this, handler,
					moviefilename, movieurl, emailPreference, sdrecord_id);

			break;

		case MENU_ITEM_7:
			// youtube

			String possibleEmail = null;
			// We need a linked google account for youtube.
			Account[] accounts = AccountManager.get(this).getAccountsByType(
					"com.google");
			
			/*
			for (Account account : accounts) {
				// TODO: Check possibleEmail against an email regex or treat
				// account.name as an email address only for certain
				// account.type values.
				possibleEmail = account.name;
				Log.d(TAG, "Could use : " + possibleEmail);
			}*/
			
			possibleEmail = accounts[0].name;
			
			if (possibleEmail != null) {
				Log.d(TAG, "Using account name for youtube upload .. "
						+ possibleEmail);
				// This launches the youtube upload process
				pu.getYouTubeAuthTokenWithPermissionAndUpload(this,
						possibleEmail, movieurl, handler, emailPreference,
						sdrecord_id);
			} else {

				// throw up dialog
				AlertDialog no_email = new AlertDialog.Builder(this)
						.setMessage(R.string.no_email_account_for_youtube)
						.setPositiveButton(R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {

									}
								}).show();
			}
			break;

		case MENU_ITEM_8:
			// Title and Description of Video

			showTitleDescriptionDialog();

			break;

		case MENU_ITEM_9:
			// Email the HOSTED URL field of the currently selected video

			if (hosted_url != null && hosted_url.length() > 0) {
				Intent i = new Intent(Intent.ACTION_SEND);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.setType("message/rfc822");
				i.putExtra(Intent.EXTRA_TEXT, hosted_url);
				this.startActivity(i);
			} else {
				Toast
						.makeText(
								this,
								R.string.video_is_not_uploaded_yet_no_hosted_url_available_,
								Toast.LENGTH_LONG).show();
			}

			break;

		case MENU_ITEM_12:
			// View the HOSTED URL field of the currently selected video in a
			// web browser.

			if (hosted_url != null && hosted_url.length() > 0) {
				Intent i2 = new Intent(Intent.ACTION_VIEW);
				i2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i2.setData(Uri.parse(hosted_url));
				this.startActivity(i2);
			} else {
				Toast
						.makeText(
								this,
								R.string.video_is_not_uploaded_yet_no_hosted_url_available_,
								Toast.LENGTH_LONG).show();
			}
			break;

		
		}

		return true;

	}

	private void showTitleDescriptionDialog() {
		// Launch Title/Description Edit View
		LayoutInflater inflater = (LayoutInflater) getApplicationContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		final View title_descr = inflater
				.inflate(R.layout.title_and_desc, null);
		// preload any existing title and description
		String[] strs = dbutils
				.getTitleAndDescriptionFromID(new String[] { Long
						.toString(sdrecord_id) });
		final EditText title_edittext = (EditText) title_descr
				.findViewById(R.id.EditTextTitle);
		final EditText desc_edittext = (EditText) title_descr
				.findViewById(R.id.EditTextDescr);

		title_edittext.setText(strs[0]);
		desc_edittext.setText(strs[1]);

		final Dialog d = new Dialog(this);
		Window w = d.getWindow();
		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
				WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		d.setTitle(R.string.rename_video);
		// the edit layout defined in an xml file (in res/layout)
		d.setContentView(title_descr);
		// Cancel
		Button cbutton = (Button) d.findViewById(R.id.button2Cancel);
		cbutton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				d.dismiss();
			}
		});
		// Edit
		Button ebutton = (Button) d.findViewById(R.id.button1Edit);
		ebutton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// save title and description to DB.
				String title_str = title_edittext.getText().toString();
				String desc_str = desc_edittext.getText().toString();
				String[] ids = new String[] { Long.toString(sdrecord_id) };

				Log.d(TAG, "New title and description is " + title_str + ":"
						+ desc_str);

				dbutils.updateTitleAndDescription(title_str, desc_str, ids);

				reloadList();

				d.dismiss();
			}
		});
		d.show();
	}

	private void reloadList() {
		// Refresh the list view
		runOnUiThread(new Runnable() {
			public void run() {

				makeCursorAndAdapter();

				listAdapter.notifyDataSetChanged();

			}
		});
	}

	public boolean isUploading() {
		return mainapp.isUploading();
	}

	public void startedUploading() {
		// Show notification of uploading
		Resources res = getResources();
		this.createNotification(res.getString(R.string.starting_upload) + " "
				+ moviefilename);

		mainapp.setUploading();
	}
	
	private Notification notification;
	
	public void showProgress (String txt, int percent)
	{
		  // configure the intent
        
        final NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(
                getApplicationContext().NOTIFICATION_SERVICE);
        
        if (notification == null)
        {
            notificationManager.cancel(NOTIFICATION_ID);

        	Intent intent = new Intent(this, LibraryActivity.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

	        // configure the notification
	        notification = new Notification(R.drawable.icon, txt, System
	                .currentTimeMillis());
	        notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
	        notification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.download_progress);
	        notification.contentIntent = pendingIntent;
	        notification.contentView.setImageViewResource(R.id.status_icon, R.drawable.icon);
        }

        notification.contentView.setTextViewText(R.id.status_text, res.getString(R.string.now_uploading) + " "
				+ moviefilename);
        notification.contentView.setProgressBar(R.id.status_progress, 100, percent, false);

        // inform the progress bar of updates in progress
        notificationManager.notify(NOTIFICATION_ID, notification);
        
        if (percent == 100)
        {
            notificationManager.cancel(NOTIFICATION_ID);

        	notification = null;
        }

	}

	public void finishedUploading(boolean success) {
		// Reload the gallery list
		reloadList();

		mainapp.setNotUploading();
	}

	public void createNotification(String notification_text) {
		Resources res = getResources();
		CharSequence contentTitle = res.getString(R.string.notification_title);
		CharSequence contentText = notification_text;

		final Notification notifyDetails = new Notification(R.drawable.icon,
				notification_text, System.currentTimeMillis());

		Intent notifyIntent = new Intent(this, LibraryActivity.class);

		PendingIntent intent = PendingIntent.getActivity(this, 0, notifyIntent,
				PendingIntent.FLAG_UPDATE_CURRENT
						| Notification.FLAG_AUTO_CANCEL);

		notifyDetails.setLatestEventInfo(this, contentTitle, contentText,
				intent);
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mNotificationManager.notify(NOTIFICATION_ID, notifyDetails);

	}

	

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		Log.i(TAG, "OnPrepareOptionsMenu called");

		menu.clear();

		addConstantMenuItems(menu);

		return true;
	}

	private void addConstantMenuItems(Menu menu) {

		MenuItem menu_about = menu.add(0, MENU_ITEM_5, 0, R.string.menu_about);
		menu_about.setIcon(R.drawable.wizard48);
		
		MenuItem menu_prefs = menu.add(0, MENU_ITEM_6, 0,
				R.string.menu_preferences);
		menu_prefs.setIcon(R.drawable.options);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuitem) {
		int menuNum = menuitem.getItemId();

		Log.d("MENU", "Option " + menuNum + " selected");

		switch (menuitem.getItemId()) {

		// Importing
		case MENU_ITEM_10:
			ImporterThread importer = new ImporterThread();
			importer.run();
			break;

		case MENU_ITEM_5:
			// ABOUT
			String mesg = getString(R.string.about_this);
			// find&replace VERSION
			mesg = mesg.replace("VERSION", "version " + getVersionName());

			final SpannableString s = new SpannableString(mesg);
			Linkify.addLinks(s, Linkify.ALL);

			// Licenses
			String mesg2 = getString(R.string.third_party_licenses);

			final SpannableString s2 = new SpannableString(mesg2);
			Linkify.addLinks(s2, Linkify.ALL);

			AlertDialog about = new AlertDialog.Builder(this).setMessage(s)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

								}
							}).setNegativeButton(R.string.licenses,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

									AlertDialog licenses = new AlertDialog.Builder(
											LibraryActivity.this)
											.setMessage(s2)
											.setPositiveButton(
													R.string.yes,
													new DialogInterface.OnClickListener() {
														public void onClick(
																DialogInterface dialog,
																int whichButton) {

														}
													}).show();

									// makes links work
									((TextView) licenses
											.findViewById(android.R.id.message))
											.setMovementMethod(LinkMovementMethod
													.getInstance());

								}
							}).show();

			// makes links work
			((TextView) about.findViewById(android.R.id.message))
					.setMovementMethod(LinkMovementMethod.getInstance());

			break;

		case MENU_ITEM_6:

			// Preferences
			Intent intent = new Intent().setClass(this,
					PreferencesActivity.class);
			this.startActivityForResult(intent, 0);

			break;

			
		}
		return true;
	}
	
	public int getVersionCode() {
		int v = 0;
		try {
			v = getPackageManager().getPackageInfo(
					getApplicationInfo().packageName, 0).versionCode;
		} catch (NameNotFoundException e) {
			// Huh? Really?
		}
		return v;
	}

	public String getVersionName() {
		String name = null;
		try {
			name = getPackageManager().getPackageInfo(
					getApplicationInfo().packageName, 0).versionName;
		} catch (NameNotFoundException e) {
			// Huh? Really?
		}
		return name;
	}

}
