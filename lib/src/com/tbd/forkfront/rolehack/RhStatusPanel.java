package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The top-left status panel: two rows in one info panel, under the header's name
 * and role line and above the PRAY column.
 *
 * ```
 * row 1   HP label · 70 x 8 square-cornered bar · 18(18) · PW label · 2(2)
 * row 2   AC6 Xp1/0 Dlvl1 $0 T1 · hunger badge
 * ```
 *
 * HP keeps a bar and PW does not: HP is the one value read peripherally
 * mid-fight without parsing digits, and beside its own "2(2)" a power bar was
 * redundant.  The panel's width is load-bearing too -- the message line's left
 * edge sits at x 250, so a second bar would push into it.
 */
public class RhStatusPanel extends View
{
	private static final float PAD_H = 9f;
	private static final float PAD_V = 6f;
	private static final float ROW_GAP = 5f;
	private static final float BAR_W = 70f;
	private static final float BAR_H = 8f;
	private static final float RADIUS = 3f;
	/** Beyond this the row is stealing width from the message line; the rest roll into "+N". */
	private static final int MAX_CONDITIONS = 4;

	private final Paint mBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mBar    = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText   = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final RectF mRect   = new RectF();

	private RhStatus mStatus;

	public RhStatusPanel(Context context)
	{
		super(context);
		mBg.setColor(RhTheme.INFO_PANEL_BG);
		mBorder.setColor(RhTheme.INFO_PANEL_BORDER);
		mBorder.setStyle(Paint.Style.STROKE);
		mBorder.setStrokeWidth(RhTheme.dp(context, 1f));
		mText.setTypeface(RhTheme.monoBold(context));
		mText.setTextAlign(Paint.Align.LEFT);
	}

