package com.eap.sdy60.ge4.eva_b.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    SupportMapFragment mapFragment;
    public LocationRequest mLocationRequest;
    FusedLocationProviderClient mFusedLocationClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;

    // Temporary data
    private ArrayList<LatLng> arrayPoints = new ArrayList<LatLng>();

    // Buttons
    Button startBtn;
    Button endBtn;
    Button putPointBtn;
    Button deletePointBtn;

    // Points view
    TextView pointsTxtView;

    // Level view
    TextView levelTxtView;

    // Polyline stuff
    PolylineOptions polylineOptions;

    // Firebase database
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference mDb = database.getInstance().getReference().child("users");

    // Local db
    TinyDB tinydb;

    // Current session's user id (retrieved from the db)
    String mUserKey;

    // Current session's path id (retrieved from the shared preference)
    String mPathKey;

    // Path type data
    ArrayList<PathType> pathTypeList = createPathTypes(new ArrayList<PathType>());

    // Last location that has been inserted to the db
    Location prevDbLoc;

    // Total meters that have been recorded
    float totalMeters;

    // Last saved distance
    float lastDistance;

    // Hashmap only for Last marker (it will contain only one entry)
    HashMap<String, Marker> hashMapMarker = new HashMap<>();

    // Define the context
    Context context;

    // Start dialog (select level)
    AlertDialog selectLevelDialog;

    // End path's recording confirmation dialog
    AlertDialog endPathConfirmationDialog;

    // Global value for current path type
    int pathLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Define fused location client
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get the buttons by the view
        startBtn = findViewById(R.id.btnStart);
        endBtn = findViewById(R.id.btnEnd);
        putPointBtn = findViewById(R.id.btnPutPoint);
        deletePointBtn = findViewById(R.id.btnDelete);

        // Get user points text view
        pointsTxtView = findViewById(R.id.user_points);

        // Get path level text view
        levelTxtView = findViewById(R.id.category_level);

        context = this;
        tinydb = new TinyDB(this);
        FirebaseApp.initializeApp(this);
        startBtn.setVisibility(View.GONE);
        endBtn.setVisibility(View.GONE);
        putPointBtn.setVisibility((View.GONE));
        deletePointBtn.setVisibility((View.GONE));
        disableDeleteButton(true);

        levelTxtView.setVisibility(View.GONE);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCategoryLevelDialog();
            }
        });

        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEndPathConfirmationDialog();
            }
        });

        putPointBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Location currLoc = mLastLocation;
                Map mPoint = new HashMap();
                mPoint.put("date", ServerValue.TIMESTAMP);
                mPoint.put("name", getLocationName(currLoc.getLatitude(), currLoc.getLongitude()));
                mPoint.put("latLng", new LatLng(currLoc.getLatitude(), currLoc.getLongitude()));

                mDb.child(mUserKey).child(mPathKey).push().setValue(mPoint, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                        LatLng currLatLng = new LatLng(currLoc.getLatitude(), currLoc.getLongitude());
                        arrayPoints.add(currLatLng);
                        String markerColor = pathTypeList.get(pathLevel).getProperty("colorHex");
                        putMarker(currLatLng, markerColor);
                        drawPolyline(pathLevel);
                        tinydb.putString("lastPointKey", databaseReference.getKey());
                        if (prevDbLoc != null) {
                            lastDistance = currLoc.distanceTo(prevDbLoc);
                            totalMeters += lastDistance;
                            if (totalMeters > 500 && pointsTxtView.getVisibility() == View.GONE) {
                                pointsTxtView.setText("Πόντοι: " + Math.round(totalMeters / 1000));
                                pointsTxtView.setVisibility(View.VISIBLE);
                            }
                        }

                        disableDeleteButton(false);
                        prevDbLoc = currLoc;

                        if (arrayPoints.size() > 3) {
                            endBtn.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        });


        // Event for clicking delete button
        deletePointBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDb.child(mUserKey).child(mPathKey).child(tinydb.getString("lastPointKey")).removeValue();
                totalMeters = totalMeters - lastDistance;
                pointsTxtView.setText("Πόντοι: " + Math.round(totalMeters / 1000));
                Marker marker = hashMapMarker.get("lastMarker");
                marker.remove();
                arrayPoints.remove(arrayPoints.size() - 1);
                drawPolyline(pathLevel);
                disableDeleteButton(true);
            }
        });
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Χρειάζεται άδεια για τον εντοπισμό θέσης");
                alert.setMessage("Η παρούσα εφαρμογή χρειάζεται την άδεια σας για να ανιχνεύσει την τοποθεσία σας, παρακαλούμε δεχτείτε τη για να αξιοποιήσετε τη λειτουργία εντοπισμού θέσης.");
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Prompt the user once explanation has been shown
                        ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
                    }
                }).create().show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Άρνηση παροχής αδείας.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        if (mFusedLocationClient != null) {
            requestLocationListener();
        }
        mUserKey = tinydb.getString("userKey");
        mPathKey = tinydb.getString("pathKey");
        if (mUserKey != null && mPathKey != null) {
            getSavedPoints();
        } else {

        }
        if (hashMapMarker.isEmpty()) {
            disableDeleteButton(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    public void onResume() {
        super.onResume();
        if (mFusedLocationClient != null) {
            requestLocationListener();
        }

        mUserKey = tinydb.getString("userKey");
        mPathKey = tinydb.getString("pathKey");
        if (mUserKey != null && mPathKey != null) {
            getSavedPoints();
        }

        if (hashMapMarker.isEmpty()) {
            disableDeleteButton(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    public void requestLocationListener() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(1000);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        }
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
    @SuppressLint("RestrictedApi")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //Initialize Google Play Services
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000); // 5 seconds interval
        mLocationRequest.setFastestInterval(1000); // one second fast interval
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //Location Permission already granted
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                mMap.setMyLocationEnabled(true);
            } else {
                //Request Location Permission
                checkLocationPermission();
            }
        } else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mMap.setMyLocationEnabled(true);
        }

    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        // After getting the latest location. save it and re-zoom the map
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                Log.i("MapsActivity", "Location: " + location.getLatitude() + " " + location.getLongitude());
                mLastLocation = location;
                if (mCurrLocationMarker != null) {
                    mCurrLocationMarker.remove();
                }
                //Place current location marker
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                //move map camera
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
            }
        }
    };

    // Get a points location name in order to have representative name for the entry
    public String getLocationName(double lat, double lng) {
        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        try {
            // Get the available addresses
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            // Check if there exist available addresses
            if (addresses != null && addresses.size() > 0) {
                // Get the first one
                Address obj = addresses.get(0);
                // In this case, we get featured name and locality
                String area = obj.getThoroughfare();
                // We return it as one name
                area = area + ", " + obj.getLocality();
                return area;
            } else {
                return "unknown area";
            }
            // Catch exception
        } catch (IOException e) {
            e.printStackTrace();
            // Inform about the error
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    public void initPathDbRef(final int pathType) {
        pathLevel = pathType;
        if (mUserKey == null) {
            mDb.push().setValue("user", new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {

                    mUserKey = databaseReference.getKey();
                    tinydb.putString("userKey", mUserKey);
                }
            });
        } else {
            mUserKey = tinydb.getString("userKey");
        }
        calculateTotalMeters();

        final Location currLoc = mLastLocation;
        String levelType = "";
        switch (pathLevel) {
            case 0:
                levelType = "onFoot";
                break;
            case 1:
                levelType = "bicycle";
                break;
            case 2:
                levelType = "babyCart";
                break;
            default:
                levelType = "onFoot";
                break;
        }
        Map mPath = new HashMap();
        mPath.put("typeStr", levelType);
        mPath.put("typeInt", pathType);

        // Push to the database
        mDb.child(mUserKey).push().setValue(mPath, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseErr, DatabaseReference databaseRef) {
                mPathKey = databaseRef.getKey();
                tinydb.putString("pathKey", mPathKey);
                Map mPoint = new HashMap();
                LatLng firstLatLng = new LatLng(currLoc.getLatitude(), currLoc.getLongitude());
                mPoint.put("date", ServerValue.TIMESTAMP);
                mPoint.put("name", getLocationName(currLoc.getLatitude(), currLoc.getLongitude()));
                mPoint.put("latLng", firstLatLng);
                String markerColor = pathTypeList.get(pathType).getProperty("colorHex");
                putMarker(firstLatLng, markerColor);
                // Push to the database
                mDb.child(mUserKey).child(mPathKey).push().setValue(mPoint, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseErr, DatabaseReference dbRef) {
                        // Show put point button, hide start button
                        startBtn.setVisibility(View.GONE);
                        putPointBtn.setVisibility(View.VISIBLE);
                        deletePointBtn.setVisibility(View.VISIBLE);
                        levelTxtView.setText("Κατηγορία: " + pathTypeList.get(pathLevel).getProperty("level"));
                        levelTxtView.setCompoundDrawablesWithIntrinsicBounds(0, 0, getResources().getIdentifier(pathTypeList.get(pathType).getProperty("icon"), "drawable", getPackageName()), 0);
                        levelTxtView.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

    }

    void putMarker(LatLng point, String clr) {
        hashMapMarker.clear();
        // Create marker
        MarkerOptions markerOptions = new MarkerOptions();
        // Set the marker's position
        markerOptions.position(point);
        // Set the marker's colour
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(Float.valueOf(clr)));
        // Set the marker's title (it is defined by location name)
        markerOptions.title(getLocationName(point.latitude, point.longitude));
        // Save the point to memory
        arrayPoints.add(point);
        // Finally, add the marker
        Marker mark = mMap.addMarker(markerOptions);
        hashMapMarker.put("lastMarker", mark);
    }

    void drawPolyline(int type) {
        // Setting polyline in the map according to the markers (connect the dots!)
        polylineOptions = new PolylineOptions();
        // Set the color of our polyline and other stylistic arrangements
        polylineOptions.color(Integer.parseInt(pathTypeList.get(type).getProperty("colorRgb")));
        polylineOptions.width(5);
        // Attach the points to the polyline
        polylineOptions.addAll(arrayPoints);
        // Finally, draw the polyline on the map
        mMap.addPolyline(polylineOptions);
    }

    void getSavedPoints() {
        calculateTotalMeters();
        DatabaseReference ref = mDb.child(mUserKey).child(mPathKey);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() == null || mPathKey.equals("")) {
                    startBtn.setVisibility(View.VISIBLE);
                    endBtn.setVisibility(View.GONE);
                    putPointBtn.setVisibility(View.GONE);
                    deletePointBtn.setVisibility(View.GONE);
                } else {
                    startBtn.setVisibility(View.GONE);
                    endBtn.setVisibility(View.VISIBLE);
                    putPointBtn.setVisibility(View.VISIBLE);
                    deletePointBtn.setVisibility(View.VISIBLE);
                    arrayPoints.clear();
                    prevDbLoc = null;
                    int type = Integer.parseInt(dataSnapshot.child("typeInt").getValue().toString());
                    pathLevel = type;
                    String markerColor = pathTypeList.get(type).getProperty("colorHex");
                    for (DataSnapshot ds : dataSnapshot.getChildren()) {
                        if (ds.child("latLng").getValue() != null) {
                            double lat = Double.parseDouble(ds.child("latLng").child("latitude").getValue().toString());
                            double lon = Double.parseDouble(ds.child("latLng").child("longitude").getValue().toString());
                            Location currLoc = new Location("location");
                            currLoc.setLatitude(lat);
                            currLoc.setLongitude(lon);
                            if (prevDbLoc != null) {
                                totalMeters += currLoc.distanceTo(prevDbLoc);
                            }
                            LatLng latLon = new LatLng(lat, lon);
                            putMarker(latLon, markerColor);
                            arrayPoints.add(latLon);
                            prevDbLoc = currLoc;
                        }
                    }
                    levelTxtView.setText("Κατηγορία: " + pathTypeList.get(pathLevel).getProperty("level"));
                    levelTxtView.setCompoundDrawablesWithIntrinsicBounds(0, 0, getResources().getIdentifier(pathTypeList.get(pathLevel).getProperty("icon"), "drawable", getPackageName()), 0);
                    levelTxtView.setVisibility(View.VISIBLE);
                    drawPolyline(type);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //handle databaseError
                Toast.makeText(MapsActivity.this, "Προέκυψε πρόβλημα με τη βάση δεδομένων, παρακαλούμε ξαναπροσπαθήστε αργότερα.", Toast.LENGTH_LONG).show();
            }
        });
    }

    void calculateTotalMeters() {
        mDb.child(mUserKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                totalMeters = 0;
                for (DataSnapshot pathSnapshot : dataSnapshot.getChildren()) {
                    // Calculate only the previous recorded paths, the current will be calculated by another function
                    if (pathSnapshot != null) {
                        float pathSum = 0;
                        Location prevLoc = new Location("location");
                        for (DataSnapshot pointSnapshot : pathSnapshot.getChildren()) {
                            if (pointSnapshot.child("latLng").getValue() != null) {
                                Location currLoc = new Location("location");
                                currLoc.setLatitude(Double.parseDouble(pointSnapshot.child("latLng").child("latitude").getValue().toString()));
                                currLoc.setLongitude(Double.parseDouble(pointSnapshot.child("latLng").child("longitude").getValue().toString()));
                                if (prevLoc.getLatitude() != 0 && prevLoc.getLongitude() != 0) {
                                    pathSum += (currLoc.distanceTo(prevLoc));
                                }
                                prevLoc = currLoc;
                            }
                        }
                        totalMeters += pathSum;
                        if (totalMeters > 500) {
                            pointsTxtView.setText("Πόντοι: " + Math.round(totalMeters / 1000));
                            pointsTxtView.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }

            // On cancel
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Nothing needed here
            }
        });
    }

    void disableDeleteButton(Boolean disabled) {
        Drawable drawable = getResources().getDrawable(R.drawable.undoicon);
        if (disabled) {
            deletePointBtn.setEnabled(false);
            deletePointBtn.setAlpha(0.6f);
            drawable.setAlpha(153);
        } else {
            deletePointBtn.setEnabled(true);
            deletePointBtn.setAlpha(1f);
            drawable.setAlpha(255);
        }
    }

    void showCategoryLevelDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View selectionView = inflater.inflate(getResources().getIdentifier("level_selection", "layout", getPackageName()), null);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, getResources().getIdentifier("levelSelectionDialogTheme", "style", getPackageName()));
        alertDialog.setTitle("Επιλογή κατηγορίας της διαδρομής");
        alertDialog.setView(selectionView);
        alertDialog.setPositiveButton("ΚΑΤΑΓΡΑΦΗ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Inform the user in order to start recording the path
                Toast toast = Toast.makeText(getApplicationContext(), "Ξεκινήστε την καταγραφή!", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();

                RadioGroup levelSelectionGroup = selectionView.findViewById(R.id.radioLevelSelection);

                // Get selected radio button from radioGroup
                int selectedLvlId = levelSelectionGroup.getCheckedRadioButtonId();
                View selectedButton = levelSelectionGroup.findViewById(selectedLvlId);
                int selectionType = levelSelectionGroup.indexOfChild(selectedButton);
                initPathDbRef(selectionType);
            }
        });
        alertDialog.setNegativeButton("ΑΚΥΡΩΣΗ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });
        selectLevelDialog = alertDialog.create();
        if (!selectLevelDialog.isShowing()) {
            selectLevelDialog.show();
        }
    }

    void showEndPathConfirmationDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View selectionView = inflater.inflate(getResources().getIdentifier("end_path_confirmation", "layout", getPackageName()), null);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, getResources().getIdentifier("endPathConfirmationDialogTheme", "style", getPackageName()));
        alertDialog.setTitle("ΕΠΙΒΕΒΑΙΩΣΗ");
        alertDialog.setView(selectionView);
        alertDialog.setPositiveButton("ΟΛΟΚΛΗΡΩΣΗ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                startBtn.setVisibility(View.VISIBLE);
                putPointBtn.setVisibility((View.GONE));
                deletePointBtn.setVisibility((View.GONE));
                disableDeleteButton(true);
                levelTxtView.setVisibility(View.GONE);
                mMap.clear();
                arrayPoints.clear();
                endBtn.setVisibility(View.GONE);
                // Reset the path
                tinydb.putString("pathKey", "");
                mPathKey = null;
                // Inform the user for ending the path's recording
                Toast toast = Toast.makeText(getApplicationContext(), "Η καταγραφή ολοκληρώθηκε!", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();
            }
        });
        alertDialog.setNegativeButton("ΑΚΥΡΩΣΗ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });

        endPathConfirmationDialog = alertDialog.create();
        if (!endPathConfirmationDialog.isShowing()) {
            endPathConfirmationDialog.show();
        }
    }

    public class PathType {

        private Map<String, String> properties = new HashMap<String, String>();
        private Map<String, Callable<Object>> callables = new HashMap<String, Callable<Object>>();

        public String getProperty(String key) {
            return properties.get(key);
        }

        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        public Object call(String key) throws Exception {
            Callable<Object> callable = callables.get(key);
            if (callable != null) {
                return callable.call();
            }
            return null;
        }

        public void define(String key, Callable<Object> callable) {
            callables.put(key, callable);
        }
    }

    // Populate a list with the distinct objects with certain properties (colors etc)
    public ArrayList<PathType> createPathTypes(ArrayList<PathType> list) {
        PathType onFoot = new PathType();
        onFoot.setProperty("index", Integer.toString(0));
        onFoot.setProperty("colorRgb", Integer.toString(Color.rgb(0, 255, 255)));
        onFoot.setProperty("colorHex", "180.0f");
        onFoot.setProperty("level", "1");
        onFoot.setProperty("icon", "onfooticon");

        PathType bicycle = new PathType();
        bicycle.setProperty("index", Integer.toString(1));
        bicycle.setProperty("colorRgb", Integer.toString(Color.rgb(128, 0, 128)));
        bicycle.setProperty("colorHex", "270.0f");
        bicycle.setProperty("level", "2");
        bicycle.setProperty("icon", "bicycleicon");


        PathType babyCart = new PathType();
        babyCart.setProperty("index", Integer.toString(2));
        babyCart.setProperty("colorRgb", Integer.toString(Color.rgb(255, 0, 255)));
        babyCart.setProperty("colorHex", "300.0f");
        babyCart.setProperty("level", "3");
        babyCart.setProperty("icon", "babycarticon");

        list.add(onFoot);
        list.add(bicycle);
        list.add(babyCart);

        return list;
    }
}



