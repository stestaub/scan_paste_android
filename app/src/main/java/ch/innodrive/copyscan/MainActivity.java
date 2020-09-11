package ch.innodrive.copyscan;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends AppCompatActivity {

    private Button btnScan;
    private IntentIntegrator qrScan;
    private TextView channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnScan = findViewById(R.id.button);
        btnScan.setOnClickListener(view -> startQrScan());
        channel = findViewById(R.id.scan_result);
        qrScan = new IntentIntegrator(this);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            //if qrcode has nothing in it
            if (result.getContents() == null) {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            } else {
                String channelId = result.getContents();
                storeConnection(channelId);
                Toast.makeText(this, "Connected To: " + channelId, Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void storeConnection(String channelId) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("channelId", channelId);
        editor.apply();
        applyDeviceIdToChannel(channelId);
    }

    private void applyDeviceIdToChannel(String channelId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("channels/" + channelId);
        myRef.setValue(MyFirebaseMessagingService.getToken(this));
    }


    private void startQrScan() {
        qrScan.initiateScan();
    }
}