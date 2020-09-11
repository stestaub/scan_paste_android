package ch.innodrive.copyscan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.text.Text;

import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ScanActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String TAG = "CameraXBasic";

    private ExecutorService cameraExecutor;
    private TextAnalyzer textAnalyzer;

    private ImageView overlayView;
    private Bitmap overlay;
    private PreviewView viewFinder;
    private Text ocrResults;

    private String scanRequest = "50e26865b125dd6faa9c8647158f6f64c6bf619304a81ff72d1f522f455334";

    private boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Text.TextBlock res = findTappedBlock(Math.round(motionEvent.getX()), Math.round(motionEvent.getY()));
                if (res != null) {
                    store(res.getText());
                    finish();
                }
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
            default:
                break;
        }
        return false;
    }

    private void updateOverlay(Text textResults) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f);
        overlay = Bitmap.createBitmap(viewFinder.getWidth(), viewFinder.getHeight(), Bitmap.Config.ARGB_8888);
        for (Text.TextBlock block :
                textResults.getTextBlocks()) {
            if(block.getBoundingBox() != null) {
                Canvas canvas = new Canvas(overlay);
                canvas.drawRect(block.getBoundingBox(), paint);
            }
        }
        runOnUiThread(
                () -> {
                    ocrResults = textResults;
                    overlayView.setImageBitmap(overlay);
                }
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readScanRquestFromIntent();

        setContentView(R.layout.activity_scan);
        overlayView = findViewById(R.id.imageView);
        viewFinder = findViewById(R.id.viewFinder);
        textAnalyzer = new TextAnalyzer();
        textAnalyzer.setOnSuccessListener(this::updateOverlay);

        super.onPostCreate(savedInstanceState);
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        overlayView.setOnTouchListener(this::onTouch);
    }

    private void readScanRquestFromIntent() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) { return; }

        String channelId = extras.getString("request_id");
        if(channelId != null && !channelId.isEmpty()) {
            this.scanRequest = channelId;
        }
    }

    private void store(String text) {
        if(scanRequest == null || scanRequest.isEmpty()) {
            return;
        }
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("request/" + scanRequest + "/result");
        myRef.setValue(text);
    }

    private Text.TextBlock findTappedBlock(int x, int y) {
        Text.TextBlock res = null;
        for (Text.TextBlock block: this.ocrResults.getTextBlocks()) {
            Rect bb =  block.getBoundingBox();
            if(bb != null && bb.contains(x, y)) {
                res = block;
                break;
            }
        }
        return res;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (this.allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        boolean granted = true;
        for(String it : REQUIRED_PERMISSIONS) {
            granted = granted && (ContextCompat.checkSelfPermission(
                    this, it) == PackageManager.PERMISSION_GRANTED);
        }
        return granted;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {

            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(viewFinder.getWidth(), viewFinder.getHeight()))
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                preview.setSurfaceProvider(viewFinder.createSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(viewFinder.getWidth(), viewFinder.getHeight()))
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, textAnalyzer);

                cameraProvider.unbindAll();
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
