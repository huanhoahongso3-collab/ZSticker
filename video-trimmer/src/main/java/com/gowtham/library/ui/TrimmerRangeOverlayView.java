package com.gowtham.library.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * Material 3 Trimmer Range Overlay sitting directly on top of the filmstrip container.
 * The underlying filmstrip card is inset horizontally by {@code handleWidthPx} (16dp) on each side,
 * so the Left and Right handles frame the 0.0..1.0 filmstrip viewport without obscuring the first
 * or last frames.
 */
public class TrimmerRangeOverlayView extends View {

    public interface OnTrimRangeListener {
        void onTrimRangeChanged(float startPercent, float endPercent, boolean isFinal);
    }

    private static final int DRAG_NONE = 0;
    private static final int DRAG_LEFT = 1;
    private static final int DRAG_RIGHT = 2;
    private static final int DRAG_CENTER = 3;

    private int activeDragMode = DRAG_NONE;
    private float downTouchX = 0f;
    private float downStartPercent = 0f;
    private float downEndPercent = 1f;

    // Range represented as 0.0f .. 1.0f of the inner filmstrip viewport width
    private float startPercent = 0.0f;
    private float endPercent = 1.0f;
    private float minSpanPercent = 0.20f;
    private float maxSpanPercent = 1.00f;
    private boolean allowCenterDrag = false;

    // Playhead position (0.0f .. 1.0f of viewport width, or -1f if disabled)
    private float playheadPercent = -1f;

    private int accentColor = Color.parseColor("#F57C00");
    private int gripColor = Color.WHITE;
    private final int maskColor = Color.parseColor("#A8000000");

    private final Paint paintMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHandle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPlayhead = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectLeftMask = new RectF();
    private final RectF rectRightMask = new RectF();
    private final RectF rectLeftHandle = new RectF();
    private final RectF rectRightHandle = new RectF();
    private final RectF rectOuterBracket = new RectF();
    private final RectF rectPlayhead = new RectF();
    private final Path pathLeftHandle = new Path();
    private final Path pathRightHandle = new Path();

    private float handleWidthPx;
    private float verticalInsetPx;
    private float outerCornerRadiusPx;
    private float borderThicknessPx;
    private float touchTargetSizePx;
    private float gripHalfHeightPx;
    private float playheadHalfWidthPx;

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
        verticalInsetPx = 3f * density;
        outerCornerRadiusPx = 11f * density;
        borderThicknessPx = 3f * density;
        touchTargetSizePx = 44f * density;
        gripHalfHeightPx = 9f * density;
        playheadHalfWidthPx = 1.75f * density;

        paintMask.setColor(maskColor);
        paintMask.setStyle(Paint.Style.FILL);

        paintFrame.setColor(accentColor);
        paintFrame.setStyle(Paint.Style.FILL);

        paintHandle.setColor(accentColor);
        paintHandle.setStyle(Paint.Style.FILL);

        paintGrip.setColor(gripColor);
        paintGrip.setStyle(Paint.Style.STROKE);
        paintGrip.setStrokeWidth(2.2f * density);
        paintGrip.setStrokeCap(Paint.Cap.ROUND);

