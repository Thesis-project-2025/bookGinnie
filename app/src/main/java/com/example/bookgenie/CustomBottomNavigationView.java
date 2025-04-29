package com.example.bookgenie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class CustomBottomNavigationView extends BottomNavigationView {
    private Path path;
    private Paint paint;
    private float radius = 60f; // köşe yuvarlaklığı

    public CustomBottomNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomBottomNavigationView(Context context) {
        super(context);
        init();
    }

    public CustomBottomNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        path = new Path();
        paint = new Paint();
        paint.setColor(android.graphics.Color.parseColor("#94B4C1")); // İstediğin renk
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float fabDiameter = 100f; // FAB için boşluk büyüklüğü
        float centerX = width / 2;

        float padding = 40f; // sağ ve soldan boşluk verelim
        float top = 0f;
        float bottom = height;

        path.reset();

        // Üst sol köşe (start)
        path.moveTo(padding + radius, top);

        // Sol üst düz çizgi
        path.lineTo(centerX - fabDiameter, top);

        // Ortada çukur (quadTo ile yumuşak bir curve)
        path.quadTo(centerX, fabDiameter, centerX + fabDiameter, top);

        // Sağ üst düz çizgi
        path.lineTo(width - padding - radius, top);

        // Sağ üst köşe (yuvarlat)
        path.quadTo(width - padding, top, width - padding, top + radius);

        // Sağ alt düz çizgi
        path.lineTo(width - padding, bottom - radius);

        // Sağ alt köşe (yuvarlat)
        path.quadTo(width - padding, bottom, width - padding - radius, bottom);

        // Sol alt düz çizgi
        path.lineTo(padding + radius, bottom);

        // Sol alt köşe (yuvarlat)
        path.quadTo(padding, bottom, padding, bottom - radius);

        // Sol üst düz çizgi (başlangıca dön)
        path.lineTo(padding, top + radius);

        // Sol üst köşe (yuvarlat)
        path.quadTo(padding, top, padding + radius, top);

        path.close();

        canvas.drawPath(path, paint);
    }
}
