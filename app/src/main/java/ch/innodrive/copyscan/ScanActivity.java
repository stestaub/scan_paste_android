package ch.innodrive.copyscan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.text.Text;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
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

    private Preview previewUseCase;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;
    private ImageAnalysis imageAnalyzer;

    private GraphicOverlay overlayView;
    private PreviewView viewFinder;
    private TextOverlay overlay;

    private String scanRequest = "50e26865b125dd6faa9c8647158f6f64c6bf619304a81ff72d1f522f455334";
    private boolean needUpdateGraphicOverlayImageSourceInfo;
    private FirebaseAuth mAuth;

    private boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Text.Line res = findTappedBlock(Math.round(motionEvent.getX()), Math.round(motionEvent.getY()));
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
        overlayView.clear();
        overlay = new TextOverlay(overlayView, textResults);
        overlayView.add(overlay);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        mAuth = FirebaseAuth.getInstance();
        mAuth.signInAnonymously();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraExecutor.shutdownNow();
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
        myRef.setValue(text).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "successfully updated scan result");
            }
            else {
                Log.e(TAG, "Unable to write scanresult");
                Log.i(TAG, "Current user is; " + mAuth.getUid());
                Log.e(TAG, task.getException().getLocalizedMessage());
            }
        });
    }

    private Text.Line findTappedBlock(int x, int y) {
        return overlay.intersect(x, y);
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

    private void bindPreviewUseCase() {
        if (cameraProvider == null) {
            return;
        }
        if (previewUseCase != null) {
            cameraProvider.unbind(previewUseCase);
        }

        previewUseCase = new Preview.Builder()
                .build();
        previewUseCase.setSurfaceProvider(viewFinder.createSurfaceProvider());
        cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase);
    }

    private void bindAnalysisUseCase() {
        if(cameraProvider == null) {
            return;
        }
        if (imageAnalyzer != null) {
            cameraProvider.unbind(imageAnalyzer);
        }
        needUpdateGraphicOverlayImageSourceInfo = true;

        imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();

        imageAnalyzer.setAnalyzer(
                ContextCompat.getMainExecutor(this),
                imageProxy-> {
                    if (needUpdateGraphicOverlayImageSourceInfo) {
                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                        if (rotationDegrees == 0 || rotationDegrees == 180) {
                            overlayView.setImageSourceInfo(
                                    imageProxy.getWidth(), imageProxy.getHeight(), false);
                        } else {
                            overlayView.setImageSourceInfo(
                                    imageProxy.getHeight(), imageProxy.getWidth(), false);
                        }
                        needUpdateGraphicOverlayImageSourceInfo = false;
                    }
                    textAnalyzer.analyze(imageProxy);
                });
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {

            try {
                cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                bindPreviewUseCase();
                bindAnalysisUseCase();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
