package ch.innodrive.copyscan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String TAG = "CameraXBasic";

    private ExecutorService cameraExecutor;
    private ImageView overlayView;
    private Bitmap overlay;
    private PreviewView viewFinder;

    private class TextAnalyser implements ImageAnalysis.Analyzer {

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            @SuppressLint("UnsafeExperimentalUsageError")
            Image mediaImage = imageProxy.getImage();

            if (mediaImage != null) {
                try {
                    InputImage image =
                            InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    TextRecognizer recognizer = TextRecognition.getClient();
                    recognizer.process(image)
                            .addOnSuccessListener(visionText -> {
                                // Task completed successfully
                                Log.d(TAG, "imageSize: " + imageProxy.getHeight() + "x" + imageProxy.getWidth());
                                imageProxy.close();
                                updateOverlay(visionText);
                            })
                            .addOnFailureListener(
                                    e -> {
                                        // Task failed with an exception
                                        // ...
                                        Log.e(TAG, "analyze: Failed to analyse", e);
                                        imageProxy.close();
                                    });;
                }
                catch (Exception e) {
                    Log.e(TAG, "analyze: Error analyzing image", e);
                }
            }
        }
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
                    overlayView.setImageBitmap(overlay);
                }
        );
    }

    private void copy() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("pastebin");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        overlayView = findViewById(R.id.imageView);
        viewFinder = findViewById(R.id.viewFinder);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
        cameraExecutor = Executors.newSingleThreadExecutor();
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
                        .setTargetRotation(viewFinder.getDisplay().getRotation())
                        .build();

                preview.setSurfaceProvider(viewFinder.createSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(viewFinder.getWidth(), viewFinder.getHeight()))
                        .setTargetRotation(viewFinder.getDisplay().getRotation())
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, new TextAnalyser());

                cameraProvider.unbindAll();
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
