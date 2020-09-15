package ch.innodrive.copyscan;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.Text.TextBlock;

import static java.lang.StrictMath.max;
import static java.lang.StrictMath.min;

public class TextOverlay extends GraphicOverlay.Graphic {

    Text text;
    Paint paint;

    TextOverlay(GraphicOverlay overlay, Text text) {
        super(overlay);
        this.text = text;
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(128, 255, 255, 255);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);

        paint.setStrokeWidth(5f);
        postInvalidate();
    }

    public Text.Line intersect(int x, int y) {
        for (TextBlock block :
                this.text.getTextBlocks()) {
            if(block.getBoundingBox() != null) {
                for(Text.Line line : block.getLines()) {
                    if(line.getBoundingBox() != null &&
                            transform(line.getBoundingBox()).contains(x, y)) {
                        return line;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void draw(Canvas canvas) {
        for (TextBlock block :
                this.text.getTextBlocks()) {
            if(block.getBoundingBox() != null) {
                for(Text.Line line : block.getLines()) {
                    canvas.drawRect(transform(line.getBoundingBox()), paint);
                }
            }
        }
    }

    public RectF transform(Rect bb) {
        RectF rect = new RectF(bb);
        float x0 = translateX(rect.left);
        float x1 = translateX(rect.right);
        rect.left = min(x0, x1);
        rect.right = max(x0, x1);
        rect.top = translateY(rect.top);
        rect.bottom = translateY(rect.bottom);
        return rect;
    }
}
