package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import net.kdt.pojavlaunch.R;

/**
 * Hardware-accelerated rounded / circular ImageView using ViewOutlineProvider (API 21+).
 */
public class RoundedImageView extends AppCompatImageView {
    private float mCornerRadiusPx = 0f;
    private boolean mIsCircle = false;

    public RoundedImageView(@NonNull Context context) {
        this(context, null);
    }

    public RoundedImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundedImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView, defStyleAttr, 0);
            mCornerRadiusPx = a.getDimension(R.styleable.RoundedImageView_auroraCornerRadius, 0f);
            mIsCircle = a.getBoolean(R.styleable.RoundedImageView_auroraCircle, false);
            a.recycle();
        }
        setupOutline();
    }

    private void setupOutline() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                if (mIsCircle) {
                    int size = Math.min(w, h);
                    int left = (w - size) / 2;
                    int top = (h - size) / 2;
                    outline.setOval(left, top, left + size, top + size);
                } else {
                    outline.setRoundRect(0, 0, w, h, mCornerRadiusPx);
                }
            }
        });
        setClipToOutline(mIsCircle || mCornerRadiusPx > 0f);
    }

    public void setCornerRadiusPx(float radiusPx) {
        mCornerRadiusPx = radiusPx;
        setClipToOutline(mIsCircle || mCornerRadiusPx > 0f);
        invalidateOutline();
    }

    public void setCircle(boolean circle) {
        mIsCircle = circle;
        setClipToOutline(mIsCircle || mCornerRadiusPx > 0f);
        invalidateOutline();
    }
}
