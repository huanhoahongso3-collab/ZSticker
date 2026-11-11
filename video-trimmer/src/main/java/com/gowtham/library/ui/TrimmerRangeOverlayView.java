package com.gowtham.library.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Material 3 Trimmer Range Overlay sitting directly on top of the filmstrip.
 * Renders:
 * - Dimmed curtain masks on left and right unselected regions.
 * - Solid accent selection frame (top and bottom border lines).
 * - Left and Right bracket handles with 48dp touch targets and grip ridges.
 * - Playhead vertical indicator line inside the selection window.
 *
 * Touch behavior:
 * - Intercepts touches on Left handle to resize Start time.
 * - Intercepts touches on Right handle to resize End time.
 * - Lets touches in the center region pass through so the user can scroll the underlying filmstrip!
 */
public class TrimmerRangeOverlayView extends View {

    public interface OnTrimRangeListener {
        void onTrimRangeChanged(float startPercent, float endPercent, boolean isFinal);
    }

    private static final int DRAG_NONE = 0;
    private static final int DRAG_LEFT = 1;
    private static final int DRAG_RIGHT = 2;

    private int activeDragMode = DRAG_NONE;
    private float downTouchX = 0f;
    private float downStartPercent = 0f;
    private float downEndPercent = 1f;

    // Range represented as 0.0f .. 1.0f of viewport width
    private float startPercent = 0.0f;
    private float endPercent = 1.0f;
    private float minSpanPercent = 0.20f; // e.g. 1s / 5s
    private float maxSpanPercent = 1.00f; // 5s / 5s

    // Playhead position (0.0f .. 1.0f of viewport width, or -1f if disabled)
    private float playheadPercent = -1f;

    private int accentColor = Color.parseColor("#F57C00");
    private final int maskColor = Color.parseColor("#99000000");

    private final Paint paintMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHandle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPlayhead = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectLeftHandle = new RectF();
    private final RectF rectRightHandle = new RectF();
    private final RectF rectSelection = new RectF();

    private float handleWidthPx;
    private float handleCornerRadius;
    private float touchTargetSizePx;

    private OnTrimRangeListener rangeListener;

    public TrimmerRangeOverlayView(Context context) {
        this(context, null);
    }

    public TrimmerRangeOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TrimmerRangeOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        handleWidthPx = 16f * density;
        handleCornerRadius = 8f * density;
        touchTargetSizePx = 48f * density;

        paintMask.setColor(maskColor);
        paintMask.setStyle(Paint.Style.FILL);

        paintFrame.setColor(accentColor);
        paintFrame.setStyle(Paint.Style.STROKE);
        paintFrame.setStrokeWidth(3.5f * density);

        paintHandle.setColor(accentColor);
        paintHandle.setStyle(Paint.Style.FILL);

        paintGrip.setColor(Color.WHITE);
        paintGrip.setStyle(Paint.Style.STROKE);
        paintGrip.setStrokeWidth(2f * density);
        paintGrip.setStrokeCap(Paint.Cap.ROUND);

