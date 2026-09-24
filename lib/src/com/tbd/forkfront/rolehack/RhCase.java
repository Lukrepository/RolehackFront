package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * The terminal style's case: a putty body, three dark key wells, and a screen
 * hood with a hole where the glass is.  Source of record: the "Rolehack
 * Terminal Mode" design canvas (2026-09-23).
 *
 * It sits at the bottom of the overlay, above the map.  The map shows only
 * through the glass, and the case swallows every touch that lands anywhere
 * else -- which answers the complaint that started the terminal style: a tap
 * that just missed a control used to land on the map and send the hero
 * travelling to the far side of the level (Lucas, 2026-09-23).
 *
 * The hood's lip carries three mode lamps.  Keycaps do not change colour, so
 * the states that used to recolour the numpad -- search mode amber, an armed
 * direction red -- light a lamp instead, and MORE lights while earlier
 * messages from this turn have scrolled away.
 */
public class RhCase extends View
{
	// Frame geometry, design dp.  RhOverlay places the keys inside these wells and
	// tells the map where the glass is, so both read the same numbers.
	public static final float MARGIN    = 8f;
	public static final float WELL_PAD  = 10f;
	public static final float DECK_H    = 66f;
	public static final float DECK_KEY  = 52f;
	public static final float HOOD_TOP  = 14f;
	public static final float HOOD_SIDE = 16f;
	public static final float LIP       = 26f;
	public static final float GLASS_R   = 20f;
	private static final float HOOD_R   = 12f;
	private static final float WELL_R   = 8f;

	/**
	 * Portrait (2026-09-24): width is short, so the hood's moulding round the
	 * glass is thinner than landscape's HOOD_SIDE and HOOD_TOP.
	 */
	public static final float P_HOOD_SIDE = 8f;
	public static final float P_HOOD_TOP  = 10f;

	/** Caseless: the lamps' strip across the top, above the message band. */
	public static final float LAMP_STRIP = 22f;

	/**
	 * The glass's inset from the left and right edges, for a bank this wide.
	 * Caseless there is no hood, so the "glass" -- where the message and status
	 * bands go -- runs right up to the wells.
	 */
	public static float glassSide(float bankDp)
	{
		return MARGIN + bankDp + MARGIN + (RhTheme.caseless() ? 0f : HOOD_SIDE);
	}

	public static float glassTop()
	{
		return MARGIN + (RhTheme.caseless() ? LAMP_STRIP : HOOD_TOP);
	}

	public static float glassBottom()
	{
		return MARGIN + DECK_H + MARGIN + (RhTheme.caseless() ? 0f : LIP);
	}

	/** Portrait: the glass runs the screen's width, inside the hood. */
	public static float portraitGlassSide()
	{
		return MARGIN + (RhTheme.caseless() ? 0f : P_HOOD_SIDE);
	}

	public static float portraitGlassTop()
	{
		return MARGIN + (RhTheme.caseless() ? LAMP_STRIP : P_HOOD_TOP);
	}

	/** Portrait: the glass stands on the controls, which rise this far from the bottom edge. */
	public static float portraitGlassBottom(float controlsDp)
	{
		return controlsDp + (RhTheme.caseless() ? 0f : LIP);
	}

	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final Path mPath = new Path();
	private final RectF mLeft = new RectF(), mRight = new RectF(), mDeck = new RectF();
	private final RectF mHood = new RectF(), mGlass = new RectF(), mRect = new RectF();

	private final float mBankDp;
	private final boolean mPortrait;
	private final float mWellHDp, mFnHDp;
	private boolean mSearch, mArmed, mMore;

	public RhCase(Context context, float bankDp)
	{
		this(context, bankDp, false, 0f, 0f);
	}

