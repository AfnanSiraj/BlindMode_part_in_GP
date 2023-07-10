package com.example.publictransportationguidance.tracking;

import static com.example.publictransportationguidance.blindMode.speechToText.SpeechToTextHelper.convertHaaToTaaMarbuta;
import static com.example.publictransportationguidance.blindMode.textToSpeech.TextToSpeechHelper.convertMathIntoThoma;
import static com.example.publictransportationguidance.blindMode.textToSpeech.TextToSpeechHelper.convertSlashIntoSharta;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_CHOOSING_PATH_OR_NOT;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_TRACKING_OR_NOT;
import static com.example.publictransportationguidance.helpers.Functions.stringIsFound;
import static com.example.publictransportationguidance.helpers.GlobalVariables.ARABIC;
import static com.example.publictransportationguidance.helpers.GlobalVariables.BUNDLE_PATH;
import static com.example.publictransportationguidance.helpers.GlobalVariables.CHOOSING_PATH_OR_NOT;
import static com.example.publictransportationguidance.helpers.GlobalVariables.COST;
import static com.example.publictransportationguidance.helpers.GlobalVariables.DISTANCE;
import static com.example.publictransportationguidance.helpers.GlobalVariables.INTENT_PATH;
import static com.example.publictransportationguidance.helpers.GlobalVariables.SORTING_CRITERIA;
import static com.example.publictransportationguidance.helpers.GlobalVariables.TIME;
import static com.example.publictransportationguidance.helpers.GlobalVariables.TRACKING_OR_NOT;
import static com.example.publictransportationguidance.helpers.GlobalVariables.NAVIGATING_TO_LIVE_LOCATION_REQUEST_CODE;
import static com.example.publictransportationguidance.helpers.GlobalVariables.SELECTED_PATH;
import com.example.publictransportationguidance.blindMode.speechToText.SpeechToTextHelper;
import com.example.publictransportationguidance.blindMode.textToSpeech.TextToSpeechHelper;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.publictransportationguidance.R;
import com.example.publictransportationguidance.databinding.SelectedPathBinding;
import com.example.publictransportationguidance.sharedPrefs.SharedPrefs;
import com.example.publictransportationguidance.tracking.trackingModule.trackingModule.LocationUpdatesService;
import com.example.publictransportationguidance.tracking.trackingModule.trackingModule.TrackLiveLocation;
import com.example.publictransportationguidance.tracking.trackingModule.trackingModule.Utils;
import com.google.android.material.snackbar.Snackbar;

import java.io.Serializable;
import java.util.ArrayList;

public class SelectedPath extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    SelectedPathBinding binding;
    Bundle bundle;
    Intent intent;

    /* Afnan: instances for Blind Mode */
    private SpeechToTextHelper speechToTextHelper;
    private TextToSpeechHelper textToSpeechHelper;

    private static final String TAG = SelectedPath.class.getSimpleName();

    // Used in checking for runtime permissions.
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    // The BroadcastReceiver used to listen from broadcasts from the service.
    private MyReceiver myReceiver;

    // A reference to the service used to get location updates.
    private LocationUpdatesService mService = null;

    // Tracks the bound state of the service.
    private boolean mBound = false;

    // Monitors the state of the connection to the service.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationUpdatesService.LocalBinder binder = (LocationUpdatesService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.selected_path);

        /* Afnan: initializing Blind Mode */
        initializeTTSandSTT();

        myReceiver = new MyReceiver();
        requestPermissions();
        // Check that the user hasn't revoked permissions by going to Settings.
        if (Utils.requestingLocationUpdates(this)) if (!checkPermissions()) requestPermissions();

        binding.selectedPath.setText(getIntent().getStringExtra(SELECTED_PATH));

        /* M Osama: read the path nodes from PathResults */
        bundle = getIntent().getBundleExtra(INTENT_PATH);

        Serializable data = bundle.getSerializable(BUNDLE_PATH);

        bundle.putSerializable(BUNDLE_PATH,data);

        /* M Osama: pass path nodes to TrackLiveLocation to be viewed on Map */
        intent = new Intent(SelectedPath.this, TrackLiveLocation.class);
        intent.putExtra(INTENT_PATH,bundle);