        paintPlayhead.setColor(Color.WHITE);
        paintPlayhead.setStyle(Paint.Style.FILL);
        paintPlayhead.setShadowLayer(4f * density, 0f, 0f, Color.parseColor("#80000000"));
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        paintFrame.setColor(color);
        paintHandle.setColor(color);
        invalidate();
    }

    public void setRange(float start, float end, float minSpan, float maxSpan) {
        this.startPercent = Math.max(0f, Math.min(start, 1f));
        this.endPercent = Math.max(this.startPercent, Math.min(end, 1f));
        this.minSpanPercent = Math.max(0.01f, minSpan);
        this.maxSpanPercent = Math.max(this.minSpanPercent, Math.min(maxSpan, 1f));
        invalidate();
    }

    public void setPlayheadPercent(float percent) {
        this.playheadPercent = percent;
        invalidate();
    }

    public void setOnTrimRangeListener(OnTrimRangeListener listener) {
        this.rangeListener = listener;
    }

    public float getStartPercent() {
        return startPercent;
    }

    public float getEndPercent() {
        return endPercent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float leftX = startPercent * w;
        float rightX = endPercent * w;

        // 1. Draw Left Mask (curtain)
        if (leftX > 0f) {
            canvas.drawRect(0f, 0f, leftX, h, paintMask);
        }

        // 2. Draw Right Mask (curtain)
        if (rightX < w) {
            canvas.drawRect(rightX, 0f, w, h, paintMask);
        }

        // 3. Draw Active Selection Border (Top & Bottom)
        float strokeInset = paintFrame.getStrokeWidth() / 2f;
        canvas.drawLine(leftX, strokeInset, rightX, strokeInset, paintFrame);
        canvas.drawLine(leftX, h - strokeInset, rightX, h - strokeInset, paintFrame);

        // 4. Draw Left Bracket Handle
        rectLeftHandle.set(leftX, 0f, leftX + handleWidthPx, h);
        canvas.drawRoundRect(rectLeftHandle, handleCornerRadius, handleCornerRadius, paintHandle);
        // Draw Left Handle Grip bars
        float lMidX = rectLeftHandle.centerX();
        float midY = h / 2f;
        canvas.drawLine(lMidX - 2f, midY - 10f, lMidX - 2f, midY + 10f, paintGrip);
        canvas.drawLine(lMidX + 2f, midY - 10f, lMidX + 2f, midY + 10f, paintGrip);

        // 5. Draw Right Bracket Handle
        rectRightHandle.set(rightX - handleWidthPx, 0f, rightX, h);
        canvas.drawRoundRect(rectRightHandle, handleCornerRadius, handleCornerRadius, paintHandle);
        // Draw Right Handle Grip bars
        float rMidX = rectRightHandle.centerX();
        canvas.drawLine(rMidX - 2f, midY - 10f, rMidX - 2f, midY + 10f, paintGrip);
        canvas.drawLine(rMidX + 2f, midY - 10f, rMidX + 2f, midY + 10f, paintGrip);

        // 6. Draw Playhead if within selection bounds
        if (playheadPercent >= startPercent && playheadPercent <= endPercent) {
            float playheadX = playheadPercent * w;
            canvas.drawRect(playheadX - 1.5f, 0f, playheadX + 1.5f, h, paintPlayhead);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int w = getWidth();
        if (w <= 0) return false;

        float x = event.getX();
        float leftX = startPercent * w;
        float rightX = endPercent * w;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downTouchX = x;
                downStartPercent = startPercent;
                downEndPercent = endPercent;

                // Test Left Handle with expanded hit target (biased slightly outward)
                float leftHitStart = leftX - (touchTargetSizePx - handleWidthPx);
                float leftHitEnd = leftX + touchTargetSizePx;
                boolean hitLeft = x >= leftHitStart && x <= leftHitEnd;

                // Test Right Handle with expanded hit target (biased slightly outward)
                float rightHitStart = rightX - touchTargetSizePx;
                float rightHitEnd = rightX + (touchTargetSizePx - handleWidthPx);
                boolean hitRight = x >= rightHitStart && x <= rightHitEnd;

                if (hitLeft && hitRight) {
                    // When handles are very close, decide by which one is closer
                    float dLeft = Math.abs(x - leftX);
                    float dRight = Math.abs(x - rightX);
                    activeDragMode = (dLeft < dRight) ? DRAG_LEFT : DRAG_RIGHT;
                } else if (hitLeft) {
                    activeDragMode = DRAG_LEFT;
                } else if (hitRight) {
                    activeDragMode = DRAG_RIGHT;
                } else {
                    // Touch in center or outside: DO NOT consume here so underlying RecyclerView can scroll!
                    activeDragMode = DRAG_NONE;
                    return false;
                }

                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (activeDragMode == DRAG_NONE) return false;

                float deltaPercent = (x - downTouchX) / (float) w;

                if (activeDragMode == DRAG_LEFT) {
                    float newStart = downStartPercent + deltaPercent;
                    // Clamp start: 0 <= newStart <= endPercent - minSpanPercent
                    newStart = Math.max(0f, Math.min(newStart, endPercent - minSpanPercent));
                    if ((endPercent - newStart) > maxSpanPercent) {
                        newStart = endPercent - maxSpanPercent;
                    }
                    if (newStart != startPercent) {
                        startPercent = newStart;
                        invalidate();
                        if (rangeListener != null) {
                            rangeListener.onTrimRangeChanged(startPercent, endPercent, false);
                        }
                    }
                } else if (activeDragMode == DRAG_RIGHT) {
                    float newEnd = downEndPercent + deltaPercent;
                    // Clamp end: startPercent + minSpanPercent <= newEnd <= 1f
                    newEnd = Math.max(startPercent + minSpanPercent, Math.min(newEnd, 1f));
                    if ((newEnd - startPercent) > maxSpanPercent) {
                        newEnd = startPercent + maxSpanPercent;
                    }
                    if (newEnd != endPercent) {
                        endPercent = newEnd;
                        invalidate();
                        if (rangeListener != null) {
                            rangeListener.onTrimRangeChanged(startPercent, endPercent, false);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (activeDragMode != DRAG_NONE) {
                    activeDragMode = DRAG_NONE;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (rangeListener != null) {
                        rangeListener.onTrimRangeChanged(startPercent, endPercent, true);
                    }
                    return true;
                }
                break;
            }
        }
        return false;
    }
}
