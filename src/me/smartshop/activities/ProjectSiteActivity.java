package me.smartshop.activities;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import android.speech.tts.TextToSpeech;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import me.smartshop.Logger;
import me.smartshop.R;
import me.smartshop.exceptions.SiteNotFoundException;
import me.smartshop.exceptions.WifiException;
import me.smartshop.model.AccessPoint;
import me.smartshop.model.BssidResult;
import me.smartshop.model.Location;
import me.smartshop.model.ProjectSite;
import me.smartshop.model.WifiScanResult;
import me.smartshop.model.helper.DatabaseHelper;
import me.smartshop.trilateration.WeightedCentroidTrilateration;
import me.smartshop.userlocation.LocationChangeListener;
import me.smartshop.userlocation.LocationServiceFactory;
import me.smartshop.userlocation.StepDetection;
import me.smartshop.userlocation.StepDetectionProvider;
import me.smartshop.userlocation.StepDetector;
import me.smartshop.view.AccessPointDrawable;
import me.smartshop.view.MultiTouchDrawable;
import me.smartshop.view.MultiTouchView;
import me.smartshop.view.NorthDrawable;
import me.smartshop.view.OkCallback;
import me.smartshop.view.RefreshableView;
import me.smartshop.view.ScaleLineDrawable;
import me.smartshop.view.SiteMapDrawable;
import me.smartshop.view.UserDrawable;
import me.smartshop.wifi.WifiResultCallback;
import me.smartshop.wifi.WifiScanner;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