	/**
	 * Portrait (Lucas, 2026-09-24): the glass across the top, the key row under
	 * it in the deck's well, and the two banks side by side along the bottom --
	 * see RhOverlay's P_ constants.  wellHDp is the banks' height, fnHDp the key
	 * row's.
	 */
	public RhCase(Context context, float bankDp, boolean portrait, float wellHDp, float fnHDp)
	{
		super(context);
		mBankDp = bankDp;
		mPortrait = portrait;
		mWellHDp = wellHDp;
		mFnHDp = fnHDp;
		mText.setTypeface(RhTheme.capFont(context));
	}

	public void setLamps(boolean search, boolean armed, boolean more)
	{
		if(search == mSearch && armed == mArmed && more == mMore)
			return;
		mSearch = search;
		mArmed = armed;
		mMore = more;
		invalidate();
	}

	private float dp(float v) { return RhTheme.dp(getContext(), v); }

	/**
	 * Fill with a gradient at full strength.  A paint's alpha scales any shader
	 * drawn with it, so a translucent colour left from the last line would fade
	 * the next well or the hood.
	 */
	private void shade(Shader s)
	{
		mPaint.setColor(0xff000000);
		mPaint.setShader(s);
	}

	private void layoutRects()
	{
		float w = getWidth(), h = getHeight();
		float m = dp(MARGIN), bank = dp(mBankDp), deck = dp(DECK_H);
		if(mPortrait)
		{
			float well = dp(mWellHDp), fn = dp(mFnHDp);
			mLeft.set(m, h - m - well, m + bank, h - m);
			mRight.set(w - m - bank, h - m - well, w - m, h - m);
			mDeck.set(m, mLeft.top - m - fn, w - m, mLeft.top - m);
			mHood.set(m, m, w - m, mDeck.top - m);
			mGlass.set(mHood.left + dp(P_HOOD_SIDE), mHood.top + dp(P_HOOD_TOP),
					   mHood.right - dp(P_HOOD_SIDE), mHood.bottom - dp(LIP));
			return;
		}
		mLeft.set(m, m, m + bank, h - m);
		mRight.set(w - m - bank, m, w - m, h - m);
		mDeck.set(m + bank + m, h - m - deck, w - m - bank - m, h - m);
		mHood.set(mDeck.left, m, mDeck.right, mDeck.top - m);
		mGlass.set(mHood.left + dp(HOOD_SIDE), mHood.top + dp(HOOD_TOP),
		           mHood.right - dp(HOOD_SIDE), mHood.bottom - dp(LIP));
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDraw(Canvas canvas)
	{
		layoutRects();
		if(RhTheme.caseless())
		{
			drawCaseless(canvas);
			return;
		}
		float w = getWidth(), h = getHeight();
		float gr = dp(GLASS_R);

		// The body, with the glass cut out of it.
		mPath.reset();
		mPath.setFillType(Path.FillType.EVEN_ODD);
		mPath.addRect(0f, 0f, w, h, Path.Direction.CW);
		mPath.addRoundRect(mGlass, gr, gr, Path.Direction.CW);
		shade(new LinearGradient(0f, 0f, 0f, h, RhTheme.caseBg()[0], RhTheme.caseBg()[1],
		                                    Shader.TileMode.CLAMP));
		canvas.drawPath(mPath, mPaint);
		mPaint.setShader(null);

		drawWell(canvas, mLeft);
		drawWell(canvas, mRight);
		drawWell(canvas, mDeck);

		// The hood: dark moulding around the tube, lit along its top edge.
		float hr = dp(HOOD_R);
		mPath.reset();
		mPath.setFillType(Path.FillType.EVEN_ODD);
		mPath.addRoundRect(mHood, hr, hr, Path.Direction.CW);
		mPath.addRoundRect(mGlass, gr, gr, Path.Direction.CW);
		shade(new LinearGradient(0f, mHood.top, 0f, mHood.bottom,
		                                    RhTheme.hoodBg()[0], RhTheme.hoodBg()[1], Shader.TileMode.CLAMP));
		canvas.drawPath(mPath, mPaint);
		mPaint.setShader(null);
		mPaint.setColor(0x24ffffff);
		canvas.drawRect(mHood.left + hr, mHood.top, mHood.right - hr, mHood.top + dp(1f), mPaint);

		// The tube's bezel ring.
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(dp(3f));
		mPaint.setColor(0xff110e0c);
		mRect.set(mGlass);
		mRect.inset(-dp(1.5f), -dp(1.5f));
		canvas.drawRoundRect(mRect, gr + dp(1.5f), gr + dp(1.5f), mPaint);
		mPaint.setStrokeWidth(dp(1f));
		mPaint.setColor(0x12ffffff);
		mRect.inset(-dp(2f), -dp(2f));
		canvas.drawRoundRect(mRect, gr + dp(3.5f), gr + dp(3.5f), mPaint);
		mPaint.setStyle(Paint.Style.FILL);

		drawLip(canvas);
	}

	/**
	 * Caseless: no body and no hood, only the wells, translucent in the skin's
	 * well colour so the map reads through them, and the lamps on a pill at the
	 * top centre.
	 */
	private void drawCaseless(Canvas canvas)
	{
		float wr = dp(WELL_R);
		int well = (RhTheme.wellBg()[1] & 0x00ffffff) | 0xa6000000;
		for(RectF r : new RectF[] { mLeft, mRight, mDeck })
		{
			mPaint.setShader(null);
			mPaint.setStyle(Paint.Style.FILL);
			mPaint.setColor(well);
			canvas.drawRoundRect(r, wr, wr, mPaint);
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeWidth(dp(1f));
			mPaint.setColor(0x33ffffff);
			canvas.drawRoundRect(r, wr, wr, mPaint);
		}
		mPaint.setStyle(Paint.Style.FILL);

		// Measure the three lamps first, so the pill can be centred on them.
		mText.setTextSize(dp(8f));
		mText.setTextAlign(Paint.Align.LEFT);
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.12f);
		float lamp = dp(3.5f) * 2 + dp(5f) + dp(14f);
		float total = 3 * lamp + mText.measureText("SEARCH") + mText.measureText("ARMED")
				+ mText.measureText("MORE") - dp(14f);
		float cy = dp(MARGIN) + dp(LAMP_STRIP) / 2f - dp(2f);
		float x = getWidth() / 2f - total / 2f;
		mRect.set(x - dp(10f), cy - dp(8f), x + total + dp(10f), cy + dp(8f));
		mPaint.setColor(0xb3070605);
		canvas.drawRoundRect(mRect, dp(8f), dp(8f), mPaint);
		x = drawLamp(canvas, x, cy, "SEARCH", mSearch, RhTheme.LAMP_AMBER, RhTheme.LAMP_AMBER_OFF);
		x = drawLamp(canvas, x, cy, "ARMED",  mArmed,  RhTheme.LAMP_RED,   RhTheme.LAMP_RED_OFF);
		drawLamp(canvas, x, cy, "MORE", mMore, RhTheme.LAMP_GREEN, RhTheme.LAMP_GREEN_OFF);
	}

