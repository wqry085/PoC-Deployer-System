package com.wqry085.deployesystem;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class SnowflakeView extends View {

    private static final int NUM_SNOWFLAKES = 150;
    private static final int FRAME_RATE = 30;

    private Snowflake[] snowflakes;
    private Paint paint;

    public SnowflakeView(Context context) {
        super(context);
        init();
    }

    public SnowflakeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SnowflakeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            snowflakes = new Snowflake[NUM_SNOWFLAKES];
            Random random = new Random();
            for (int i = 0; i < NUM_SNOWFLAKES; i++) {
                snowflakes[i] = new Snowflake(random, w, h);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (snowflakes == null) {
            return;
        }

        for (Snowflake snowflake : snowflakes) {
            snowflake.update();
            snowflake.draw(canvas, paint);
        }

        postInvalidateDelayed(1000 / FRAME_RATE);
    }

    private static class Snowflake {
        private float x, y;
        private float radius;
        private float speed;
        private int screenWidth, screenHeight;
        private Random random;

        public Snowflake(Random random, int screenWidth, int screenHeight) {
            this.random = random;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            reset();
        }

        public void reset() {
            x = random.nextInt(screenWidth);
            y = -random.nextInt(screenHeight);
            radius = random.nextFloat() * 5 + 2; // 2 to 7
            speed = random.nextFloat() * 4 + 2;  // 2 to 6
        }

        public void update() {
            y += speed;
            if (y > screenHeight) {
                reset();
                y = -radius;
            }
        }

        public void draw(Canvas canvas, Paint paint) {
            paint.setAlpha(150); // semi-transparent
            canvas.drawCircle(x, y, radius, paint);
        }
    }
}
