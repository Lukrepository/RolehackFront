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
 * One key's slot that scrolls, with a second key waiting below its edge.
 *
 * gurrhack's command panels scrolled past their borders, and Lucas kept an
 * `s300` button one line below the edge, out of sight until he scrolled for it
 * (2026-09-24).  A key that commits hundreds of turns is safest when it is not
 * on screen to be tapped by accident, and cheapest when reaching it is one
 * drag rather than a menu.  This is that: the visible key works as it always
 * did, a drag upward brings the hidden one up in its place, and after a few
 * seconds, or once it fires, it scrolls away again.
 *
 * A drag is claimed only once the finger has moved past the touch slop, so a
 * tap or a hold still reaches the key underneath untouched.
 */
public class RhScrollWell extends FrameLayout
{
	private static final int HIDE_AFTER_MS = 4000;

	private final LinearLayout mStrip;
	private final float mSlop;
	private final float mGap;
	private final Handler mHandler = new Handler();
	private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
	private float mDownY, mStartTrans;
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

	public RhScrollWell(Context context, View top, View below, int keyH, int gap)
	{
		super(context);
		mGap = gap;
		mSlop = RhTheme.dp(context, 8f);
		setClipChildren(true);
		setWillNotDraw(false);

		mStrip = new LinearLayout(context);
		mStrip.setOrientation(LinearLayout.VERTICAL);
		mStrip.setClipChildren(false);
		mStrip.addView(top, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, keyH));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, keyH);
		lp.topMargin = gap;
		mStrip.addView(below, lp);
		addView(mStrip, new LayoutParams(LayoutParams.MATCH_PARENT, keyH * 2 + gap));
	}

	/** Holds the hidden key up while its own chip row is open. */
	public void setPinned(boolean pinned)
	{
		mPinned = pinned;
		if(!pinned && isRevealed())
			hideLater();
	}

	public boolean isRevealed()
	{
		return mStrip.getTranslationY() < -travel() / 2f;
	}

	private float travel()
	{
		return getHeight() + mGap;
	}

	/** Scrolls the hidden key into view, or back out of it. */
	public void scrollTo(boolean revealed)
	{
		mHandler.removeCallbacks(mHide);
		mStrip.animate().translationY(revealed ? -travel() : 0f).setDuration(140).start();
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
				mDownY = e.getY();
				mStartTrans = mStrip.getTranslationY();
				mDragging = false;
				return false;
			case MotionEvent.ACTION_MOVE:
				if(!mDragging && Math.abs(e.getY() - mDownY) > mSlop)
				{
					// The key under the finger gets a cancel: no tap, no hold.
					mDragging = true;
					mHandler.removeCallbacks(mHide);
					return true;
				}
				return false;
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
				float t = mStartTrans + (e.getY() - mDownY);
				mStrip.setTranslationY(Math.max(-travel(), Math.min(0f, t)));
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

	/** Two dots at the right edge say there is more in the slot, and which is showing. */
	@Override
	protected void dispatchDraw(Canvas canvas)
	{
		// The overlay does not clip its children, and clipChildren here only clips
		// the strip to the strip -- so the slot clips itself, or the hidden key
		// shows below it.
		canvas.save();
		canvas.clipRect(0, 0, getWidth(), getHeight());
		super.dispatchDraw(canvas);
		canvas.restore();
		float r = RhTheme.dp(getContext(), 1.8f);
		float x = getWidth() - RhTheme.dp(getContext(), 6f);
		float mid = getHeight() / 2f - RhTheme.dp(getContext(), 3f);
		float frac = travel() > 0 ? -mStrip.getTranslationY() / travel() : 0f;
		for(int i = 0; i < 2; i++)
		{
			boolean on = (i == 0) ? frac < 0.5f : frac >= 0.5f;
			mDot.setColor(on ? 0xe6ffffff : 0x59ffffff);
			canvas.drawCircle(x, mid + i * RhTheme.dp(getContext(), 6f), r, mDot);
		}
	}
}