	private void drawWell(Canvas canvas, RectF r)
	{
		float wr = dp(WELL_R);
		shade(new LinearGradient(0f, r.top, 0f, r.bottom,
		                                    RhTheme.wellBg()[0], RhTheme.wellBg()[1], Shader.TileMode.CLAMP));
		canvas.drawRoundRect(r, wr, wr, mPaint);
		// The inset shadow along the top edge: the case overhangs the well.
		shade(new LinearGradient(0f, r.top, 0f, r.top + dp(7f),
		                                    RhTheme.wellShade(), 0x00000000, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(r, wr, wr, mPaint);
		mPaint.setShader(null);
		// The case's lit edge just below the well.
		mPaint.setColor(0x59ffffff);
		canvas.drawRect(r.left + wr, r.bottom, r.right - wr, r.bottom + dp(1f), mPaint);
	}

	private void drawLip(Canvas canvas)
	{
		float mid = (mGlass.bottom + mHood.bottom) / 2f + dp(1f);
		float x = mHood.left + dp(22f);
		mText.setTextSize(dp(8f));
		mText.setTextAlign(Paint.Align.LEFT);
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.12f);
		x = drawLamp(canvas, x, mid, "SEARCH", mSearch, RhTheme.LAMP_AMBER, RhTheme.LAMP_AMBER_OFF);
		x = drawLamp(canvas, x, mid, "ARMED",  mArmed,  RhTheme.LAMP_RED,   RhTheme.LAMP_RED_OFF);
		drawLamp(canvas, x, mid, "MORE", mMore, RhTheme.LAMP_GREEN, RhTheme.LAMP_GREEN_OFF);

		// The nameplate, right-aligned on the lip.
		mText.setTextSize(dp(9f));
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.22f);
		float nameW = mText.measureText("ROLEHACK");
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.06f);
		float modelW = mText.measureText("RH-5");
		float padH = dp(8f), gap = dp(6f);
		float plateW = padH + nameW + gap + modelW + padH;
		float plateH = dp(15f);
		mRect.set(mHood.right - dp(22f) - plateW, mid - plateH / 2f, mHood.right - dp(22f), mid + plateH / 2f);
		shade(new LinearGradient(0f, mRect.top, 0f, mRect.bottom, 0xff2b2a28, 0xff0e0d0c,
		                                    Shader.TileMode.CLAMP));
		canvas.drawRoundRect(mRect, dp(3f), dp(3f), mPaint);
		mPaint.setShader(null);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(dp(1f));
		mPaint.setColor(0xff8d8a80);
		canvas.drawRoundRect(mRect, dp(3f), dp(3f), mPaint);
		mPaint.setStyle(Paint.Style.FILL);

