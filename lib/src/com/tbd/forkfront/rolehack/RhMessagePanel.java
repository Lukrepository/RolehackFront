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
	}

	public void setMessage(String message)
	{
		String m = message == null ? "" : message.trim();
		if(m.equals(mMessage))
			return;
		mMessage = m;
		mLayout = null;
		requestLayout();
		invalidate();
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
			h = mLayout.getLineTop(lines) + Math.round(2 * RhTheme.dp(getContext(), PAD_V));
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
		                getHeight() - 2 * RhTheme.dp(getContext(), PAD_V));
		mLayout.draw(canvas);
		canvas.restore();
	}
}