//        binding.startLiveLocationBtn.setOnClickListener( v -> startActivity(intent) );

        //Afnan: In case of Blind Mode
        if(SharedPrefs.readMap("ON_BLIND_MODE",0)==1){
            textToSpeechHelper.speak(convertSlashIntoSharta(String.valueOf(binding.selectedPath.getText()))+"."+"هل تريد تتبع رحلتكْ . نعم أَمْ لا " ,()-> listenToTracking(this));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @org.jetbrains.annotations.NotNull String[] permissions, @NonNull @org.jetbrains.annotations.NotNull int[] grantResults) {

        if (requestCode == NAVIGATING_TO_LIVE_LOCATION_REQUEST_CODE){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){ binding.startLiveLocationBtn.setOnClickListener((View v)-> startActivity(new Intent(SelectedPath.this, TrackLiveLocation.class))); }
            else { Toast.makeText(SelectedPath.this,"من فضلك أضغط على زر سماح للموقع",Toast.LENGTH_SHORT).show(); }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        binding.startLiveLocationBtn.setOnClickListener(view -> {
            if (!checkPermissions()) requestPermissions();
            else mService.requestLocationUpdates();
            startActivity(intent);
        });

        // Restore the state of the buttons when the activity (re)launches.
        setButtonsState(Utils.requestingLocationUpdates(this));

        // Bind to the service. If the service is in foreground mode, this signals to the service that since this activity is in the foreground, the service can exit foreground mode.
        bindService(new Intent(this, LocationUpdatesService.class), mServiceConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver, new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer in the foreground, and the service can respond by promoting itself to a foreground service.
            unbindService(mServiceConnection);
            mBound = false;
        }
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    /**
     * Returns the current state of the permissions needed.
     */
    private boolean checkPermissions() { return  PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION); }

    private void requestPermissions() {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(findViewById(R.id.selectedPath), R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok, view -> ActivityCompat.requestPermissions(SelectedPath.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE)).show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy sets the permission in a given state or the user denied the permission previously and checked "Never ask again".
            ActivityCompat.requestPermissions(SelectedPath.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Receiver for broadcasts sent by {@link LocationUpdatesService}.
     */
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION);
            if (location != null) Toast.makeText(SelectedPath.this, Utils.getLocationText(location), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        // Update the buttons state depending on whether location updates are being requested.
        if (s.equals(Utils.KEY_REQUESTING_LOCATION_UPDATES)) {setButtonsState(sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES, false));
        }
    }

    private void setButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
//            binding.startLiveLocationBtn.setEnabled(false);
        } else {
//            binding.startLiveLocationBtn.setEnabled(true);
        }
    }

    private void listenToNothing(){}

    /* Afnan: Blind Mode */
    void initializeTTSandSTT(){
        textToSpeechHelper = TextToSpeechHelper.getInstance(this,ARABIC);
        speechToTextHelper = SpeechToTextHelper.getInstance(ARABIC);
    }

    /* Afnan: Blind Mode */
    private void listenToTracking(SelectedPath selectedPath) {
        speechToTextHelper.startSpeechRecognition(selectedPath , LISTEN_TO_TRACKING_OR_NOT);
    }

    /* Afnan: receiving answer from the user in Blind Mode */
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        speechToTextHelper.onActivityResult(requestCode, resultCode, data);                                         // Pass the onActivityResult event to the SpeechToTextHelper
        ArrayList<String> speechConvertedToText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

        switch (requestCode) {
            case LISTEN_TO_TRACKING_OR_NOT:
                String trackingResponse = convertHaaToTaaMarbuta(speechConvertedToText.get(0));
                if (stringIsFound(trackingResponse, TRACKING_OR_NOT)) {
                    //Afnan: in case of Yes for Tracking
                    if (trackingResponse.equals(CHOOSING_PATH_OR_NOT[0]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[1]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[2])
                            || trackingResponse.equals(CHOOSING_PATH_OR_NOT[3]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[4]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[5])
                            || trackingResponse.equals(CHOOSING_PATH_OR_NOT[6]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[7]))
                    {
                       /* Button startLiveLocation= findViewById(R.id.startLiveLocationBtn);
                        startLiveLocation.performClick();*/
                        binding.startLiveLocationBtn.performClick();
                    }
                    //Afnan: in case of No Tracking
                    else if (trackingResponse.equals(CHOOSING_PATH_OR_NOT[8]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[9]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[10]) || trackingResponse.equals(CHOOSING_PATH_OR_NOT[11]))
                    {

                    }
                }
                else
                    textToSpeechHelper.speak("عفوا !", () -> listenToTracking(this));
        }
    }
}
