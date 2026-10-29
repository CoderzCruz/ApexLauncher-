package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom bioluminescent ocean-wave progress view matching Mockup #02 ('Abyssal Bioluminescent Ocean').
 * Renders dual layered sine-wave crests clipped cleanly inside rounded glass card corners.
 */
public class AbyssalWaveProgressView extends View {

    private final Path mClipPath = new Path();
    private final Path mBackWavePath = new Path();
    private final Path mFrontWavePath = new Path();
    private final Path mCrestStrokePath = new Path();
    private final RectF mBounds = new RectF();

    private final Paint mBackWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFrontWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCrestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mProgress = 84; // 0..100
    private boolean mAnimating = false;
    private float mPhase = 0.6f;
    private float mCornerRadiusPx;

    public AbyssalWaveProgressView(Context context) {
        this(context, null);
    }

    public AbyssalWaveProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbyssalWaveProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        mCornerRadiusPx = 19f * density;

        mBackWavePaint.setStyle(Paint.Style.FILL);
        mFrontWavePaint.setStyle(Paint.Style.FILL);

        mCrestPaint.setStyle(Paint.Style.STROKE);
        mCrestPaint.setStrokeWidth(1.8f * density);
        mCrestPaint.setColor(0xE6A5FFF8);
    }

    public void setProgress(int progress) {
        int clamped = Math.max(0, Math.min(100, progress));
        if (mProgress != clamped) {
            mProgress = clamped;
            invalidate();
        }
    }

    public int getProgress() {
        return mProgress;
    }

    public void setWaveActive(boolean active) {
        if (mAnimating != active) {
            mAnimating = active;
            if (active) {
                postInvalidateOnAnimation();
            } else {
                invalidate();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBounds.set(0, 0, w, h);
        mClipPath.reset();
        mClipPath.addRoundRect(mBounds, mCornerRadiusPx, mCornerRadiusPx, Path.Direction.CW);

        if (h > 0) {
            mBackWavePaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    0x6600E5C4, 0x38056676,
                    Shader.TileMode.CLAMP));
            mFrontWavePaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    0xD92EFFE9, 0xB3009E91,
                    Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int save = canvas.save();
        canvas.clipPath(mClipPath);

        // Map progress (0..100) into visually balanced wave baseline inside the bottom half of the card
        float fillRatio = 0.24f + (mProgress / 100f) * 0.34f;
        float baseY = h * (1f - fillRatio);
        float amp1 = Math.max(3f, h * 0.065f);
        float amp2 = Math.max(2.5f, h * 0.052f);

        buildWavePath(mBackWavePath, null, w, h, baseY - amp2 * 0.4f, amp2, 2.1f, -mPhase * 0.8f + 1.9f);
        canvas.drawPath(mBackWavePath, mBackWavePaint);

        buildWavePath(mFrontWavePath, mCrestStrokePath, w, h, baseY, amp1, 2.3f, mPhase);
        canvas.drawPath(mFrontWavePath, mFrontWavePaint);
        canvas.drawPath(mCrestStrokePath, mCrestPaint);

        canvas.restoreToCount(save);

        if (mAnimating && isShown()) {
            mPhase += 0.11f;
            if (mPhase > (float) (Math.PI * 20)) {
                mPhase -= (float) (Math.PI * 20);
            }
            postInvalidateDelayed(36L);
        }
    }

    private void buildWavePath(
            Path fillOut,
            @Nullable Path strokeOut,
            int width,
            int height,
            float baseY,
            float amplitude,
            float cycles,
            float phase) {
        fillOut.reset();
        if (strokeOut != null) strokeOut.reset();

        final int steps = 36;
        float dx = (float) width / steps;
        for (int i = 0; i <= steps; i++) {
            float x = i * dx;
            double angle = ((double) i / steps) * Math.PI * 2.0 * cycles + phase;
            float y = baseY + (float) Math.sin(angle) * amplitude;
            if (i == 0) {
                fillOut.moveTo(x, y);
                if (strokeOut != null) strokeOut.moveTo(x, y);
            } else {
                fillOut.lineTo(x, y);
                if (strokeOut != null) strokeOut.lineTo(x, y);
            }
        }
        fillOut.lineTo(width, height);
        fillOut.lineTo(0, height);
        fillOut.close();
    }
}
