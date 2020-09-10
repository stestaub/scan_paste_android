package ch.innodrive.copyscan;

import android.annotation.SuppressLint;
import android.media.Image;
import android.os.Build;
import android.util.Log;


import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

interface AnalyzeResultListener {
    void onSuccess(Text result);
}

class TextAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "TextAnalyzer";
    private AnalyzeResultListener onSuccessListener;

    public void setOnSuccessListener(AnalyzeResultListener onSuccessListener) {
        this.onSuccessListener = onSuccessListener;
    }

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
                            onSuccessListener.onSuccess(visionText);
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
