package dhp.thl.tpl.ndv;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EasterEggActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private ImageView imgLogo;
    private final Random random = new Random();
    private final List<View> mosaicStickers = new ArrayList<>();

    // Resource IDs for your specific images
    private final int[] stickerRes = {R.drawable.thl, R.drawable.tpl, R.drawable.ndv};
    private List<String> emojiPool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Setup Transparent Root
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        initEmojiPool();
        setupLogo();

        // 2. Spawn Mosaic after layout measurement
        rootLayout.post(() -> {
            spawnDenseMosaic();
            imgLogo.bringToFront(); // Ensure logo is on top after spawning
        });
    }

    private void setupLogo() {
        imgLogo = new ImageView(this);
        int size = (int) (126 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        imgLogo.setLayoutParams(params);

        // Foreground Image
        imgLogo.setImageResource(R.drawable.ic_launcher_foreground);

        // Circular White Frame
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.WHITE);
        shape.setStroke(4, Color.LTGRAY);
        imgLogo.setBackground(shape);

        imgLogo.setElevation(100f);
        imgLogo.setClickable(true);

        imgLogo.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            reshuffleMosaic();
            showRandomEmojiToast();
        });

        rootLayout.addView(imgLogo);
    }

    private void spawnDenseMosaic() {
        int width = rootLayout.getWidth();
        int height = rootLayout.getHeight();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 150; i++) {
            ImageView sticker = new ImageView(this);
            int sizeDp = random.nextInt(60) + 70; // 70dp to 130dp
            int sizePx = (int) (sizeDp * density);

            sticker.setImageResource(stickerRes[random.nextInt(stickerRes.length)]);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
            sticker.setLayoutParams(params);

            // Random scatter
            sticker.setTranslationX(random.nextInt(width) - (sizePx / 2f));
            sticker.setTranslationY(random.nextInt(height) - (sizePx / 2f));
            sticker.setRotation(random.nextFloat() * 360f);
            sticker.setAlpha(0.7f);

            setupDraggable(sticker);
            rootLayout.addView(sticker);
            mosaicStickers.add(sticker);
        }
    }

    private void reshuffleMosaic() {
        int width = rootLayout.getWidth();
        int height = rootLayout.getHeight();

        for (View v : mosaicStickers) {
            v.animate()
            .translationX(random.nextInt(width) - (v.getWidth() / 2f))
            .translationY(random.nextInt(height) - (v.getHeight() / 2f))
            .rotation(random.nextFloat() * 360f)
            .setDuration(700)
            .setInterpolator(new OvershootInterpolator())
            .start();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // "Pop up" effect
                        v.animate().scaleX(1.4f).scaleY(1.4f).alpha(1f).setDuration(150).start();
                        v.bringToFront();
                        imgLogo.bringToFront(); // Force logo to stay top-most

                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        v.setX(event.getRawX() + dX);
                        v.setY(event.getRawY() + dY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Settle back down
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.7f).setDuration(150).start();
                        return true;
                }
                return false;
            }
        });
    }

    private void showRandomEmojiToast() {
        int count = random.nextInt(10) + 5;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(emojiPool.get(random.nextInt(emojiPool.size()))).append(" ");
        }
        Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_SHORT).show();
    }

    private void initEmojiPool() {
        emojiPool = new ArrayList<>();
        int[][] ranges = {{0x1F600, 0x1F64F}, {0x1F400, 0x1F4FF}, {0x1F300, 0x1F3FF}, {0x1F680, 0x1F6FF}};
        for (int[] range : ranges) {
            for (int i = range[0]; i <= range[1]; i++) {
                emojiPool.add(new String(Character.toChars(i)));
            }
        }
    }
}package dhp.thl.tpl.ndv;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EasterEggActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private ImageView imgLogo;
    private final Random random = new Random();
    private final List<View> mosaicStickers = new ArrayList<>();

    // Resource IDs for your specific images
    private final int[] stickerRes = {R.drawable.thl, R.drawable.tpl, R.drawable.ndv};
    private List<String> emojiPool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Setup Transparent Root
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        initEmojiPool();
        setupLogo();

        // 2. Spawn Mosaic after layout measurement
        rootLayout.post(() -> {
            spawnDenseMosaic();
            imgLogo.bringToFront(); // Ensure logo is on top after spawning
        });
    }

    private void setupLogo() {
        imgLogo = new ImageView(this);
        int size = (int) (126 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        imgLogo.setLayoutParams(params);

        // Foreground Image
        imgLogo.setImageResource(R.drawable.ic_launcher_foreground);

        // Circular White Frame
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.WHITE);
        shape.setStroke(4, Color.LTGRAY);
        imgLogo.setBackground(shape);

        imgLogo.setElevation(100f);
        imgLogo.setClickable(true);

        imgLogo.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            reshuffleMosaic();
            showRandomEmojiToast();
        });

        rootLayout.addView(imgLogo);
    }

    private void spawnDenseMosaic() {
        int width = rootLayout.getWidth();
        int height = rootLayout.getHeight();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 150; i++) {
            ImageView sticker = new ImageView(this);
            int sizeDp = random.nextInt(60) + 70; // 70dp to 130dp
            int sizePx = (int) (sizeDp * density);

            sticker.setImageResource(stickerRes[random.nextInt(stickerRes.length)]);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
            sticker.setLayoutParams(params);

            // Random scatter
            sticker.setTranslationX(random.nextInt(width) - (sizePx / 2f));
            sticker.setTranslationY(random.nextInt(height) - (sizePx / 2f));
            sticker.setRotation(random.nextFloat() * 360f);
            sticker.setAlpha(0.7f);

            setupDraggable(sticker);
            rootLayout.addView(sticker);
            mosaicStickers.add(sticker);
        }
    }

    private void reshuffleMosaic() {
        int width = rootLayout.getWidth();
        int height = rootLayout.getHeight();

        for (View v : mosaicStickers) {
            v.animate()
            .translationX(random.nextInt(width) - (v.getWidth() / 2f))
            .translationY(random.nextInt(height) - (v.getHeight() / 2f))
            .rotation(random.nextFloat() * 360f)
            .setDuration(700)
            .setInterpolator(new OvershootInterpolator())
            .start();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // "Pop up" effect
                        v.animate().scaleX(1.4f).scaleY(1.4f).alpha(1f).setDuration(150).start();
                        v.bringToFront();
                        imgLogo.bringToFront(); // Force logo to stay top-most

                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        v.setX(event.getRawX() + dX);
                        v.setY(event.getRawY() + dY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Settle back down
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.7f).setDuration(150).start();
                        return true;
                }
                return false;
            }
        });
    }

    private void showRandomEmojiToast() {
        int count = random.nextInt(10) + 5;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(emojiPool.get(random.nextInt(emojiPool.size()))).append(" ");
        }
        Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_SHORT).show();
    }

    private void initEmojiPool() {
        emojiPool = new ArrayList<>();
        int[][] ranges = {{0x1F600, 0x1F64F}, {0x1F400, 0x1F4FF}, {0x1F300, 0x1F3FF}, {0x1F680, 0x1F6FF}};
        for (int[] range : ranges) {
            for (int i = range[0]; i <= range[1]; i++) {
                emojiPool.add(new String(Character.toChars(i)));
            }
        }
    }
}
