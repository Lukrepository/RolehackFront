package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/**
 * The message line, in the interface's own chrome.
 *
 * Carries verbatim NetHack message output in info-panel styling, centred above
 * the map. It draws above the hub layer on purpose: OFFENSE's radial and the
 * fans sweep up through this band, and the message that prompted an action must
 * not be covered by the control answering it.
 *
 * The panel keeps its declared width even when there is nothing to say, so the
 * message-history button beside it never moves. A control that wanders is worse
 * than one that is occasionally next to an empty box.
 */
public class RhMessagePanel extends View
{
	/** The handoff's max-width. Fixed rather than shrink-to-fit, so nothing shifts. */
	public static final float WIDTH = 420f;

	private static final float PAD_H = 12f;
	private static final float PAD_V = 5f;
	private static final float RADIUS = 3f;
	private static final float TEXT_SIZE = 11.5f;
	private static final float LINE_SPACING = 1.35f;
	/** Messages are capped at three lines upstream; allow a little wrap on top. */
	private static final int MAX_LINES = 4;

	private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final RectF mRect = new RectF();

	private String mMessage = "";
	private StaticLayout mLayout;
	/** Messages this turn that fell out of the lines shown; 0 for none. */
	private int mMore;
	private final TextPaint mMoreText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
	private static final float MORE_SIZE = 8.5f;
	private static final float MORE_ROW = 13f;

	public RhMessagePanel(Context context)
	{
		super(context);
		mBg.setColor(RhTheme.INFO_PANEL_BG);
		mBorder.setColor(RhTheme.INFO_PANEL_BORDER);
		mBorder.setStyle(Paint.Style.STROKE);
		mBorder.setStrokeWidth(RhTheme.dp(context, 1f));

		mText.setTypeface(RhTheme.monoRegular(context));
		mText.setTextSize(RhTheme.dp(context, TEXT_SIZE));
		mText.setColor(RhTheme.MESSAGE_TEXT);

		mMoreText.setTypeface(RhTheme.monoBold(context));
		mMoreText.setTextSize(RhTheme.dp(context, MORE_SIZE));
		mMoreText.setColor(RhTheme.RAW_KEY_DIM);
		mMoreText.setTextAlign(Paint.Align.RIGHT);
	}

	public void setMessage(String message)
	{
		setMessage(message, 0);
	}

	/**
	 * The lines to show, and how many earlier messages from the same turn did
	 * not fit.  ForkFront drew those as a separate "--N more--" line; it is
	 * carried here instead, as a tag under the text, and the panel takes a tap
	 * (opening the message history) only while there is something to see.
	 */
	public void setMessage(String message, int more)
	{
		String m = message == null ? "" : message.trim();
		int n = Math.max(0, more);
		if(m.equals(mMessage) && n == mMore)
			return;
		mMessage = m;
		mMore = n;
		mLayout = null;
		setClickable(mMore > 0 && m.length() > 0);
		requestLayout();
		invalidate();
	}

	private float moreRow()
	{
		return mMore > 0 ? RhTheme.dp(getContext(), MORE_ROW) : 0f;
	}

	private int contentWidth()
	{
		return Math.round(RhTheme.dp(getContext(), WIDTH) - 2 * RhTheme.dp(getContext(), PAD_H));
	}

	@SuppressWarnings("deprecation")
	private void buildLayout()
	{
		if(mLayout != null || mMessage.length() == 0)
			return;
		mLayout = new StaticLayout(mMessage, mText, contentWidth(),
				Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, false);
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec)
	{
		buildLayout();

		int w = Math.round(RhTheme.dp(getContext(), WIDTH));
		int h;
		if(mLayout == null)
		{
			h = 0;
		}
		else
		{
			int lines = Math.min(mLayout.getLineCount(), MAX_LINES);
			h = mLayout.getLineTop(lines) + Math.round(2 * RhTheme.dp(getContext(), PAD_V) + moreRow());
		}
		setMeasuredDimension(w, h);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		buildLayout();
		if(mLayout == null || getHeight() == 0)
			return;

		float bw = mBorder.getStrokeWidth();
		float r = RhTheme.dp(getContext(), RADIUS);
		mRect.set(bw / 2f, bw / 2f, getWidth() - bw / 2f, getHeight() - bw / 2f);
		canvas.drawRoundRect(mRect, r, r, mBg);
		canvas.drawRoundRect(mRect, r, r, mBorder);

		canvas.save();
		canvas.translate(RhTheme.dp(getContext(), PAD_H), RhTheme.dp(getContext(), PAD_V));
		// Clip rather than let a long message spill past the panel's edge.
		canvas.clipRect(0, 0, contentWidth(),
		                getHeight() - 2 * RhTheme.dp(getContext(), PAD_V) - moreRow());
		mLayout.draw(canvas);
		canvas.restore();

		if(mMore > 0)
		{
			Paint.FontMetrics fm = mMoreText.getFontMetrics();
			float rowTop = getHeight() - RhTheme.dp(getContext(), PAD_V) - moreRow();
			float baseline = rowTop + (moreRow() - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			canvas.drawText("+" + mMore + (mMore == 1 ? " EARLIER MESSAGE" : " EARLIER MESSAGES") + " \u00b7 TAP",
			                getWidth() - RhTheme.dp(getContext(), PAD_H), baseline, mMoreText);
		}
	}
}