		Paint.FontMetrics fm = mText.getFontMetrics();
		float baseline = mid - (fm.ascent + fm.descent) / 2f;
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.22f);
		mText.setColor(0xffebe4d3);
		canvas.drawText("ROLEHACK", mRect.left + padH, baseline, mText);
		if(android.os.Build.VERSION.SDK_INT >= 21)
			mText.setLetterSpacing(0.06f);
		mText.setColor(0xffe8763d);
		canvas.drawText("RH-5", mRect.left + padH + nameW + gap, baseline, mText);
	}

	/** One lamp and its label; returns the x where the next one starts. */
	private float drawLamp(Canvas canvas, float x, float cy, String label, boolean on, int lit, int unlit)
	{
		float r = dp(3.5f);
		float cx = x + r;
		if(on)
		{
			shade(new RadialGradient(cx, cy, r * 2.6f, (lit & 0x00ffffff) | 0x99000000,
			                                    lit & 0x00ffffff, Shader.TileMode.CLAMP));
			canvas.drawCircle(cx, cy, r * 2.6f, mPaint);
			mPaint.setShader(null);
		}
		mPaint.setColor(on ? lit : unlit);
		canvas.drawCircle(cx, cy, r, mPaint);

		// On the dark pill when caseless, whatever the skin's own lip colour.
		mText.setColor(RhTheme.caseless() ? 0xffd9d1bd : RhTheme.lipText());
		Paint.FontMetrics fm = mText.getFontMetrics();
		float tx = cx + r + dp(5f);
		canvas.drawText(label, tx, cy - (fm.ascent + fm.descent) / 2f, mText);
		return tx + mText.measureText(label) + dp(14f);
	}

	// ____________________________________________________________________________________
	/**
	 * Everything but the glass is case: a touch there stops here.  Caseless,
	 * only the wells are case, and everything else is map.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		layoutRects();
		float x = e.getX(), y = e.getY();
		if(RhTheme.caseless())
			return mLeft.contains(x, y) || mRight.contains(x, y) || mDeck.contains(x, y);
		return !mGlass.contains(x, y);
	}
}
