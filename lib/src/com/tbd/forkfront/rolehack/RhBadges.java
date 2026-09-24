package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Status badges as a column hanging under the status panel: hunger, encumbrance
 * and every active condition, one per line, worst first.
 *
 * They used to run along the panel's second row, which put two faults in one
 * place (Lucas, 2026-09-23).  The row ran under the centred message panel, so
 * WEAK and BURDENED were hidden by the last message; and hunger and encumbrance
 * shared one slot -- "the encumbrance word when there is no hunger state" -- so
 * a Weak, Burdened character saw only WEAK.  Hunger was also always amber, so
 * WEAK read as a caution when it is one step from fainting.
 *
 * Colour now follows severity, not source, in four tiers that mirror the
 * status highlights Lucas plays with (satiated/burdened yellow, hungry/stressed
 * orange, weak/strained red):
 *
 *   critical  red     Weak, Fainting, Strained and worse, deadly conditions
 *   serious   orange  Hungry, Stressed
 *   warning   amber   Satiated, Burdened, impairing conditions (Stun, Conf...)
 *   info      blue    Lev, Fly, Ride
 *
 * The column shows MAX_ROWS lines.  Past that the last line becomes "+N MORE";
 * tapping it lists everything for a few seconds.  Taps land on the map as usual
 * when nothing is hidden.
 */
public class RhBadges extends View
{
	private static final int MAX_ROWS = 3;
	private static final int EXPANDED_MS = 6000;

	private static final int TIER_CRITICAL = 0, TIER_SERIOUS = 1, TIER_WARNING = 2, TIER_INFO = 3;
	private static final int[] BG = { RhTheme.COND_DEADLY_BG, RhTheme.STATUS_SERIOUS_BG,
	                                  RhTheme.COND_IMPAIR_BG, RhTheme.COND_MOVE_BG };
	private static final int[] FG = { RhTheme.COND_DEADLY_TEXT, RhTheme.STATUS_SERIOUS_TEXT,
	                                  RhTheme.COND_IMPAIR_TEXT, RhTheme.COND_MOVE_TEXT };
	private static final int MORE_BG = 0xff2a302d, MORE_FG = 0xffffffff;

	/** One badge; the terminal style's status line draws the same list (RhScreen). */
	static final class Badge
	{
		final String text;
		final int tier, order;
		Badge(String text, int tier, int order) { this.text = text; this.tier = tier; this.order = order; }
	}

	static int bg(int tier) { return BG[tier]; }
	static int fg(int tier) { return FG[tier]; }

	private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF mRect = new RectF();
	private final Handler mHandler = new Handler();
	private final Runnable mCollapse = new Runnable()
	{
		@Override
		public void run()
		{
			mExpanded = false;
			requestLayout();
			invalidate();
		}
	};
	private RhStatus mStatus;
	private boolean mExpanded;

	public RhBadges(Context context)
	{
		super(context);
		mText.setTextAlign(Paint.Align.LEFT);
	}

	public void setStatus(RhStatus status)
	{
		mStatus = status;
		requestLayout();
		invalidate();
	}

	private List<Badge> badges()
	{
		return badgesFor(mStatus);
	}

	/** Hunger, encumbrance and conditions, sorted worst tier first; stable within a tier. */
	static List<Badge> badgesFor(RhStatus status)
	{
		List<Badge> out = new ArrayList<Badge>();
		if(status == null)
			return out;

		int n = 0;
		for(RhStatus.Condition c : status.activeConditions())
		{
			int tier = c.severity == RhStatus.SEVERITY_DEADLY ? TIER_CRITICAL
					: c.severity == RhStatus.SEVERITY_IMPAIR ? TIER_WARNING : TIER_INFO;
			out.add(new Badge(c.name.toUpperCase(), tier, n++));
		}

		String h = status.value(RhStatus.BL_HUNGER);
		if(h != null && h.trim().length() > 0)
		{
			String k = h.trim().toLowerCase();
			int tier = k.startsWith("weak") || k.startsWith("faint") ? TIER_CRITICAL
					: k.startsWith("hungry") ? TIER_SERIOUS : TIER_WARNING;
			out.add(new Badge(h.trim().toUpperCase(), tier, 100));
		}

		String cap = status.value(RhStatus.BL_CAP);
		if(cap != null && cap.trim().length() > 0)
		{
			String k = cap.trim().toLowerCase();
			int tier = k.startsWith("burdened") ? TIER_WARNING
					: k.startsWith("stressed") ? TIER_SERIOUS : TIER_CRITICAL;
			out.add(new Badge(cap.trim().toUpperCase(), tier, 101));
		}

		java.util.Collections.sort(out, new java.util.Comparator<Badge>()
		{
			@Override
			public int compare(Badge a, Badge b)
			{
				return a.tier != b.tier ? a.tier - b.tier : a.order - b.order;
			}
		});
		return out;
	}

	private int hiddenCount(List<Badge> all)
	{
		return mExpanded || all.size() <= MAX_ROWS ? 0 : all.size() - (MAX_ROWS - 1);
	}

	private float dp(float v) { return RhTheme.dp(getContext(), v); }

	private void prepText()
	{
		mText.setTypeface(RhTheme.monoBold(getContext()));
		mText.setTextSize(dp(9f));
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec)
	{
		List<Badge> all = badges();
		int hidden = hiddenCount(all);
		int rows = hidden > 0 ? MAX_ROWS : all.size();
		prepText();
		float w = 0f;
		for(int i = 0; i < rows; i++)
		{
			String t = hidden > 0 && i == rows - 1 ? "+" + hidden + " MORE" : all.get(i).text;
			w = Math.max(w, mText.measureText(t) + 2 * dp(7f));
		}
		float h = rows == 0 ? 0f : rows * dp(17f) + (rows - 1) * dp(3f);
		setMeasuredDimension(Math.round(w), Math.round(h));
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		List<Badge> all = badges();
		int hidden = hiddenCount(all);
		int rows = hidden > 0 ? MAX_ROWS : all.size();
		prepText();
		Paint.FontMetrics fm = mText.getFontMetrics();
		float y = 0f, rowH = dp(17f), r = dp(3f), padX = dp(7f);
		for(int i = 0; i < rows; i++)
		{
			boolean more = hidden > 0 && i == rows - 1;
			String t = more ? "+" + hidden + " MORE" : all.get(i).text;
			int tier = more ? -1 : all.get(i).tier;
			float w = mText.measureText(t) + 2 * padX;
			mRect.set(0f, y, w, y + rowH);
			mFill.setColor(more ? MORE_BG : BG[tier]);
			canvas.drawRoundRect(mRect, r, r, mFill);
			mText.setColor(more ? MORE_FG : FG[tier]);
			float baseline = y + (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			canvas.drawText(t, padX, baseline, mText);
			y += rowH + dp(3f);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		List<Badge> all = badges();
		boolean canToggle = mExpanded || all.size() > MAX_ROWS;
		if(!canToggle)
			return false;
		if(e.getActionMasked() == MotionEvent.ACTION_UP)
		{
			mExpanded = !mExpanded;
			mHandler.removeCallbacks(mCollapse);
			if(mExpanded)
				mHandler.postDelayed(mCollapse, EXPANDED_MS);
			requestLayout();
			invalidate();
		}
		return true;
	}
}
