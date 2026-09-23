package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * The key flash.
 *
 * Every command press flashes its raw NetHack key just above the finger, so the
 * player learns the keyboard by playing.  This is what lets the interface show
 * words instead of keys without hiding the game underneath, and it is why the
 * whole design can afford English labels.
 *
 * Timing is the handoff's keyframe list, run off the view's own frame clock so
 * it behaves identically on every API level:
 *
 *   0%    opacity 0,  translateY(-2)   scale(.86)
 *   20%   opacity 1,  translateY(-16)  scale(1)
 *   72%   opacity 1,  translateY(-16)
 *   100%  opacity 0,  translateY(-26)
 */
public class RhFlash extends View
{
	public static final int DURATION_MS = 900;

	private final Paint mBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText   = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final RectF mRect   = new RectF();

	private String mKey;
	private long mStart;
	private final float mBorderW;
	private final float mRadius;
	private final float mPadH;
	private final float mPadV;

	public RhFlash(Context context)
	{
		super(context);
		mBorderW = RhTheme.dp(context, 2f);
		mRadius  = RhTheme.dp(context, 3f);
		mPadH    = RhTheme.dp(context, 7f);
		mPadV    = RhTheme.dp(context, 4f);

		mBg.setColor(RhTheme.DEVICE_BG);
		mBorder.setColor(RhTheme.FACE_BORDER);
		mBorder.setStyle(Paint.Style.STROKE);
		mBorder.setStrokeWidth(mBorderW);

		mText.setTypeface(RhTheme.monoBold(context));
		mText.setTextSize(RhTheme.dp(context, 14f));
		mText.setTextAlign(Paint.Align.CENTER);
		mText.setColor(RhTheme.RAW_KEY);

		setVisibility(GONE);
	}

	/** Measured width of the pill for the given key, so the caller can centre it. */
	public float pillWidth(String key)
	{
		return mText.measureText(key) + 2 * mPadH + 2 * mBorderW;
	}

	public float pillHeight()
	{
		Paint.FontMetrics fm = mText.getFontMetrics();
		return (fm.descent - fm.ascent) + 2 * mPadV + 2 * mBorderW;
	}

	public void flash(String key)
	{
		mKey = key;
		mStart = SystemClock.uptimeMillis();
		setVisibility(VISIBLE);
		invalidate();
	}

	public void cancel()
	{
		mKey = null;
		setVisibility(GONE);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		if(mKey == null)
			return;

		float t = (SystemClock.uptimeMillis() - mStart) / (float)DURATION_MS;
		if(t >= 1f)
		{
			// Only this flash's own frame loop can end it; a newer flash resets
			// mStart, so a stale timer can never clear a fresher key.
			cancel();
			return;
		}

		float alpha, dy, scale;
		if(t < 0.20f)
		{
			float k = t / 0.20f;
			alpha = k;
			dy    = -2f + k * (-16f + 2f);
			scale = 0.86f + k * (1f - 0.86f);
		}
		else if(t < 0.72f)
		{
			alpha = 1f;
			dy    = -16f;
			scale = 1f;
		}
		else
		{
			float k = (t - 0.72f) / 0.28f;
			alpha = 1f - k;
			dy    = -16f + k * (-26f + 16f);
			scale = 1f;
		}

		int a = Math.round(255 * RhTheme.clamp(alpha, 0f, 1f));
		float cx = getWidth() / 2f;
		float cy = getHeight() / 2f;

		canvas.save();
		canvas.translate(0, RhTheme.dp(getContext(), dy));
		canvas.scale(scale, scale, cx, cy);

		float w = pillWidth(mKey) - 2 * mBorderW;
		float h = pillHeight() - 2 * mBorderW;
		mRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);

		mBg.setAlpha(a);
		mBorder.setAlpha(a);
		mText.setAlpha(a);

		canvas.drawRoundRect(mRect, mRadius, mRadius, mBg);
		canvas.drawRoundRect(mRect, mRadius, mRadius, mBorder);

		Paint.FontMetrics fm = mText.getFontMetrics();
		canvas.drawText(mKey, cx, cy - (fm.ascent + fm.descent) / 2f, mText);
		canvas.restore();

		invalidate();
	}
}