public class ProjectSiteActivity extends Activity implements OnClickListener,
		WifiResultCallback, RefreshableView, LocationChangeListener {

	protected Logger log = new Logger(ProjectSiteActivity.class);

	public static final String SITE_KEY = "SITE";

	public static final String PROJECT_KEY = "PROJECT";

	public static final String SCAN_INTERVAL = "scan_interval";

	// public static final int START_NEW = 1, START_LOAD = 2;

	protected static final int DIALOG_TITLE = 1, DIALOG_SCANNING = 2,
			DIALOG_CHANGE_SIZE = 3, DIALOG_SET_BACKGROUND = 4,
			DIALOG_SET_SCALE_OF_MAP = 5, DIALOG_ADD_KNOWN_AP = 6,
			DIALOG_SELECT_BSSIDS = 7, DIALOG_FRESH_SITE = 8,
			DIALOG_ASK_CHANGE_SCALE = 9, DIALOG_ASK_FOR_NORTH = 10,
			DIALOG_CHANGE_SCAN_INTERVAL = 11;

	protected static final int MESSAGE_REFRESH = 1, MESSAGE_START_WIFISCAN = 2,
			MESSAGE_PERSIST_RESULT = 3;

	protected static final int FILEBROWSER_REQUEST = 1;

	protected int schedulerTime = 10;

	protected MultiTouchView multiTouchView;

	protected SiteMapDrawable map;

	protected ProjectSite site;

	protected DatabaseHelper databaseHelper = null;

	protected Dao<ProjectSite, Integer> projectSiteDao = null;

	protected AlertDialog scanAlertDialog;

	protected ImageView scanningImageView;

	protected boolean ignoreWifiResults = false;

	protected BroadcastReceiver wifiBroadcastReceiver;

	protected UserDrawable user;

	protected ScaleLineDrawable scaler = null;

	protected final Context context = this;

	protected TextView backgroundPathTextView;

	protected float scalerDistance;

	public TextToSpeech tts;

	public String txt;

	protected TriangulationTask triangulationTask = null;

	protected StepDetectionProvider stepDetectionProvider = null;

	public StepDetection sd = new StepDetection(context, stepDetectionProvider,
			scalerDistance, scalerDistance, schedulerTime);

	public StepDetector detector = new StepDetector(scalerDistance,
			scalerDistance, schedulerTime);

	protected NorthDrawable northDrawable = null;

	protected Handler messageHandler;

	protected final ScheduledExecutorService scheduler = Executors
			.newScheduledThreadPool(1);

	protected Runnable wifiRunnable;

	protected ScheduledFuture<?> scheduledTask = null;

	protected ArrayList<WifiScanResult> unsavedScanResults;

	protected boolean walkingAndScanning = false;

	protected boolean freshSite = false;

	protected boolean trackSteps = true;

	int count = 0;

	ImageView path;

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			this.setContentView(R.layout.project_site);
			super.onCreate(savedInstanceState);
			path = (ImageView) findViewById(R.id.imageView1);

			Intent intent = this.getIntent();

			int siteId = intent.getExtras().getInt(SITE_KEY, -1);
			if (siteId == -1) {
				throw new SiteNotFoundException(
						"ProjectSiteActivity called without a correct site ID!");
			}

			databaseHelper = OpenHelperManager.getHelper(this,
					DatabaseHelper.class);
			projectSiteDao = databaseHelper.getDao(ProjectSite.class);
			site = projectSiteDao.queryForId(siteId);

			if (site == null) {
				throw new SiteNotFoundException(
						"The ProjectSite Id could not be found in the database!");
			}

			MultiTouchDrawable.setGridSpacing(site.getGridSpacingX(),
					site.getGridSpacingY());

			map = new SiteMapDrawable(this, this);
			map.setAngleAdjustment(site.getNorth());

			if (site.getWidth() == 0 || site.getHeight() == 0) {
				// the site has never been loaded
				freshSite = true;
				site.setSize(map.getWidth(), map.getHeight());
			} else {
				map.setSize(site.getWidth(), site.getHeight());
			}
			if (site.getBackgroundBitmap() != null) {
				map.setBackgroundImage(site.getBackgroundBitmap());
			}

			for (AccessPoint ap : site.getAccessPoints()) {
				new AccessPointDrawable(this, map, ap);
			}

			user = new UserDrawable(this, map);

			if (site.getLastLocation() != null) {
				user.setRelativePosition(site.getLastLocation().getX(), site
						.getLastLocation().getY());
			} else {
				user.setRelativePosition(map.getWidth() / 2,
						(map.getHeight() / 2));
			}

			LocationServiceFactory.getLocationService().setRelativeNorth(
					site.getNorth());
			LocationServiceFactory.getLocationService().setGridSpacing(
					site.getGridSpacingX(), site.getGridSpacingY());
			stepDetectionProvider = new StepDetectionProvider(this);
			stepDetectionProvider.setLocationChangeListener(this);

			messageHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					switch (msg.what) {
					case MESSAGE_REFRESH:
						/* Refresh UI */
						if (multiTouchView != null)
							multiTouchView.invalidate();
						break;
					case MESSAGE_START_WIFISCAN:
						// start a wifiscan
						startWifiBackgroundScan();
						break;

					case MESSAGE_PERSIST_RESULT:

						if (msg.arg1 == RESULT_OK) {
							if (msg.getData().getInt(
									WifiScanResultPersistTask.RESULT_COUNT) > 0)
								Toast.makeText(
										context,
										R.string.project_site_scanresults_persisted,
										Toast.LENGTH_SHORT).show();
						} else {
							Toast.makeText(
									context,
									context.getString(
											R.string.project_site_scanresults_not_persisted,
											msg.getData()
													.getString(
															WifiScanResultPersistTask.RESULT_MESSAGE)),
									Toast.LENGTH_LONG).show();
						}

						break;
					}
				}
			};

			wifiRunnable = new Runnable() {

				@Override
				public void run() {
					messageHandler.sendEmptyMessage(MESSAGE_START_WIFISCAN);
				}

			};

			unsavedScanResults = new ArrayList<WifiScanResult>();

			schedulerTime = this.getPreferences(Context.MODE_PRIVATE).getInt(
					SCAN_INTERVAL, schedulerTime);

			initUI();

		} catch (Exception ex) {
			log.error(
					"Failed to create ProjectSiteActivity: " + ex.getMessage(),
					ex);
			Toast.makeText(this, R.string.project_site_load_failed,
					Toast.LENGTH_LONG).show();
			this.finish();
		}
	}

	@SuppressWarnings("deprecation")
	protected void initUI() {

		tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {

			@Override
			public void onInit(int status) {

				if (status == TextToSpeech.SUCCESS) {
					int result = tts.setLanguage(Locale.UK);
					if (result == TextToSpeech.LANG_MISSING_DATA
							|| result == TextToSpeech.LANG_NOT_SUPPORTED) {
						Log.e("error", "This Language is not supported");
					} else {

					}
				} else
					Log.e("error", "Initilization Failed!");
			}
		});

		multiTouchView = ((MultiTouchView) findViewById(R.id.project_site_resultview));
		multiTouchView.setRearrangable(false);
		setScaleOfMap(5);
		multiTouchView.addDrawable(map);
		setWalkingAndScanning(!walkingAndScanning, true);
		user.setRelativePosition(360, 1150);


		if (site.getTitle().equals(ProjectSite.UNTITLED)) {
			showDialog(DIALOG_TITLE);
		} else {
			if (freshSite) {
				// start configuration dialog
				showDialog(DIALOG_FRESH_SITE);
			}
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (databaseHelper != null) {
			OpenHelperManager.releaseHelper();
			databaseHelper = null;
		}
		WifiScanner.stopScanning(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		log.debug("setting context");

		multiTouchView.loadImages(this);
		map.load();
		// stepDetectionProvider.start();

		if (walkingAndScanning) {
			setWalkingAndScanning(true, true);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onClick(View v) {
		switch (v.getId()) {

		case R.id.project_site_add_known_ap:
			showDialog(DIALOG_ADD_KNOWN_AP);
			break;

		}
	}


	protected void setWalkingAndScanning(boolean shouldRun, boolean ui) {
		if (!shouldRun) {
			// stop!

			if (stepDetectionProvider.isRunning())
				stepDetectionProvider.stop();
			if (scheduledTask != null) {
				scheduledTask.cancel(false);
				scheduledTask = null;
			}
			stopWifiScan();
		} else {
			// start
			unsavedScanResults = new ArrayList<WifiScanResult>();

			if (!stepDetectionProvider.isRunning()) {
				stepDetectionProvider.start();
			}

			if (scheduledTask == null) {
				scheduledTask = scheduler.scheduleWithFixedDelay(wifiRunnable,
						0, (schedulerTime <= 0 ? 1 : schedulerTime),
						TimeUnit.SECONDS);
			}
		}
	}

	@SuppressWarnings("deprecation")
	protected void persistScanResults(boolean dialog) {
		if (dialog) {
			final ProgressDialog persistProgress = new ProgressDialog(this);

			final WifiScanResultPersistTask persistTask = new WifiScanResultPersistTask(
					this, persistProgress);

			// create dialog and run asynctask
			persistProgress
					.setTitle(R.string.project_site_scanresults_persisting_title);
			persistProgress
					.setMessage(getString(R.string.project_site_scanresults_persisting_message));
			persistProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

			persistProgress.setButton(getString(R.string.button_cancel),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							// Canceled.
							if (persistTask != null) {
								persistTask.cancel(true);
							}
						}
					});

			persistProgress.show();
			persistTask.execute();
		} else {
			WifiScanResultPersistTask persistTask = new WifiScanResultPersistTask(
					this, null);
			persistTask.execute();
		}
	}

	protected void addKnownAP(String bssid, String ssid) {
		Location curLocation = LocationServiceFactory.getLocationService()
				.getLocation();
		AccessPoint ap = new AccessPoint();
		ap.setBssid(bssid);
		ap.setSsid(ssid);
		ap.setLocation(curLocation);
		ap.setCapabilities("");
		ap.setCalculated(false);
		ap.setProjectSite(site);
		new AccessPointDrawable(this, map, ap);

		try {
			databaseHelper.getDao(Location.class).create(curLocation);
			databaseHelper.getDao(AccessPoint.class).create(ap);
		} catch (SQLException e) {
			Logger.e("could not create ap", e);

		}

		multiTouchView.invalidate();

	}

	protected void setCalculatedAccessPoints(Vector<AccessPointDrawable> aps) {
		// delete all old messurements
		for (int i = 0; i < map.getSubDrawables().size(); i++) {
			MultiTouchDrawable d = map.getSubDrawables().get(i);
			if (d instanceof AccessPointDrawable
					&& ((AccessPointDrawable) d).isCalculated()) {
				map.removeSubDrawable(d);
				i--;
			}
		}

		try {
			Dao<AccessPoint, Integer> apDao = databaseHelper
					.getDao(AccessPoint.class);
			Dao<Location, Integer> locDao = databaseHelper
					.getDao(Location.class);

			for (AccessPoint ap : site.getAccessPoints()) {

				try {
					if (ap.isCalculated())
						apDao.delete(ap);
				} catch (Exception e) {

				}
			}

			for (AccessPointDrawable ap : aps) {

				locDao.createIfNotExists(ap.getAccessPoint().getLocation());
				ap.getAccessPoint().setProjectSite(site);
				apDao.createOrUpdate(ap.getAccessPoint());
			}

			projectSiteDao.refresh(site);

		} catch (SQLException e) {
			Logger.e("could not delete old or create new ap results", e);
		}

		for (AccessPointDrawable ap : aps) {
			map.addSubDrawable(ap);
			map.recalculatePositions();

		}
		multiTouchView.invalidate();
	}

	protected void setScaleOfMap(float scale) {
		float mapScale = 1000 / scale;
		site.setGridSpacingX(mapScale);
		site.setGridSpacingY(mapScale);
		LocationServiceFactory.getLocationService().setGridSpacing(
				site.getGridSpacingX(), site.getGridSpacingY());
		MultiTouchDrawable.setGridSpacing(mapScale, mapScale);
		multiTouchView.invalidate();

	}

	protected void setSiteTitle(String title) {
		site.setTitle(title);
		saveProjectSite();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.project_site, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch (item.getItemId()) {
	case R.id.project_site_menu_set_north:

		setMapNorth();
		return false;
		}
		return super.onOptionsItemSelected(item);

	}

	protected void setMapNorth() {
		if (northDrawable == null) {
			// Stop auto-rotate when map north is set
			//((ToggleButton) findViewById(R.id.project_site_toggle_autorotate)).setChecked(false);
			//map.stopAutoRotate();

			// create the icon the set the north
			northDrawable = new NorthDrawable(this, map, site) {

				/*
				 * (non-Javadoc)
				 * 
				 * @see at.fhstp.wificompass.view.NorthDrawable#onOk()
				 */
				@Override
				public void onOk() {
					super.onOk();
					northDrawable = null;
					site.setNorth(me.smartshop.ToolBox.normalizeAngle(adjustmentAngle));
					map.setAngleAdjustment(site.getNorth());
					
					LocationServiceFactory.getLocationService().setRelativeNorth(site.getNorth());
					Logger.d("set adjustment angle of map to "+site.getNorth());
					Toast.makeText(ctx, R.string.project_site_nort_set, Toast.LENGTH_SHORT).show();
					saveProjectSite();
				}

			};
			northDrawable.setRelativePosition(site.getWidth() / 2, site.getHeight() / 2);
			northDrawable.setAngle(map.getAngle() + site.getNorth());

		} else {
			map.removeSubDrawable(northDrawable);
			// do not set the angle, if the menu option is clicked
			// site.setNorth(northDrawable.getAngle());
			// LocationServiceFactory.getLocationService().setRelativeNorth(site.getNorth());
			northDrawable = null;
		}

		multiTouchView.invalidate();

	}
	@SuppressWarnings("deprecation")
	protected Dialog onCreateDialog(int id) {
		switch (id) {

		case DIALOG_ADD_KNOWN_AP:

			AlertDialog.Builder addAPAlert = new AlertDialog.Builder(this);

			addAPAlert.setTitle(R.string.project_site_dialog_add_known_ap_title);
			addAPAlert.setMessage(R.string.project_site_dialog_add_known_ap_message);

			addAPAlert.setView(getLayoutInflater().inflate(R.layout.project_site_dialog_add_known_ap, (ViewGroup) getCurrentFocus()));

			addAPAlert.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {

					String ssid = ((EditText) ((AlertDialog) dialog).findViewById(R.id.project_site_dialog_add_known_ap_ssid)).getText().toString();
					String bssid = ((EditText) ((AlertDialog) dialog).findViewById(R.id.project_site_dialog_add_known_ap_bssid)).getText().toString();
					addKnownAP(bssid, ssid);

				}
			});

			addAPAlert.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					// Canceled.
				}
			});

			return addAPAlert.create();

		/*case DIALOG_SELECT_BSSIDS:

			AlertDialog.Builder selectBssidsDialog = new AlertDialog.Builder(this);

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.project_site_dialog_select_bssids,
					(ViewGroup) findViewById(R.id.project_site_dialog_select_bssids_root_layout));
			selectBssidsDialog.setView(layout);

			final SelectBssdidsExpandableListAdapter adapter = new SelectBssdidsExpandableListAdapter();

			selectBssidsDialog.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					site.setUnselectedBssids(adapter.getSelectedBssids(false));
					try {
						site.update();
					} catch (SQLException e) {
						Logger.e("Could not update project site", e);
					}
				}
			});
			selectBssidsDialog.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					// Canceled.
				}
			});

			AlertDialog dialog = selectBssidsDialog.create();

			ExpandableListView listView = (ExpandableListView) layout.findViewById(R.id.project_site_dialog_select_bssids_list_view);
			adapter.initialize(dialog.getContext(), new ArrayList<String>(), new ArrayList<ArrayList<Bssid>>());

			Button selectAll = (Button) layout.findViewById(R.id.project_site_dialog_select_bssids_select_all_button);
			Button deselectAll = (Button) layout.findViewById(R.id.project_site_dialog_select_bssids_deselect_all_button);

			OnClickListener selectAllListener = new OnClickListener() {

				@Override
				public void onClick(View v) {
					boolean state = true;

					if (v.getId() == R.id.project_site_dialog_select_bssids_select_all_button)
						state = true;
					else
						state = false;

					adapter.selectAllChildren(state);
				}

			};

			selectAll.setOnClickListener(selectAllListener);
			deselectAll.setOnClickListener(selectAllListener);

			// Set this blank adapter to the list view
			listView.setAdapter(adapter);

			ForeignCollection<WifiScanResult> scanResults = site.getScanResults();
			ArrayList<Bssid> bssids = new ArrayList<Bssid>();

			for (WifiScanResult scanResult : scanResults) {
				Collection<BssidResult> bssidResults = scanResult.getBssids();

				for (BssidResult bssidResult : bssidResults) {
					Bssid bssid = new Bssid(bssidResult.getBssid(), bssidResult.getSsid());

					boolean alreadyAdded = false;

					for (Bssid tmpBssid : bssids) {
						if (tmpBssid.getBssid().equals(bssid.getBssid()) && tmpBssid.getSsid().equals(bssid.getSsid()))
							alreadyAdded = true;
					}

					if (!alreadyAdded) {
						bssid.setSelected(site.isBssidSelected(bssid.getBssid()));
						bssids.add(bssid);
					}
				}
			}

			adapter.addItems(bssids);

			return dialog;*/

		case DIALOG_FRESH_SITE:

			AlertDialog.Builder freshBuilder = new Builder(context);
			freshBuilder.setTitle(R.string.project_site_dialog_fresh_site_title);
			freshBuilder.setMessage(R.string.project_site_dialog_fresh_site_message);

			freshBuilder.setPositiveButton(getString(R.string.button_yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					showDialog(DIALOG_SET_BACKGROUND);
				}

			});

			freshBuilder.setNegativeButton(getString(R.string.button_no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					// Canceled.
					freshSite = false;
				}
			});

			return freshBuilder.create();

		case DIALOG_ASK_FOR_NORTH:

			AlertDialog.Builder askNorthBuilder = new Builder(context);
			askNorthBuilder.setTitle(R.string.project_site_dialog_ask_north_title);
			askNorthBuilder.setMessage(R.string.project_site_dialog_ask_north_message);

			askNorthBuilder.setPositiveButton(getString(R.string.button_yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					setMapNorth();
					freshSite = false;
				}

			});

			askNorthBuilder.setNegativeButton(getString(R.string.button_no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					// Canceled.
					freshSite = false;
				}
			});

			return askNorthBuilder.create();

		case DIALOG_CHANGE_SCAN_INTERVAL:
			AlertDialog.Builder changeScanIntervalBuilder = new Builder(context);
			changeScanIntervalBuilder.setTitle(R.string.project_site_dialog_change_scan_interval_title);
			changeScanIntervalBuilder.setMessage(getString(R.string.project_site_dialog_change_scan_interval_message, schedulerTime));

			final SeekBar sb = new SeekBar(this);
			sb.setMax(60);
			sb.setProgress(schedulerTime);

			changeScanIntervalBuilder.setView(sb);

			changeScanIntervalBuilder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					schedulerTime = sb.getProgress();
					if(schedulerTime==0) schedulerTime=1; // schedulerTime must not be 0
					getPreferences(MODE_PRIVATE).edit().putInt(SCAN_INTERVAL, schedulerTime).commit();
					if (walkingAndScanning) {
						// timer must be updated
						setWalkingAndScanning(false,true);
						setWalkingAndScanning(true,true);
					}
				}

			});

			changeScanIntervalBuilder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					// Canceled.

				}
			});

			final AlertDialog changeScanIntervalDialog = changeScanIntervalBuilder.create();

			sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					changeScanIntervalDialog.setMessage(context.getString(R.string.project_site_dialog_change_scan_interval_message, progress));
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

			});

			return changeScanIntervalDialog;

		default:
			return super.onCreateDialog(id);
		}
	}
	protected void scaleOfMap() {
		if (scaler == null) {
			scaler = new ScaleLineDrawable(context, map, new OkCallback() {

				@Override
				public void onOk() {
					onMapScaleSelected();
				}
			});
			scaler.getSlider(1).setRelativePosition(user.getRelativeX() - 80,
					user.getRelativeY());
			scaler.getSlider(2).setRelativePosition(user.getRelativeX() + 80,
					user.getRelativeY());
			multiTouchView.invalidate();
		} else {
			onMapScaleSelected();
		}
	}

	@SuppressWarnings("deprecation")
	protected void onMapScaleSelected() {
		scalerDistance = scaler.getSliderDistance();
		scaler.removeScaleSliders();
		map.removeSubDrawable(scaler);
		scaler = null;
		invalidate();
		showDialog(DIALOG_SET_SCALE_OF_MAP);
	}

	@Override
	protected void onPause() {

		if (tts != null) {

			tts.stop();
			tts.shutdown();
		}
		super.onPause();
		multiTouchView.unloadImages();
		map.unload();

		setWalkingAndScanning(false, false);

		saveProjectSite();
	}

	protected void saveProjectSite() {
		log.debug("saveing project site");

		try {

			Location curLocation = new Location(LocationServiceFactory
					.getLocationService().getLocation()), lastLocation = site
					.getLastLocation();

			if (lastLocation == null
					|| (curLocation.getX() != lastLocation.getX() && curLocation
							.getY() != lastLocation.getY())) {
				site.setLastLocation(curLocation);

				Dao<Location, Integer> locDao = databaseHelper
						.getDao(Location.class);

				if (lastLocation != null) {
					// delete old location
					locDao.delete(lastLocation);
				}
				// and create new one
				locDao.create(curLocation);

			}

			for (MultiTouchDrawable d : map.getSubDrawables()) {

				if (d instanceof AccessPointDrawable) {
					AccessPoint ap = ((AccessPointDrawable) d).getAccessPoint();
					// id is not 0, so this location was never saved
					if (!ap.isCalculated() && ap.getLocation() != null) {
						try {
							databaseHelper.getDao(Location.class).create(
									ap.getLocation());
							databaseHelper.getDao(AccessPoint.class).update(ap);
						} catch (SQLException e) {
							log.error(
									"could not save location data for an ap: "
											+ ap.toString(), e);
						}
					}
				}
			}

			int changed = projectSiteDao.update(site);

			if (changed > 0) {
			}

			projectSiteDao.refresh(site);
		} catch (SQLException e) {
			log.error("could not save or refresh project site", e);
		}

	}

	protected void deleteProjectSite() {
		log.debug("saveing project site");

		try {
			int rows = site.delete();

			if (rows > 0) {
				Toast.makeText(this, R.string.project_site_deleted,
						Toast.LENGTH_SHORT).show();
			} else {
				Logger.w("Tried to delete a project site, but it did not exist?!?");

			}
			finish();

		} catch (SQLException e) {
			log.error("could not delete project site", e);
			Toast.makeText(
					this,
					getString(R.string.project_site_delete_failed,
							e.getMessage()), Toast.LENGTH_LONG).show();
		}

	}

	@Override
	public synchronized void onScanFinished(WifiScanResult wr) {
		hideWifiScanDialog();
		if (!ignoreWifiResults) {
			try {

				Logger.d("received a wifi scan result!");
				ignoreWifiResults = true;

				wr.setProjectLocation(site);

				if (walkingAndScanning) {
					unsavedScanResults.add(wr);
				} else {
					wr.save(databaseHelper);
					site.getScanResults().refreshCollection();
				}

				HashMap<String, Integer> ssids = new HashMap<String, Integer>();
				if (wr.getBssids() != null)
					for (BssidResult result : wr.getBssids()) {
						ssids.put(result.getSsid(),
								(ssids.get(result.getSsid()) == null ? 1
										: ssids.get(result.getSsid()) + 1));

					}

				user.bringToFront();

				multiTouchView.invalidate();
				
				Toast.makeText(this, this.getString(R.string.project_site_wifiscan_finished, ssids.size(), wr.getBssids().size()), Toast.LENGTH_SHORT)
				.show();

			} catch (SQLException e) {
				Logger.e("could not update wifiscanresult!", e);
				Toast.makeText(
						this,
						this.getString(R.string.project_site_wifiscan_failed,
								e.getMessage()), Toast.LENGTH_LONG).show();
			}

		}
	}

	@Override
	public void onScanFailed(Exception ex) {
		hideWifiScanDialog();
		if (!ignoreWifiResults) {

			Logger.e("Wifi scan failed!", ex);
			Toast.makeText(
					this,
					this.getString(R.string.project_site_wifiscan_failed,
							ex.getMessage()), Toast.LENGTH_LONG).show();

		}

	}

	protected void startWifiScan() throws WifiException {
		log.debug("starting WiFi Scan");

		wifiBroadcastReceiver = WifiScanner.startScan(this, this);
		ignoreWifiResults = false;
	}

	protected void startWifiBackgroundScan() {

		try {
			// we first stop the old receiver, so we wont receive duplicate
			// results
			// stopWifiScan();

			if (wifiBroadcastReceiver != null) {
				// wifiBroadcastReceiver.
			}

			startWifiScan();
			// Toast.makeText(this, R.string.project_site_wifiscan_started,
			// Toast.LENGTH_SHORT).show();
		} catch (WifiException e) {
			Logger.e("could not start wifi scan", e);
			Toast.makeText(
					this,
					getString(R.string.project_site_wifiscan_start_failed,
							e.getMessage()), Toast.LENGTH_LONG).show();
		}

	}

	/**
	 * stop the wifi scan, if in progress
	 */
	protected void stopWifiScan() {
		hideWifiScanDialog();

		if (wifiBroadcastReceiver != null) {

			WifiScanner.stopScanner(this, wifiBroadcastReceiver);
			wifiBroadcastReceiver = null;

		}
		// stop scan
		// oh, wait, we can't stop the scan, it's asynchronous!
		// we just have to ignore the result!
		ignoreWifiResults = true;

	}

	/**
	 * hide the wifi scan dialog if shown
	 */
	protected void hideWifiScanDialog() {
		if (scanningImageView != null) {
			((AnimationDrawable) scanningImageView.getDrawable()).stop();
			// scanningImageView = null;
		}

		if (scanAlertDialog != null) {
			scanAlertDialog.cancel();
			// scanAlertDialog = null;
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		// if(scanAlertDialog!=null && scanningImageView!=null){
		// ((AnimationDrawable) scanningImageView.getDrawable()).start();
		// }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Logger.d("Activity result of " + requestCode + " " + resultCode + " "
				+ (data != null ? data.toString() : ""));

	}

	protected void setBackgroundImage(String path) {

		try {
			Bitmap bmp = BitmapFactory.decodeResource(getResources(),
					R.drawable.shop_f);
			site.setBackgroundBitmap(bmp);
			map.setBackgroundImage(bmp);
			site.setSize(bmp.getWidth(), bmp.getHeight());
			map.setSize(bmp.getWidth(), bmp.getHeight());
			user.setRelativePosition(bmp.getWidth() / 2, bmp.getHeight() / 2);
			multiTouchView.invalidate();
			Toast.makeText(context,
					"set " + path + " as new background image!",
					Toast.LENGTH_LONG).show();
			saveProjectSite();

		} catch (Exception e) {
			Logger.e("could not set background", e);
			Toast.makeText(
					context,
					getString(R.string.project_site_set_background_failed,
							e.getMessage()), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		this.setContentView(R.layout.project_site);
		initUI();
	}

	@Override
	public void invalidate() {
		if (multiTouchView != null) {
			multiTouchView.invalidate();
		}
	}

	/**
	 * @author Thomas Konrad (is101503@fhstp.ac.at)
	 */
	protected class TriangulationTask extends
			AsyncTask<Void, Integer, Vector<AccessPointDrawable>> {

		/**
		 * @uml.property name="parent"
		 * @uml.associationEnd
		 */
		private final ProjectSiteActivity parent;

		private final ProgressDialog progress;

		public TriangulationTask(final ProjectSiteActivity parent,
				final ProgressDialog progress) {
			this.parent = parent;
			this.progress = progress;
		}

		@Override
		protected Vector<AccessPointDrawable> doInBackground(Void... params) {
			WeightedCentroidTrilateration wc = new WeightedCentroidTrilateration(
					context, site, progress);
			return wc.calculateAllAndGetDrawables();
		}

		@Override
		protected void onPostExecute(final Vector<AccessPointDrawable> result) {
			progress.dismiss();
			parent.setCalculatedAccessPoints(result);
		}
	}
	
/*	public void startAnimation(View view) {
		  float scale = getResources().getDisplayMetrics().density;
		  View someImage = findViewById(R.id.button1);

		  ObjectAnimator anim1 = ObjectAnimator.ofFloat(someImage, 
		    "x", user.getRelativeX());
		  ObjectAnimator anim2 = ObjectAnimator.ofFloat(someImage, 
		    "y", user.getRelativeY());
		 
		  
		  AnimatorSet animSet = new AnimatorSet();
		  
		  animSet.play(anim1).with(anim2);
		  animSet.setDuration(1000);
		  animSet.setInterpolator(new AccelerateDecelerateInterpolator());
		  animSet.start();
	}*/
	
	public void onClickCart(View v) {

		setWalkingAndScanning(false, false);
		final CharSequence[] items = { "Biscuits", "Drinks" };
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Pick an Item");
		builder.setItems(items, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int item) {

				if (item == 0) {

					map.deleteAllSteps();

					txt = "Turn to your left slighty and take 3 steps, then turn right slightly"
							+ "and take 4 steps.";
					tts.speak(txt, TextToSpeech.QUEUE_FLUSH, null, null);

					map.addStep(new PointF(315, 1152));
					map.addStep(new PointF(210, 1059));
					map.addStep(new PointF(171, 984));
					map.addStep(new PointF(165, 884));
					map.addStep(new PointF(165, 400));

				}

				if (item == 1) {

					map.deleteAllSteps();

					txt = "Turn to your left slighty and take 3 steps, then turn right slightly"
							+ "and take 5 steps, turn right again and take 6 steps";
					tts.speak(txt, TextToSpeech.QUEUE_FLUSH, null, null);

					map.addStep(new PointF(315, 1152));
					map.addStep(new PointF(210, 1059));
					map.addStep(new PointF(171, 984));
					map.addStep(new PointF(165, 884));
					map.addStep(new PointF(165, 354));
					map.addStep(new PointF(198, 276));
					map.addStep(new PointF(465, 276));

				}

				setWalkingAndScanning(true, true);
			}
		});

		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	public void onLocationChange(Location loc) {
		// info from StepDetectionProvider, that the location changed.

		user.setRelativePosition(loc.getX(), loc.getY());
		//map.addStep(new PointF(loc.getX(),loc.getY()));
		
		// the block was used to simulate a user walking around the store
		
	/*	user.setRelativePosition(315, 1152);
		

		if (count == 1) {
			user.setRelativePosition(210, 1059);
		}
		if (count == 2) {
			user.setRelativePosition(171, 984);
		}
		if (count == 3) {
			user.setRelativePosition(165, 885);
		}
		if (count == 4) {
			user.setRelativePosition(165, 778);
		}
		if (count == 5) {
			user.setRelativePosition(165, 615);
		}
		if (count == 6) {
			user.setRelativePosition(165, 560);
		}
		if (count == 7) {
			user.setRelativePosition(165, 480);
		}
		if (count == 8) {
			user.setRelativePosition(165, 444);
		}
		if (count == 9) {
			user.setRelativePosition(165, 354);
		}
		if (count == 10) {
			user.setRelativePosition(198, 276);
		}
		if (count == 11) {
			user.setRelativePosition(267, 276);
		}
		if (count == 12) {
			user.setRelativePosition(318, 276);
		}
		if (count == 13) {
			user.setRelativePosition(381, 276);
		}
		if (count == 14) {
			user.setRelativePosition(420, 276);
		}
		if (count == 15) {
			user.setRelativePosition(465, 276);
		}
		if (count == 16) {
			user.setRelativePosition(522, 276);
		}
		if (count == 17) {
			user.setRelativePosition(560, 291);
		}
		if (count == 18) {
			user.setRelativePosition(560, 375);
		}
		if (count == 19) {
			user.setRelativePosition(560, 450);
		}
		if (count == 20) {
			user.setRelativePosition(560, 687);
		}
		if (count == 21) {
			user.setRelativePosition(560, 840);
		}
		if (count == 22) {
			user.setRelativePosition(560, 984);
		}
		if (count == 23) {
			user.setRelativePosition(498, 969);
		}
		if (count == 24) {
			user.setRelativePosition(426, 930);
		}
		if (count == 25) {
			user.setRelativePosition(369, 861);
		}
		if (count == 26) {
			user.setRelativePosition(369, 783);
		}
		if (count == 27) {
			user.setRelativePosition(369, 600);
		}
		if (count == 28) {
			user.setRelativePosition(369, 525);
		}
		if (count == 29) {
			user.setRelativePosition(369, 438);
		}
		count += 1;*/

	
		// messageHandler.sendEmptyMessage(MESSAGE_REFRESH);
	}

	protected class WifiScanResultPersistTask extends
			AsyncTask<Void, Integer, Bundle> {

		protected ProjectSiteActivity parent;

		protected ProgressDialog progressDialog;

		protected boolean running = true;

		protected DatabaseHelper databaseHelper;

		static final String RESULT_CODE = "result", RESULT_MESSAGE = "message",
				RESULT_COUNT = "count";

		public WifiScanResultPersistTask(ProjectSiteActivity parent,
				ProgressDialog progress) {
			this.parent = parent;
			this.progressDialog = progress;
			databaseHelper = OpenHelperManager.getHelper(parent,
					DatabaseHelper.class);
		}

		@Override
		protected Bundle doInBackground(Void... params) {
			Bundle result = new Bundle();
			result.putInt(RESULT_CODE, RESULT_CANCELED);

			int progress = 0;

			synchronized (unsavedScanResults) {

				try {
					this.progressDialog.setMax(unsavedScanResults.size());
				} catch (Exception ex) {
				}

				// save all wifiscan results
				try {

					for (WifiScanResult sr : unsavedScanResults) {
						if (!running) {
							break;
						}
						sr.save(databaseHelper);
						this.publishProgress(++progress);
					}

					result.putInt(RESULT_COUNT, progress);
					if (running) {
						Logger.d("saved " + unsavedScanResults.size()
								+ " WiFi scan results");
						unsavedScanResults = new ArrayList<WifiScanResult>();
						result.putInt(RESULT_CODE, RESULT_OK);
					} else {
						// remove the saved results

						while (progress > 0) {
							unsavedScanResults.remove(0);
							progress--;
						}
					}
				} catch (SQLException e) {
					Logger.e("Could not save temporary results", e);
					result.putInt(RESULT_CODE, RESULT_CANCELED);
					result.putString(RESULT_MESSAGE, RESULT_MESSAGE);
				}
			}

			return result;
		}

		@Override
		protected void onPostExecute(Bundle result) {
			if (progressDialog != null)
				progressDialog.dismiss();
			OpenHelperManager.releaseHelper();
			if (running && messageHandler != null) {
				Message msg = new Message();
				msg.what = MESSAGE_PERSIST_RESULT;
				msg.arg1 = result.getInt(RESULT_CODE);
				msg.setData(result);
				messageHandler.sendMessage(msg);
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (progressDialog != null)
				progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onCancelled() {
			running = false;
			super.onCancelled();
		}

	}
}
