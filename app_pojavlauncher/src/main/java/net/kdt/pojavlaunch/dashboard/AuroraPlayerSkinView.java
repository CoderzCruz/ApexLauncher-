package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws a stylised, full-body voxel player figure for the "Your Space" dashboard
 * right-hand column. Uses the real account head texture when available and falls
 * back to a hand-drawn blocky character, so the card is never empty.
 */
public class AuroraPlayerSkinView extends View {

    private static final int COLOR_SKIN = 0xFFC68C63;
    private static final int COLOR_SKIN_SHADE = 0xFFB87E58;
    private static final int COLOR_HAIR = 0xFF5A3A22;
    private static final int COLOR_SHIRT = 0xFF2BB3A3;
    private static final int COLOR_SHIRT_SHADE = 0xFF219A8C;
    private static final int COLOR_PANTS = 0xFF3B3A8E;
    private static final int COLOR_PANTS_SHADE = 0xFF2F2E76;
    private static final int COLOR_SHOES = 0xFF2A2A5E;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTemp = new RectF();
    private final Rect mSrcRect = new Rect();

    @Nullable private Bitmap mFace;
    private boolean mOnline = true;

    public AuroraPlayerSkinView(Context context) {
        this(context, null);
    }

    public AuroraPlayerSkinView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AuroraPlayerSkinView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFacePaint.setFilterBitmap(false);
        setWillNotDraw(false);
    }

    public void setFaceBitmap(@Nullable Bitmap face) {
        mFace = face;
        invalidate();
    }

    public void setOnline(boolean online) {
        if (mOnline != online) {
            mOnline = online;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mGlowPaint.setShader(new RadialGradient(
                    w / 2f, h * 0.44f, Math.max(w, h) * 0.62f,
                    new int[]{0x33_7D_E8_D0, 0x147DE8D0, 0x00000000},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (mGlowPaint.getShader() != null) {
            canvas.drawRect(0, 0, w, h, mGlowPaint);
        }

        // Blocky player proportions: 16u wide (arms out) x 32u tall
        float unit = Math.min(w / 17.5f, h / 34f);
        if (unit <= 0) return;

        final float cx = w / 2f;
        final float figureH = 32f * unit;
        final float top = Math.max(unit * 1.2f, (h - figureH) / 2f);

        final float headSize = 8f * unit;
        final float bodyW = 8f * unit;
        final float bodyH = 12f * unit;
        final float armW = 4f * unit;
        final float legW = 4f * unit;

        final float headLeft = cx - headSize / 2f;
        final float headTop = top;
        final float bodyLeft = cx - bodyW / 2f;
        final float bodyTop = headTop + headSize;
        final float legsTop = bodyTop + bodyH;

        // --- legs ---
        drawBox(canvas, bodyLeft, legsTop, legW, bodyH, COLOR_PANTS, COLOR_PANTS_SHADE);
        drawBox(canvas, bodyLeft + legW, legsTop, legW, bodyH, COLOR_PANTS, COLOR_PANTS_SHADE);
        // shoes
        drawBox(canvas, bodyLeft, legsTop + bodyH - unit * 1.2f, legW, unit * 1.2f, COLOR_SHOES, COLOR_SHOES);
        drawBox(canvas, bodyLeft + legW, legsTop + bodyH - unit * 1.2f, legW, unit * 1.2f, COLOR_SHOES, COLOR_SHOES);

        // --- torso ---
        drawBox(canvas, bodyLeft, bodyTop, bodyW, bodyH, COLOR_SHIRT, COLOR_SHIRT_SHADE);

        // --- arms (bare skin, short teal sleeve) ---
        drawBox(canvas, bodyLeft - armW, bodyTop, armW, bodyH, COLOR_SKIN, COLOR_SKIN_SHADE);
        drawBox(canvas, bodyLeft + bodyW, bodyTop, armW, bodyH, COLOR_SKIN, COLOR_SKIN_SHADE);
        drawBox(canvas, bodyLeft - armW, bodyTop, armW, bodyH * 0.34f, COLOR_SHIRT_SHADE, COLOR_SHIRT_SHADE);
        drawBox(canvas, bodyLeft + bodyW, bodyTop, armW, bodyH * 0.34f, COLOR_SHIRT_SHADE, COLOR_SHIRT_SHADE);

        // --- head ---
        drawBox(canvas, headLeft, headTop, headSize, headSize, COLOR_SKIN, COLOR_SKIN_SHADE);
        // hair cap + side fringe
        drawBox(canvas, headLeft, headTop, headSize, headSize * 0.26f, COLOR_HAIR, COLOR_HAIR);
        drawBox(canvas, headLeft, headTop, headSize * 0.14f, headSize, COLOR_HAIR, COLOR_HAIR);

        if (mFace != null && !mFace.isRecycled()) {
            mSrcRect.set(0, 0, mFace.getWidth(), mFace.getHeight());
            mTemp.set(headLeft + headSize * 0.14f, headTop + headSize * 0.2f,
                    headLeft + headSize, headTop + headSize);
            canvas.drawBitmap(mFace, mSrcRect, mTemp, mFacePaint);
        } else {
            drawSteveFace(canvas, headLeft + headSize * 0.14f, headTop + headSize * 0.26f,
                    headSize * 0.86f, headSize * 0.74f, unit);
        }

        // --- soft contact shadow ---
        mFill.setColor(0x59000000);
        mTemp.set(bodyLeft - unit, legsTop + bodyH - unit * 0.4f,
                bodyLeft + bodyW + unit, legsTop + bodyH + unit * 0.9f);
        canvas.drawOval(mTemp, mFill);
    }

    private void drawBox(Canvas canvas, float left, float top, float width, float height,
                         int color, int shadeColor) {
        if (width <= 0 || height <= 0) return;
        mFill.setColor(color);
        canvas.drawRect(left, top, left + width, top + height, mFill);
        // Right-hand shading for a subtle 3D read
        mFill.setColor(shadeColor);
        canvas.drawRect(left + width * 0.72f, top, left + width, top + height, mFill);
        // Hairline edge
        mFill.setColor(0x1A000000);
        canvas.drawRect(left, top, left + width, top + Math.max(1f, height * 0.02f), mFill);
    }

    private void drawSteveFace(Canvas canvas, float left, float top, float width, float height, float unit) {
        if (width <= 0 || height <= 0) return;
        mFill.setColor(COLOR_HAIR);
        canvas.drawRect(left, top, left + width, top + height * 0.16f, mFill);

        float eyeSize = Math.max(1.4f, width * 0.22f);
        float eyeY = top + height * 0.34f;
        float eyeGap = width * 0.16f;

        mFill.setColor(0xFFF2F7FF);
        canvas.drawRect(left + eyeGap, eyeY, left + eyeGap + eyeSize, eyeY + eyeSize * 0.8f, mFill);
        canvas.drawRect(left + width - eyeGap - eyeSize, eyeY,
                left + width - eyeGap, eyeY + eyeSize * 0.8f, mFill);

        mFill.setColor(0xFF3B2A6B);
        canvas.drawRect(left + eyeGap + eyeSize * 0.55f, eyeY,
                left + eyeGap + eyeSize, eyeY + eyeSize * 0.8f, mFill);
        canvas.drawRect(left + width - eyeGap - eyeSize, eyeY,
                left + width - eyeGap - eyeSize * 0.45f, eyeY + eyeSize * 0.8f, mFill);

        mFill.setColor(0xFF8A5A44);
        canvas.drawRect(left + width * 0.36f, top + height * 0.72f,
                left + width * 0.64f, top + height * 0.78f, mFill);
    }
}
