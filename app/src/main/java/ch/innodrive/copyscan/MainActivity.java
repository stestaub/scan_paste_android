package ch.innodrive.copyscan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String CHANNEL_ID = "ch.innodirve.copyscan.ScanRequest";
    private Button btnConnect;
    private ImageButton btnDisconnect;
    private IntentIntegrator qrScan;

    private Button btnRetry;
    private TextView errorMessage;
    private TextView channel;
    private View connectedView;
    private View disconnectedView;
    private View errorView;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        setContentView(R.layout.activity_main);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.disconnect);
        btnRetry = findViewById(R.id.btn_retry);

        connectedView = findViewById(R.id.connected_view);
        disconnectedView = findViewById(R.id.disconnected_view);
        errorView = findViewById(R.id.error_panel);

        btnConnect.setOnClickListener(view -> startQrScan());
        btnDisconnect.setOnClickListener(view -> disconnect());
        btnRetry.setOnClickListener(view -> reconnect());
        errorMessage = findViewById(R.id.error_message);
        channel = findViewById(R.id.scan_result);
        qrScan = new IntentIntegrator(this);

        createNotificationChannel();
    }

    private void reconnect() {
        signIn();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(true);
            channel.setVibrationPattern(new long[] { 500, 500 });
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    public void signIn() {

        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInAnonymously:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInAnonymously:failure", task.getException());
                        updateUI(null);
                    }
                });

    }

    public void showError(String message) {
        connectedView.setVisibility(View.GONE);
        disconnectedView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null) {
            signIn();
        }
        else {
            updateUI(currentUser);
        }
    }

    public void updateUI(FirebaseUser currentUser) {
        if (currentUser == null) {
            showError(getResources().getString(R.string.connection_error));
            return;
        }

        hideError();
        if(isConnected()) {
            applyConnectedState();
        } else {
            applyDisconnectedState();
        }
    }

    public boolean isConnected() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String channelId = sharedPref.getString("channelId", null);
        return channelId != null;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            //if qrcode has nothing in it
            if (result.getContents() == null) {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            } else {
                String rawDeviceInfo = result.getContents();
                connect(rawDeviceInfo);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void connect(String rawConnectionData) {
        try {
            JSONObject connectionData = new JSONObject(rawConnectionData);
            String channel = connectionData.getString("channel");
            JSONObject os = connectionData.getJSONObject("os");
            JSONObject browser = connectionData.getJSONObject("browser");

            String osName = os.getString("name");
            String osVersion = os.getString("version");
            String browserName = browser.getString("name");
            String browserVersion =  browser.getString("version");

            storeConnection(channel);
            storeDeviceInfo(osName, osVersion, browserName, browserVersion);
            applyDeviceIdToChannel(channel);

            applyConnectedState();
            Toast.makeText(this, "Connected To: " + getConnectedDeviceString(), Toast.LENGTH_LONG).show();

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not parse QR Code", Toast.LENGTH_LONG).show();
        }
    }

    private void storeDeviceInfo(String osName, String osVersion, String browserName, String browserVersion) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("osName", osName);
        editor.putString("osVersion", osVersion);
        editor.putString("browserName", browserName);
        editor.putString("browserVersion", browserVersion);
        editor.apply();
    }

    private void storeConnection(String channelId) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("channelId", channelId);
        editor.apply();
    }

    private void applyConnectedState() {
        connectedView.setVisibility(View.VISIBLE);
        disconnectedView.setVisibility(View.GONE);
        channel.setText(getConnectedDeviceString());
    }

    private String getConnectedDeviceString() {
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        String osName = sharedPreferences.getString("osName", "Unknown");
        String osVersion = sharedPreferences.getString("osVersion", "Unknown");
        String browserName = sharedPreferences.getString("browserName", "Unknown");
        String browserVersion = sharedPreferences.getString("browserVersion", "Unknown");

        return String.format("%s %s - %s %s", osName, osVersion, browserName, browserVersion);
    }

    private void applyDisconnectedState() {
        disconnectedView.setVisibility(View.VISIBLE);
        connectedView.setVisibility(View.GONE);
        channel.setText("");
    }

    private void disconnect() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String channelId = sharedPref.getString("channelId", null);
        if(channelId != null) {
            editor.remove("channelId");
            editor.apply();
            deleteChannel(channelId);
        }
        btnConnect.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Successfully Disconnected" + channelId, Toast.LENGTH_LONG).show();
        applyDisconnectedState();
    }

    private void deleteChannel(String channelId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference("channels").child(channelId).removeValue();
    }

    private void applyDeviceIdToChannel(String channelId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("channels/" + channelId);
        DeviceInfo data = new DeviceInfo(
                MyFirebaseMessagingService.getToken(this),
                Build.MODEL,
                mAuth.getUid());
        myRef.setValue(data);
    }


    private void startQrScan() {
        qrScan.setOrientationLocked(false);
        qrScan.initiateScan();
    }
}