        paintPlayhead.setColor(Color.WHITE);
        paintPlayhead.setStyle(Paint.Style.FILL);
        paintPlayhead.setShadowLayer(4f * density, 0f, 0f, Color.parseColor("#99000000"));
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        this.gripColor = ColorUtils.calculateLuminance(color) > 0.5 ? Color.parseColor("#1C1B1F") : Color.WHITE;
        paintFrame.setColor(color);
        paintHandle.setColor(color);
        paintGrip.setColor(gripColor);
        invalidate();
    }

    public void setAllowCenterDrag(boolean allowCenterDrag) {
        this.allowCenterDrag = allowCenterDrag;
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

        float innerLeft = handleWidthPx;
        float innerRight = Math.max(innerLeft + 1f, w - handleWidthPx);
        float innerWidth = innerRight - innerLeft;

        float leftX = innerLeft + (startPercent * innerWidth);
        float rightX = innerLeft + (endPercent * innerWidth);
        float topY = verticalInsetPx;
        float bottomY = h - verticalInsetPx;

        // 1. Draw Left Mask (unselected region inside filmstrip)
        if (leftX > innerLeft + 0.5f) {
            rectLeftMask.set(innerLeft, topY, leftX, bottomY);
            canvas.drawRoundRect(rectLeftMask, outerCornerRadiusPx, outerCornerRadiusPx, paintMask);
            if (leftX - innerLeft > outerCornerRadiusPx) {
                canvas.drawRect(innerLeft + outerCornerRadiusPx, topY, leftX, bottomY, paintMask);
            }
        }

        // 2. Draw Right Mask (unselected region inside filmstrip)
        if (rightX < innerRight - 0.5f) {
            rectRightMask.set(rightX, topY, innerRight, bottomY);
            canvas.drawRoundRect(rectRightMask, outerCornerRadiusPx, outerCornerRadiusPx, paintMask);
            if (innerRight - rightX > outerCornerRadiusPx) {
                canvas.drawRect(rightX, topY, innerRight - outerCornerRadiusPx, bottomY, paintMask);
            }
        }

        // 3. Draw Top & Bottom Selection Borders connecting the Left and Right handles
        canvas.drawRect(leftX - 1f, topY, rightX + 1f, topY + borderThicknessPx, paintFrame);
        canvas.drawRect(leftX - 1f, bottomY - borderThicknessPx, rightX + 1f, bottomY, paintFrame);

        // 4. Draw Left Bracket Handle (rounded on outer left corners, flush on inner right edge)
        rectLeftHandle.set(leftX - handleWidthPx, topY, leftX, bottomY);
        pathLeftHandle.reset();
        float[] leftRadii = new float[]{
                outerCornerRadiusPx, outerCornerRadiusPx,
                0f, 0f,
                0f, 0f,
                outerCornerRadiusPx, outerCornerRadiusPx
        };
        pathLeftHandle.addRoundRect(rectLeftHandle, leftRadii, Path.Direction.CW);
        canvas.drawPath(pathLeftHandle, paintHandle);

        // Left Handle Grip Pill
        float lMidX = rectLeftHandle.centerX();
        float midY = h / 2f;
        canvas.drawLine(lMidX, midY - gripHalfHeightPx, lMidX, midY + gripHalfHeightPx, paintGrip);

        // 5. Draw Right Bracket Handle (flush on inner left edge, rounded on outer right corners)
        rectRightHandle.set(rightX, topY, rightX + handleWidthPx, bottomY);
        pathRightHandle.reset();
        float[] rightRadii = new float[]{
                0f, 0f,
                outerCornerRadiusPx, outerCornerRadiusPx,
                outerCornerRadiusPx, outerCornerRadiusPx,
                0f, 0f
        };
        pathRightHandle.addRoundRect(rectRightHandle, rightRadii, Path.Direction.CW);
        canvas.drawPath(pathRightHandle, paintHandle);

        // Right Handle Grip Pill
        float rMidX = rectRightHandle.centerX();
        canvas.drawLine(rMidX, midY - gripHalfHeightPx, rMidX, midY + gripHalfHeightPx, paintGrip);

        // 6. Draw Playhead if within selection bounds
        if (playheadPercent >= startPercent && playheadPercent <= endPercent) {
            float playheadX = innerLeft + (playheadPercent * innerWidth);
            playheadX = Math.max(leftX + playheadHalfWidthPx, Math.min(rightX - playheadHalfWidthPx, playheadX));
            rectPlayhead.set(
                    playheadX - playheadHalfWidthPx,
                    topY - (1.5f * borderThicknessPx * 0.3f),
                    playheadX + playheadHalfWidthPx,
                    bottomY + (1.5f * borderThicknessPx * 0.3f)
            );
            canvas.drawRoundRect(rectPlayhead, playheadHalfWidthPx, playheadHalfWidthPx, paintPlayhead);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int w = getWidth();
        float innerWidth = w - (2f * handleWidthPx);
        if (innerWidth <= 1f) return false;

        float x = event.getX();
        float leftHandleCenter = ( handleWidthPx + (startPercent * innerWidth) ) - (handleWidthPx / 2f);
        float rightHandleCenter = ( handleWidthPx + (endPercent * innerWidth) ) + (handleWidthPx / 2f);
        float leftInnerX = handleWidthPx + (startPercent * innerWidth);
        float rightInnerX = handleWidthPx + (endPercent * innerWidth);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downTouchX = x;
                downStartPercent = startPercent;
                downEndPercent = endPercent;

                float halfHit = touchTargetSizePx / 2f;
                boolean hitLeft = Math.abs(x - leftHandleCenter) <= halfHit;
                boolean hitRight = Math.abs(x - rightHandleCenter) <= halfHit;

                if (hitLeft && hitRight) {
                    float dLeft = Math.abs(x - leftHandleCenter);
                    float dRight = Math.abs(x - rightHandleCenter);
                    activeDragMode = (dLeft <= dRight) ? DRAG_LEFT : DRAG_RIGHT;
                } else if (hitLeft) {
                    activeDragMode = DRAG_LEFT;
                } else if (hitRight) {
                    activeDragMode = DRAG_RIGHT;
                } else if (allowCenterDrag && x > leftInnerX && x < rightInnerX && (endPercent - startPercent) < 0.999f) {
                    activeDragMode = DRAG_CENTER;
                } else {
                    // Let touches in the center pass through to the scrollable filmstrip RecyclerView
                    activeDragMode = DRAG_NONE;
                    return false;
                }

                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (activeDragMode == DRAG_NONE) return false;

                float deltaPercent = (x - downTouchX) / innerWidth;

                if (activeDragMode == DRAG_LEFT) {
                    float newStart = downStartPercent + deltaPercent;
                    newStart = Math.max(0f, Math.min(newStart, endPercent - minSpanPercent));
                    if ((endPercent - newStart) > maxSpanPercent) {
                        newStart = endPercent - maxSpanPercent;
                    }
                    if (Math.abs(newStart - startPercent) > 0.0005f) {
                        startPercent = newStart;
                        invalidate();
                        if (rangeListener != null) {
                            rangeListener.onTrimRangeChanged(startPercent, endPercent, false);
                        }
                    }
                } else if (activeDragMode == DRAG_RIGHT) {
                    float newEnd = downEndPercent + deltaPercent;
                    newEnd = Math.max(startPercent + minSpanPercent, Math.min(newEnd, 1f));
                    if ((newEnd - startPercent) > maxSpanPercent) {
                        newEnd = startPercent + maxSpanPercent;
                    }
                    if (Math.abs(newEnd - endPercent) > 0.0005f) {
                        endPercent = newEnd;
                        invalidate();
                        if (rangeListener != null) {
                            rangeListener.onTrimRangeChanged(startPercent, endPercent, false);
                        }
                    }
                } else if (activeDragMode == DRAG_CENTER) {
                    float span = downEndPercent - downStartPercent;
                    float newStart = Math.max(0f, Math.min(1f - span, downStartPercent + deltaPercent));
                    float newEnd = newStart + span;
                    if (Math.abs(newStart - startPercent) > 0.0005f || Math.abs(newEnd - endPercent) > 0.0005f) {
                        startPercent = newStart;
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
