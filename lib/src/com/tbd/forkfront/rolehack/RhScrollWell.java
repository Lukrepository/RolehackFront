package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * One key's slot that scrolls sideways, with a second key waiting past its
 * right edge.
 *
 * gurrhack's command panels scrolled past their borders, and Lucas kept an
 * `s300` button just out of sight until he scrolled for it (2026-09-24).  A key
 * that commits hundreds of turns is safest when it is not on screen to be
 * tapped by accident, and cheapest when reaching it is one drag rather than a
 * menu.  This is that: the visible key works as it always did, a swipe from
 * its right-hand side toward the screen's edge brings the hidden one in from
 * the right, and after a few seconds, or once it fires, it scrolls away again.
 * The first build swiped upward; Lucas wanted it sideways, the way the panels
 * ran.
 *
 * A drag is claimed only once the finger has moved sideways past the touch
 * slop, so a tap or a hold still reaches the key underneath untouched.
 */
public class RhScrollWell extends FrameLayout
{
	private static final int HIDE_AFTER_MS = 4000;

	private final LinearLayout mStrip;
	private final float mSlop;
	private final float mGap;
	private final Handler mHandler = new Handler();
	private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
	private float mDownX, mDownY, mStartTrans;
	private boolean mDragging;
	private boolean mPinned;
	private final Runnable mHide = new Runnable()
	{
		@Override
		public void run()
		{
			if(!mPinned)
				scrollTo(false);
		}
	};

	public RhScrollWell(Context context, View shown, View hidden, int keyW, int gap)
	{
		super(context);
		mGap = gap;
		mSlop = RhTheme.dp(context, 8f);
		setClipChildren(true);
		setWillNotDraw(false);

		mStrip = new LinearLayout(context);
		mStrip.setOrientation(LinearLayout.HORIZONTAL);
		mStrip.setClipChildren(false);
		mStrip.addView(shown, new LinearLayout.LayoutParams(keyW, LayoutParams.MATCH_PARENT));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(keyW, LayoutParams.MATCH_PARENT);
		lp.leftMargin = gap;
		mStrip.addView(hidden, lp);
		addView(mStrip, new LayoutParams(keyW * 2 + gap, LayoutParams.MATCH_PARENT));
	}

	/** Holds the hidden key in view while its own chip row is open. */
	public void setPinned(boolean pinned)
	{
		mPinned = pinned;
		if(!pinned && isRevealed())
			hideLater();
	}

	public boolean isRevealed()
	{
		return mStrip.getTranslationX() < -travel() / 2f;
	}

	private float travel()
	{
		return getWidth() + mGap;
	}

	/** Scrolls the hidden key into view, or back out of it. */
	public void scrollTo(boolean revealed)
	{
		mHandler.removeCallbacks(mHide);
		mStrip.animate().translationX(revealed ? -travel() : 0f).setDuration(140).start();
		if(revealed)
			hideLater();
		invalidate();
	}

	private void hideLater()
	{
		mHandler.removeCallbacks(mHide);
		mHandler.postDelayed(mHide, HIDE_AFTER_MS);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e)
	{
		switch(e.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				mDownX = e.getX();
				mDownY = e.getY();
				mStartTrans = mStrip.getTranslationX();
				mDragging = false;
				return false;
			case MotionEvent.ACTION_MOVE:
			{
				float dx = Math.abs(e.getX() - mDownX);
				if(!mDragging && dx > mSlop && dx > Math.abs(e.getY() - mDownY))
				{
					// The key under the finger gets a cancel: no tap, no hold.
					mDragging = true;
					mHandler.removeCallbacks(mHide);
					return true;
				}
				return false;
			}
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		switch(e.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_MOVE:
			{
				float t = mStartTrans + (e.getX() - mDownX);
				mStrip.setTranslationX(Math.max(-travel(), Math.min(0f, t)));
				invalidate();
				return true;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				mDragging = false;
				scrollTo(isRevealed());
				return true;
		}
		return true;
	}

	/** Two dots side by side at the right edge say there is more in the slot, and which is showing. */
	@Override
	protected void dispatchDraw(Canvas canvas)
	{
		// The overlay does not clip its children, and clipChildren here only clips
		// the strip to the strip -- so the slot clips itself, or the hidden key
		// shows beside it.
		canvas.save();
		canvas.clipRect(0, 0, getWidth(), getHeight());
		super.dispatchDraw(canvas);
		canvas.restore();
		float r = RhTheme.dp(getContext(), 1.8f);
		float step = RhTheme.dp(getContext(), 6f);
		float x = getWidth() - RhTheme.dp(getContext(), 6f) - step;
		float y = getHeight() / 2f;
		float frac = travel() > 0 ? -mStrip.getTranslationX() / travel() : 0f;
		for(int i = 0; i < 2; i++)
		{
			boolean on = (i == 0) ? frac < 0.5f : frac >= 0.5f;
			mDot.setColor(on ? 0xe6ffffff : 0x59ffffff);
			canvas.drawCircle(x + i * step, y, r, mDot);
		}
	}
}