	public void setStatus(RhStatus status)
	{
		mStatus = status;
		requestLayout();
		invalidate();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onMeasure(int widthSpec, int heightSpec)
	{
		float rowH = Math.max(RhTheme.dp(getContext(), 11f), RhTheme.dp(getContext(), BAR_H));
		int h = Math.round(2 * RhTheme.dp(getContext(), PAD_V)
		                   + 2 * rowH + RhTheme.dp(getContext(), ROW_GAP));
		int w = Math.round(measureContentWidth() + 2 * RhTheme.dp(getContext(), PAD_H));
		setMeasuredDimension(w, h);
	}

	private float measureContentWidth()
	{
		// Row 1 is the wider of the two in practice, but measure both and take the
		// larger so a long Dlvl or a big gold pile cannot clip.
		float row1 = RhTheme.dp(getContext(), BAR_W) + RhTheme.dp(getContext(), 34f);
		mText.setTextSize(RhTheme.dp(getContext(), 11f));
		row1 += mText.measureText(hpText()) + mText.measureText(pwText())
		        + RhTheme.dp(getContext(), 22f);

		mText.setTextSize(RhTheme.dp(getContext(), 10.5f));
		float row2 = mText.measureText(row2Text()) + RhTheme.dp(getContext(), 8f);
		// badges hang below the panel now -- see RhBadges
		return Math.max(row1, row2);
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDraw(Canvas canvas)
	{
		if(mStatus == null || !mStatus.isPopulated())
			return;

		float r = RhTheme.dp(getContext(), RADIUS);
		float bw = mBorder.getStrokeWidth();
		mRect.set(bw / 2f, bw / 2f, getWidth() - bw / 2f, getHeight() - bw / 2f);
		canvas.drawRoundRect(mRect, r, r, mBg);
		canvas.drawRoundRect(mRect, r, r, mBorder);

		float padH = RhTheme.dp(getContext(), PAD_H);
		float padV = RhTheme.dp(getContext(), PAD_V);
		float rowH = (getHeight() - 2 * padV - RhTheme.dp(getContext(), ROW_GAP)) / 2f;

		drawRow1(canvas, padH, padV, rowH);
		drawRow2(canvas, padH, padV + rowH + RhTheme.dp(getContext(), ROW_GAP), rowH);
	}

	private void drawRow1(Canvas canvas, float x, float top, float rowH)
	{
		float cy = top + rowH / 2f;

		x = label(canvas, "HP", x, cy);
		x += RhTheme.dp(getContext(), 5f);

		// Square corners on the trough, per the tokens -- the one place in the
		// interface that does not round.
		float barW = RhTheme.dp(getContext(), BAR_W);
		float barH = RhTheme.dp(getContext(), BAR_H);
		float barTop = cy - barH / 2f;

		mBar.setColor(RhTheme.BAR_TROUGH_BG);
		canvas.drawRect(x, barTop, x + barW, barTop + barH, mBar);
		mBar.setColor(RhTheme.HP_FILL);
		canvas.drawRect(x, barTop, x + barW * mStatus.hpFraction(), barTop + barH, mBar);
		mBar.setColor(RhTheme.BAR_TROUGH_RIM);
		mBar.setStyle(Paint.Style.STROKE);
		mBar.setStrokeWidth(RhTheme.dp(getContext(), 1f));
		canvas.drawRect(x, barTop, x + barW, barTop + barH, mBar);
		mBar.setStyle(Paint.Style.FILL);

		x += barW + RhTheme.dp(getContext(), 8f);
		x = value(canvas, hpText(), x, cy);
		x += RhTheme.dp(getContext(), 9f);
		x = label(canvas, "PW", x, cy);
		x += RhTheme.dp(getContext(), 5f);
		value(canvas, pwText(), x, cy);
	}

	private void drawRow2(Canvas canvas, float x, float top, float rowH)
	{
		float cy = top + rowH / 2f;

		mText.setTypeface(RhTheme.monoRegular(getContext()));
		mText.setTextSize(RhTheme.dp(getContext(), 10.5f));
		mText.setColor(RhTheme.VALUE_SECONDARY);
		String run = row2Text();
		canvas.drawText(run, x, baseline(cy), mText);
		x += mText.measureText(run);

		// badges hang below the panel now -- see RhBadges
	}

	/**
	 * Hunger and the active conditions, as badges.
	 *
	 * One pass serves both measuring and drawing, so the panel's width can never
	 * disagree with what it actually paints -- pass a null canvas to measure.
	 *
	 * Conditions come worst-first from the core's bitmask, and the row is capped:
	 * a player who is simultaneously stoning, slimed and strangled needs to see
	 * the first of those, not a panel that has grown across the message line.
	 */
	private float badgeRun(Canvas canvas, float x, float cy)
	{
		if(mStatus == null)
			return 0f;

		float startX = x;
		float gap = RhTheme.dp(getContext(), 5f);
		float padX = RhTheme.dp(getContext(), 6f);
		float h = RhTheme.dp(getContext(), 14f);
		float total = 0f;

		mText.setTypeface(RhTheme.monoBold(getContext()));
		mText.setTextSize(RhTheme.dp(getContext(), 8.5f));

		String hunger = hungerText();
		if(hunger.length() > 0)
			total += drawBadge(canvas, startX + total, cy, hunger,
			                   RhTheme.BADGE_BG, RhTheme.BADGE_TEXT, gap, padX, h);

		java.util.List<RhStatus.Condition> conditions = mStatus.activeConditions();
		int shown = 0;
		for(RhStatus.Condition c : conditions)
		{
			if(shown++ >= MAX_CONDITIONS)
				break;
			int bg, fg;
			if(c.severity == RhStatus.SEVERITY_DEADLY)
			{
				bg = RhTheme.COND_DEADLY_BG;
				fg = RhTheme.COND_DEADLY_TEXT;
			}
			else if(c.severity == RhStatus.SEVERITY_IMPAIR)
			{
				bg = RhTheme.COND_IMPAIR_BG;
				fg = RhTheme.COND_IMPAIR_TEXT;
			}
			else
			{
				bg = RhTheme.COND_MOVE_BG;
				fg = RhTheme.COND_MOVE_TEXT;
			}
			total += drawBadge(canvas, startX + total, cy, c.name.toUpperCase(),
			                   bg, fg, gap, padX, h);
		}

		int hidden = conditions.size() - shown;
		if(hidden > 0)
			total += drawBadge(canvas, startX + total, cy, "+" + hidden,
			                   RhTheme.COND_DEADLY_BG, RhTheme.COND_DEADLY_TEXT, gap, padX, h);

		return total;
	}

	/** Returns the width consumed, including its leading gap. */
	private float drawBadge(Canvas canvas, float x, float cy, String text,
	                        int bg, int fg, float gap, float padX, float h)
	{
		float w = mText.measureText(text) + 2 * padX;
		if(canvas != null)
		{
			mBar.setColor(bg);
			canvas.drawRect(x + gap, cy - h / 2f, x + gap + w, cy + h / 2f, mBar);
			mText.setColor(fg);
			canvas.drawText(text, x + gap + padX, baseline(cy), mText);
		}
		return gap + w;
	}

	// ____________________________________________________________________________________
	private float label(Canvas canvas, String s, float x, float cy)
	{
		mText.setTypeface(RhTheme.monoBold(getContext()));
		mText.setTextSize(RhTheme.dp(getContext(), 9f));
		mText.setColor(RhTheme.LABEL_MUTED);
		canvas.drawText(s, x, baseline(cy), mText);
		return x + mText.measureText(s);
	}

	private float value(Canvas canvas, String s, float x, float cy)
	{
		mText.setTypeface(RhTheme.monoBold(getContext()));
		mText.setTextSize(RhTheme.dp(getContext(), 11f));
		mText.setColor(RhTheme.TEXT);
		canvas.drawText(s, x, baseline(cy), mText);
		return x + mText.measureText(s);
	}

	private float baseline(float cy)
	{
		Paint.FontMetrics fm = mText.getFontMetrics();
		return cy - (fm.ascent + fm.descent) / 2f;
	}

	// ____________________________________________________________________________________
	private String hpText()
	{
		if(mStatus == null)
			return "";
		return text(RhStatus.BL_HP) + "(" + text(RhStatus.BL_HPMAX) + ")";
	}

	private String pwText()
	{
		if(mStatus == null)
			return "";
		return text(RhStatus.BL_ENE) + "(" + text(RhStatus.BL_ENEMAX) + ")";
	}

	/**
	 * AC, experience, depth, gold and time, in NetHack's own botl order.  The
	 * labels are ours; the core's own are stripped, or every one would double up.
	 */
	private String row2Text()
	{
		if(mStatus == null)
			return "";
		StringBuilder sb = new StringBuilder();
		sb.append("AC").append(text(RhStatus.BL_AC));

		sb.append("  Xp").append(text(RhStatus.BL_XP));
		String exp = mStatus.bare(RhStatus.BL_EXP);
		if(exp != null && exp.length() > 0)
			sb.append('/').append(exp);

		String lvl = mStatus.bare(RhStatus.BL_LEVELDESC);
		if(lvl != null && lvl.length() > 0)
			sb.append("  Dlvl").append(lvl);

		String gold = mStatus.bare(RhStatus.BL_GOLD);
		if(gold != null && gold.length() > 0)
			sb.append("  $").append(gold);

		sb.append("  T").append(text(RhStatus.BL_TIME));
		return sb.toString();
	}

	/** The hunger badge, and the encumbrance word when there is no hunger state. */
	private String hungerText()
	{
		if(mStatus == null)
			return "";
		String h = mStatus.value(RhStatus.BL_HUNGER);
		if(h != null && h.trim().length() > 0)
			return h.trim().toUpperCase();
		String cap = mStatus.value(RhStatus.BL_CAP);
		return cap == null ? "" : cap.trim().toUpperCase();
	}

	private String text(int idx)
	{
		return mStatus.bareOr(idx, "?");
	}
}
