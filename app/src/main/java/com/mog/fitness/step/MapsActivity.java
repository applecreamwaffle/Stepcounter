package com.mog.fitness.step;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.mog.fitness.common.logger.LogView;
import com.mog.fitness.common.logger.LogWrapper;
import com.mog.fitness.common.logger.MessageOnlyLogFilter;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, View.OnClickListener {
    private final String TAG = MapsActivity.class.getSimpleName();

    private final LatLng defaultLatLng = new LatLng(37.5728109, 126.9743077);
    private final float defaultZoomLevel = 16; //6;
    private final float MaxZoomLevel = 16; //6;
    private final int REQUEST_PERMISSION_ACCESS_FINE_LOCATION = 9;
    private static final long MIN_DISTANCE_UPDATE = 10;
    private static final long MIN_TIME_UPDATE = 1000 * 5 * 1;
    private static final int REQUEST_OAUTH_REQUEST_CODE = 0x1001;

    private GoogleMap mMap;
    private LocationManager locationManager;
    private Location currentLocation;
    private Marker currentMarker;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Acquire a reference to the system Location Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);


        // Register the listener with the Location Manager to receive location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        String locationProvider = LocationManager.NETWORK_PROVIDER;
        // Or, use GPS location data:
        // String locationProvider = LocationManager.GPS_PROVIDER;
        locationManager.requestLocationUpdates(locationProvider, MIN_TIME_UPDATE, MIN_DISTANCE_UPDATE, locationListener);
        // Get a fast fix with the last known location
        Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
        Log.d(TAG, "onCreate() lastKnownLocation: " + lastKnownLocation);

        currentLocation = (lastKnownLocation == null) ? null : lastKnownLocation;

        Button btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(this);

        initFitnessApi();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initFitnessApi() {
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging();

        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .build();

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this, // your activity
                    REQUEST_OAUTH_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions);
        } else {
            subscribe();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        com.mog.fitness.common.logger.Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView logView = (LogView) findViewById(R.id.sample_logview);

        // Fixing this lint error adds logic without benefit.
        // noinspection AndroidLintDeprecation
        logView.setTextAppearance(R.style.Log);

        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.newLatLng(defaultLatLng));
        mMap.setMinZoomPreference(defaultZoomLevel);
        mMap.setMaxZoomPreference(MaxZoomLevel);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        GoogleMapOptions options = new GoogleMapOptions().liteMode(true);
        onMyLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "YES!", Toast.LENGTH_SHORT).show();
                    onMyLocation();
                    Log.d("func ", "ok");
                } else {
                    Toast.makeText(this, "Nope!", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private void onMyLocation() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_ACCESS_FINE_LOCATION);
            }
            return;
        }

        Log.d(TAG, "onMyLocation() OK");
        mMap.setMyLocationEnabled(true);

        LatLng currentLoc = null;

        try {
            currentLoc = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLoc));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(17));
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
//        if (currentLocation == null) {
//
//        } else {
//            String locationProvider = LocationManager.NETWORK_PROVIDER;
//            Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
//            currentLoc = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
//        }

    }


    private void makeUseOfNewLocation(Location location) {
        currentLocation = location;
        onMyLocation();

    }

    // Define a listener that responds to location updates
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged(Location " + location + ")");
            makeUseOfNewLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                subscribe();
            }
        }
    }

    /** Records step data by requesting a subscription to background step data. */
    private void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this))
            .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
            .addOnCompleteListener(
                    new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.i(TAG, "Successfully subscribed!");
                            } else {
                                Log.w(TAG, "There was a problem subscribing.", task.getException());
                            }
                        }
                    });
    }

    /**
     * Reads the current daily step total, computed from midnight of the current day on the device's
     * current timezone.
     */
    private void readData() {
        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(
                        new OnSuccessListener<DataSet>() {
                            @Override
                            public void onSuccess(DataSet dataSet) {
                                long total =
                                        dataSet.isEmpty()
                                                ? 0
                                                : dataSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
                                Log.i(TAG, "Total steps: " + total);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "There was a problem getting the step count.", e);
                            }
                        });
    }

    @Override
    protected void onDestroy() {
        locationManager.removeUpdates(locationListener);
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        readData();
    }


}
