package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The terminal style's glass: the message lines along the top, the status
 * lines along the bottom, and the tube's scanlines and vignette over all of it,
 * the map included.  The map itself is ForkFront's, drawn underneath and
 * centred in the band between the two (RhOverlay.mapArea).
 *
 * The status reads the way the tty port's does -- "Dlvl:13 $:5 HP:41(41)" --
 * over three lines rather than two, because the glass is narrower than an
 * 80-column screen.  The third line keeps a fixed place for conditions, so a
 * new one never moves anything (the same rule as the context strip).  The
 * title carries an inverse-video HP bar, after tty's `hitpointbar` option.
 *
 * The status can be cut to two lines, leaving off the attributes, or hidden
 * for more map, all but its conditions (RhPrefs.statusLines; Lucas, 2026-09-24).  Its band is smoked
 * glass the map shows through, and a tap on it reaches the map.
 *
 * The message band is tty's top line, three rows high in portrait and two in
 * landscape, and it never grows over the map.  The core decides when it is
 * full and waits at --More-- (winandroid.c, rh_msg_place()), measuring each
 * message with the same wrap() this view draws with; a message longer than
 * the band is shown a band at a time.  Messages the player has acted on are
 * dimmed, as on the web (Lucas, 2026-09-28, after the message band research).
 */
public class RhScreen extends View
{
	/** Height of the status band, design dp; the map lives between it and the message band. */
	public static final float STATUS_BAND = 48f;

	/** The status band's glass: dark enough to read on, light enough to see the map through. */
	private static final int STATUS_SMOKE = 0x99090d0a;

	private static final float PAD_H     = 10f;
	/**
	 * The message band's text is sized by its x-height, 10dp: 0.25 degrees at a
	 * phone's 36 cm, the message band research's target, over reading science's
	 * 0.2 degree critical print size (VT323 at the old 11dp x 1.35 gave about
	 * 0.15).  True dp: the case's UI scale does not shrink it.  Times the
	 * player's size and the phone's own font size.  Rows 1.35 apart.
	 */
	private static final float MSG_X = 10f;
	private static final float MSG_LEADING = 1.35f;
	private static final float STAT_SIZE = 10.5f;
	private static final float LINE      = 13.5f;

	/** Rows of the message band: three in portrait, two in landscape (Lucas, 2026-09-28). */
	public static int msgRows(boolean portrait) { return portrait ? 3 : 2; }

	/** The message band's text size, px. */
	public static float msgTextPx(Context c)
	{
		float system = RhTheme.clamp(c.getResources().getConfiguration().fontScale, 0.85f, 2f);
		return RhTheme.rawDp(c, MSG_X / RhTheme.messageXHeight()) * RhTheme.messageScale() * system;
	}

	/** One row of the message band, px. */
	public static float msgRowPx(Context c)
	{
		return msgTextPx(c) * MSG_LEADING;
	}

	/** The message band's height for its rows, px: 5 above and 4 below at the UI scale. */
	public static float msgBandPx(Context c, int rows)
	{
		return RhTheme.dp(c, 5f) + RhTheme.dp(c, 4f) + rows * msgRowPx(c);
	}

	public interface Listener
	{
		/** A tap on the message lines while earlier messages have scrolled away. */
		void onHistory();

		/** A tap on the glass at --More--: Space. */
		void onMore();
	}

	/**
	 * What the core needs to decide when the band is full, read on the NetHack
	 * thread (NetHackIO.rhMsgBand, rhMsgRows): its rows, its text's width and
	 * font.  A copy of the paint, so the two threads never share one.  Null
	 * while no band is on screen -- the classic message line, as it was.
	 */
	private static final class Band
	{
		final TextPaint paint;
		final float width, slot;
		final int rows;

		Band(TextPaint paint, float width, float slot, int rows)
		{
			this.paint = paint;
			this.width = width;
			this.slot = slot;
			this.rows = rows;
		}
	}
	private static volatile Band sBand;
	private static volatile RhScreen sOwner;

	/** For the core: the band's rows; negative when a full band shouldn't pause; 0, no band. */
	public static int bandForCore()
	{
		Band b = sBand;
		if(b == null)
			return 0;
		return RhPrefs.morePause() ? b.rows : -b.rows;
	}

	/** For the core: how many rows a message takes, starting on the page's row start. */
	public static int rowsForCore(String text, int start)
	{
		Band b = sBand;
		if(b == null)
			return 1;
		synchronized(b)
		{
			return wrap(text, start, b.paint, b.width, b.slot, b.rows).size();
		}
	}

	/**
	 * A message broken into the band's rows at spaces, as tty breaks a long one
	 * (topl.c); a word wider than a row is broken where it must be.  A page's
	 * last row keeps room at its right end for --More--, as tty keeps 8 columns.
	 * The same code on the web (web.js, wrapRows), so the two builds page alike.
	 */
	static List<String> wrap(String text, int startRow, Paint paint, float width, float slot, int rows)
	{
		List<String> out = new ArrayList<>();
		if(width < 40f || rows <= 0)
		{
			out.add(text);
			return out;
		}
		StringBuilder row = new StringBuilder();
		int r = startRow;
		int i = 0, n = text.length();
		while(i < n)
		{
			int j = i;
			boolean spaces = text.charAt(i) == ' ';
			while(j < n && (text.charAt(j) == ' ') == spaces)
				j++;
			String w = text.substring(i, j);
			i = j;
			float room = width - ((r % rows) == rows - 1 ? slot : 0f);
			if(spaces)
			{
				// a break falls on spaces, and eats them
				if(row.length() > 0)
				{
					if(paint.measureText(row + w) <= room)
						row.append(w);
					else
					{
						out.add(trimEnd(row));
						row.setLength(0);
						r++;
					}
				}
				continue;
			}
			if(paint.measureText(row + w) <= room)
			{
				row.append(w);
				continue;
			}
			if(row.length() > 0)
			{
				out.add(trimEnd(row));
				row.setLength(0);
				r++;
				room = width - ((r % rows) == rows - 1 ? slot : 0f);
			}
			String rest = w;
			while(paint.measureText(rest) > room)
			{
				int k = rest.length() - 1;
				while(k > 1 && paint.measureText(rest, 0, k) > room)
					k--;
				out.add(rest.substring(0, k));
				rest = rest.substring(k);
				r++;
				room = width - ((r % rows) == rows - 1 ? slot : 0f);
			}
			row.append(rest);
		}
		if(row.length() > 0 || out.isEmpty())
			out.add(trimEnd(row));
		return out;
	}

	private static String trimEnd(StringBuilder sb)
	{
		int e = sb.length();
		while(e > 0 && sb.charAt(e - 1) == ' ')
			e--;
		return sb.substring(0, e);
	}

	private final TextPaint mMsg  = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final Paint mStat = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mScan = new Paint();
	private final Path mClip = new Path();
	private final RectF mRect = new RectF();

	private final Listener mListener;
	private RhStatus mStatus;
	private final int mRows;
	/** This page's messages; the page before, dimmed, while this one is empty. */
	private List<String> mPage = Collections.emptyList();
	private List<String> mOld = Collections.emptyList();
	/** The page row the band starts at: a message longer than the band. */
	private int mScroll;
	private boolean mMorePrompt;
	/** Rows to draw, and how many messages went by unshown (the pause turned off). */
	private final List<String> mShown = new ArrayList<>();
	private boolean mShownOld;
	private int mHidden;
	private boolean mDirty = true;
	private float mDownX, mDownY;

	public RhScreen(Context context, Listener listener, int rows)
	{
		super(context);
		mListener = listener;
		mRows = rows;
		mMsg.setTypeface(RhTheme.messageFont(context));
		mMsg.setTextSize(msgTextPx(context));
		mStat.setTypeface(RhTheme.screenFont(context));
		mStat.setTextSize(statSize());

		// Scanlines: one dark pixel row in every three dp, tiled.
		int period = Math.max(3, Math.round(dp(3f)));
		Bitmap b = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
		b.setPixel(0, 0, 0x3d000000);
		mScan.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
	}

	private float dp(float v) { return RhTheme.dp(getContext(), v); }

	/** The status band's height for the lines shown (RhPrefs.statusLines), design dp. */
	public static float statusBand()
	{
		switch(RhPrefs.statusLines())
		{
			case HIDDEN:  return 0f;
			case COMPACT: return STATUS_BAND - LINE;
			default:      return STATUS_BAND;
		}
	}

	public void setStatus(RhStatus status)
	{
		mStatus = status;
		invalidate();
	}

	public int rows() { return mRows; }

	/** The band's page (NHW_Message), where it is scrolled to, and whether --More-- is up. */
	public void setBand(List<String> page, List<String> old, int scroll, boolean morePrompt)
	{
		mPage = page == null ? Collections.<String>emptyList() : page;
		mOld = old == null ? Collections.<String>emptyList() : old;
		mScroll = Math.max(0, scroll);
		mMorePrompt = morePrompt;
		mDirty = true;
		invalidate();
	}

	/** Whether the MORE lamp is lit: --More-- is up, or messages went by unshown. */
	public boolean lampMore()
	{
		layoutRows();
		return mMorePrompt || mHidden > 0;
	}

	private float textWidth() { return getWidth() - 2 * dp(PAD_H); }
	private float slotWidth() { return mMsg.measureText("--More--") + dp(14f); }

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		mDirty = true;
		mClip.reset();
		mRect.set(0f, 0f, w, h);
		float r = dp(RhTheme.caseless() ? 8f : RhCase.GLASS_R);
		mClip.addRoundRect(mRect, r, r, Path.Direction.CW);
		publish();
	}

	@Override
	protected void onVisibilityChanged(View changedView, int visibility)
	{
		super.onVisibilityChanged(changedView, visibility);
		publish();
	}

	@Override
	protected void onAttachedToWindow()
	{
		super.onAttachedToWindow();
		publish();
	}

	@Override
	protected void onDetachedFromWindow()
	{
		if(sOwner == this)
		{
			sBand = null;
			sOwner = null;
		}
		super.onDetachedFromWindow();
	}

	/** Tell the core about the band this view draws, or that there is none. */
	private void publish()
	{
		if(getWidth() <= 0 || !isShown())
		{
			if(sOwner == this)
				sBand = null;
			return;
		}
		sBand = new Band(new TextPaint(mMsg), textWidth(), slotWidth(), mRows);
		sOwner = this;
	}

	/**
	 * The rows to draw.  Pausing, the page from where it is scrolled to -- the
	 * core never lets it run past the band.  Not pausing, the newest messages
	 * that fit whole, and a count of the rest.  An empty page shows the one
	 * before it, dimmed.
	 */
	private void layoutRows()
	{
		if(!mDirty)
			return;
		mDirty = false;
		mShown.clear();
		mHidden = 0;
		mShownOld = mPage.isEmpty();
		List<String> src = mShownOld ? mOld : mPage;
		if(src.isEmpty() || getWidth() <= 0)
			return;
		float width = textWidth(), slot = slotWidth();
		if(RhPrefs.morePause())
		{
			List<String> all = rowsOf(src, width, slot);
			int from = mShownOld ? Math.max(0, all.size() - mRows) : Math.min(mScroll, all.size());
			for(int i = from; i < all.size() && i < from + mRows; i++)
				mShown.add(all.get(i));
			return;
		}
		int k = src.size();
		List<String> fit = Collections.emptyList();
		while(k > 0)
		{
			List<String> trial = rowsOf(src.subList(k - 1, src.size()), width, slot);
			if(trial.size() > mRows)
				break;
			fit = trial;
			k--;
		}
		if(fit.isEmpty())
		{
			// the newest alone is longer than the band: its start, cut short
			List<String> last = rowsOf(src.subList(src.size() - 1, src.size()), width, slot);
			for(int i = 0; i < mRows && i < last.size(); i++)
				mShown.add(last.get(i));
			mShown.set(mShown.size() - 1, mShown.get(mShown.size() - 1) + "\u2026");
			k = src.size() - 1;
		}
		else
			mShown.addAll(fit);
		mHidden = k;
	}

	private List<String> rowsOf(List<String> msgs, float width, float slot)
	{
		List<String> rows = new ArrayList<>();
		for(String m : msgs)
			rows.addAll(wrap(m, rows.size(), mMsg, width, slot, mRows));
		return rows;
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDraw(Canvas canvas)
	{
		float w = getWidth(), h = getHeight();
		int text = RhTheme.phosphorText();
		canvas.save();
		canvas.clipPath(mClip);

		// The message band is solid glass: the map is centred below it and must not
		// show through the text.  Its height is fixed; what does not fit waits
		// behind --More--.
		layoutRows();
		float msgBottom = msgBandPx(getContext(), mRows);
		// Caseless, it is smoked glass over the map rather than the tube.
		boolean caseless = RhTheme.caseless();
		mFill.setColor(caseless ? 0xc7070a08 : RhTheme.GLASS_BG);
		canvas.drawRect(0f, 0f, w, msgBottom, mFill);
		// The status band is lighter smoke in both, so the map shows through it
		// (Lucas, 2026-09-24: the rows south of the hero were hidden under it).
		float band = dp(statusBand());
		if(band > 0f)
		{
			mFill.setColor(STATUS_SMOKE);
			canvas.drawRect(0f, h - band, w, h, mFill);
		}

		if(!mShown.isEmpty())
		{
			Paint.FontMetrics fm = mMsg.getFontMetrics();
			float lineH = msgRowPx(getContext());
			int colour = mShownOld ? RhTheme.phosphorDim() : text;
			mMsg.setColor(colour);
			if(!mShownOld)
				mMsg.setShadowLayer(dp(3f), 0f, 0f, (text & 0x00ffffff) | 0x80000000);
			canvas.save();
			canvas.clipRect(0f, 0f, w, msgBottom);
			for(int i = 0; i < mShown.size(); i++)
			{
				float base = dp(5f) + i * lineH + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
				canvas.drawText(mShown.get(i), dp(PAD_H), base, mMsg);
			}
			canvas.restore();
			mMsg.clearShadowLayer();
		}
		// The last row's right end: --More--, inverse amber as tty's standout
		// shows it, or with the pause off how many messages went by unshown.
		{
			Paint.FontMetrics fm = mMsg.getFontMetrics();
			float lineH = msgRowPx(getContext());
			float base = dp(5f) + (mRows - 1) * lineH + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			float right = w - dp(PAD_H);
			if(mMorePrompt)
			{
				String tag = "--More--";
				float tw = mMsg.measureText(tag);
				mFill.setColor(RhTheme.LAMP_AMBER);
				canvas.drawRect(right - tw - dp(6f), base + fm.ascent, right, base + fm.descent, mFill);
				mMsg.setColor(RhTheme.GLASS_BG);
				mMsg.setTextAlign(Paint.Align.RIGHT);
				canvas.drawText(tag, right - dp(3f), base, mMsg);
				mMsg.setTextAlign(Paint.Align.LEFT);
			}
			else if(mHidden > 0)
			{
				mMsg.setColor(RhTheme.LAMP_AMBER);
				mMsg.setTextAlign(Paint.Align.RIGHT);
				canvas.drawText("+" + mHidden + " \u25b8", right, base, mMsg);
				mMsg.setTextAlign(Paint.Align.LEFT);
			}
		}

		drawStatus(canvas, w, h);

		if(caseless)
		{
			canvas.restore();
			return;
		}

		// The tube: scanlines over everything, then a vignette and a faint glare.
		canvas.drawRect(0f, 0f, w, h, mScan);
		mFill.setColor(0xff000000); // opaque, or its alpha would fade the gradients
		mFill.setShader(new RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.62f,
				new int[] { 0x00000000, 0x00000000, 0x8c000000 },
				new float[] { 0f, 0.62f, 1f }, Shader.TileMode.CLAMP));
		canvas.drawRect(0f, 0f, w, h, mFill);
		mFill.setShader(new RadialGradient(w * 0.28f, h * 0.06f, Math.max(w, h) * 0.5f,
				0x12ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
		canvas.drawRect(0f, 0f, w, h, mFill);
		mFill.setShader(null);
		canvas.restore();
	}

	// ____________________________________________________________________________________
	private void drawStatus(Canvas canvas, float w, float h)
	{
		RhPrefs.StatusLines mode = RhPrefs.statusLines();
		if(mStatus == null || !mStatus.isPopulated())
			return;
		boolean compact = mode == RhPrefs.StatusLines.COMPACT;

		// The phosphor is plain text's colour.  HP's colour and the conditions'
		// severities show under every phosphor, as the menu colours do: they are
		// warnings (Lucas, 2026-09-27: "keep the status colours under amber too").
		int text = RhTheme.phosphorText();
		float x0 = dp(PAD_H);

		if(mode == RhPrefs.StatusLines.HIDDEN)
		{
			// Hidden keeps the conditions (Lucas, 2026-09-24): Stoned or Slimed must
			// never be one scrolled-away message.  Inverse video carries its own
			// ground, so they sit on the map where line 3 would put them.
			mStat.setTextSize(statSize());
			mStat.clearShadowLayer();
			Paint.FontMetrics fm = mStat.getFontMetrics();
			float lineH = dp(LINE);
			float baseline = h - dp(4f) - lineH + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			drawBadges(canvas, x0, w - dp(PAD_H), baseline, fm);
			return;
		}
		float top = h - dp(statusBand()) + dp(3.5f);

		String title = trimmed(mStatus.value(RhStatus.BL_TITLE));
		if(title.length() == 0)
			title = mStatus.name();
		String tail1 = line1Tail();
		String hpText = "HP:" + bare(RhStatus.BL_HP) + "(" + bare(RhStatus.BL_HPMAX) + ")";
		String tail2 = line2Tail();
		String stats = statsText();

		// The lines shrink together until the widest fits: the glass narrows on a
		// small screen, and the fonts on offer differ in width.  Room is kept for a
		// condition on whichever line carries them.
		mStat.setTextSize(statSize());
		float avail = w - 2 * dp(PAD_H);
		float widest = Math.max(mStat.measureText(title + "  " + tail1 + (compact ? "  Hungry" : "")),
				mStat.measureText(hpText + " " + tail2));
		if(!compact)
			widest = Math.max(widest, mStat.measureText(stats + "  Hungry"));
		if(widest > avail)
			mStat.setTextSize(statSize() * Math.max(0.6f, avail / widest));

		Paint.FontMetrics fm = mStat.getFontMetrics();
		float lineH = dp(LINE);
		float[] base = new float[compact ? 2 : 3];
		for(int i = 0; i < base.length; i++)
			base[i] = top + i * lineH + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
		mStat.setTextAlign(Paint.Align.LEFT);
		mStat.setShadowLayer(dp(2.5f), 0f, 0f, (text & 0x00ffffff) | 0x70000000);

		float hp = mStatus.hpFraction();
		int hpColour = hp >= 0.66f ? 0xff63e07c : hp >= 0.33f ? 0xfff5b342 : 0xffff5a44;

		// Line 1: the title under its HP bar, then where and when.
		float tw = mStat.measureText(title);
		mStat.setColor(text);
		canvas.drawText(title, x0, base[0], mStat);
		if(hp > 0f)
		{
			float barTop = base[0] + fm.ascent - dp(0.5f);
			float barBottom = base[0] + fm.descent;
			canvas.save();
			canvas.clipRect(x0 - dp(1f), barTop, x0 + tw * hp, barBottom);
			mFill.setColor(hpColour);
			canvas.drawRect(x0 - dp(1f), barTop, x0 + tw, barBottom, mFill);
			mStat.clearShadowLayer();
			mStat.setColor(RhTheme.GLASS_BG);
			canvas.drawText(title, x0, base[0], mStat);
			canvas.restore();
			mStat.setShadowLayer(dp(2.5f), 0f, 0f, (text & 0x00ffffff) | 0x70000000);
		}
		mStat.setColor(text);
		float tail1X = x0 + tw + mStat.measureText("  ");
		canvas.drawText(tail1, tail1X, base[0], mStat);

		// Line 2: HP in its own colour, then power, armour, experience, alignment.
		float x = x0;
		mStat.setColor(hpColour);
		canvas.drawText(hpText, x, base[1], mStat);
		x += mStat.measureText(hpText + " ");
		mStat.setColor(text);
		canvas.drawText(tail2, x, base[1], mStat);

		// Line 3: attributes, and the conditions right-aligned in inverse video.
		// Compact leaves the attributes off and the conditions move up to line 1,
		// where they keep a fixed place at its right end.
		float right = w - dp(PAD_H);
		if(compact)
		{
			mStat.clearShadowLayer();
			drawBadges(canvas, tail1X + mStat.measureText(tail1) + dp(8f), right, base[0], fm);
		}
		else
		{
			canvas.drawText(stats, x0, base[2], mStat);
			mStat.clearShadowLayer();
			drawBadges(canvas, x0 + mStat.measureText(stats) + dp(8f), right, base[2], fm);
		}
	}

	/** As many conditions as fit between left and right, worst first; the rest become "+N". */
	private void drawBadges(Canvas canvas, float left, float right, float baseline,
			Paint.FontMetrics fm)
	{
		List<RhBadges.Badge> badges = RhBadges.badgesFor(mStatus);
		float padX = dp(3f), gap = dp(4f);
		float used = 0f;
		int shown = 0;
		for(int i = 0; i < badges.size(); i++)
		{
			float bw = mStat.measureText(badges.get(i).text) + 2 * padX + (i > 0 ? gap : 0f);
			String more = "+" + (badges.size() - i - 1);
			float reserve = i < badges.size() - 1 ? mStat.measureText(more) + 2 * padX + gap : 0f;
			if(left + used + bw + reserve > right)
				break;
			used += bw;
			shown++;
		}
		float bx = right - used;
		int hidden = badges.size() - shown;
		if(hidden > 0)
		{
			String more = "+" + hidden;
			float mw = mStat.measureText(more) + 2 * padX;
			bx -= mw + (shown > 0 ? gap : 0f);
			drawBadge(canvas, bx, baseline, fm, more, 0xff2a302d, 0xffffffff);
			bx += mw + (shown > 0 ? gap : 0f);
		}
		for(int i = 0; i < shown; i++)
		{
			RhBadges.Badge b = badges.get(i);
			float bw = mStat.measureText(b.text) + 2 * padX;
			drawBadge(canvas, bx, baseline, fm, b.text,
					RhBadges.bg(b.tier), RhBadges.fg(b.tier));
			bx += bw + gap;
		}
	}

	private void drawBadge(Canvas canvas, float x, float baseline, Paint.FontMetrics fm,
	                       String s, int bg, int fg)
	{
		float padX = dp(3f);
		float w = mStat.measureText(s) + 2 * padX;
		mFill.setColor(bg);
		canvas.drawRect(x, baseline + fm.ascent - dp(0.5f), x + w, baseline + fm.descent, mFill);
		mStat.setColor(fg);
		canvas.drawText(s, x + padX, baseline, mStat);
	}

	/** Where and when: "Dlvl:13  $:5  T:2141". */
	private String line1Tail()
	{
		StringBuilder sb = new StringBuilder();
		appendRaw(sb, mStatus.value(RhStatus.BL_LEVELDESC));
		appendLabelled(sb, "$:", RhStatus.BL_GOLD);
		appendLabelled(sb, "T:", RhStatus.BL_TIME);
		return sb.toString();
	}

	/** Everything on line 2 after HP: power, armour, experience, alignment. */
	private String line2Tail()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Pw:").append(bare(RhStatus.BL_ENE)).append('(').append(bare(RhStatus.BL_ENEMAX)).append(')');
		appendLabelled(sb, "AC:", RhStatus.BL_AC);
		String xp = mStatus.bare(RhStatus.BL_XP);
		if(xp != null && xp.length() > 0)
		{
			sb.append(" Xp:").append(xp);
			String exp = mStatus.bare(RhStatus.BL_EXP);
			if(exp != null && exp.length() > 0)
				sb.append('/').append(exp);
		}
		else
			appendLabelled(sb, "HD:", RhStatus.BL_HD);
		String align = mStatus.bare(RhStatus.BL_ALIGN);
		if(align != null && align.length() > 0)
			sb.append("  ").append(align);
		return sb.toString();
	}

	private String statsText()
	{
		StringBuilder sb = new StringBuilder();
		appendLabelled(sb, "St:", RhStatus.BL_STR);
		appendLabelled(sb, "Dx:", RhStatus.BL_DX);
		appendLabelled(sb, "Co:", RhStatus.BL_CO);
		appendLabelled(sb, "In:", RhStatus.BL_IN);
		appendLabelled(sb, "Wi:", RhStatus.BL_WI);
		appendLabelled(sb, "Ch:", RhStatus.BL_CH);
		return sb.toString().trim();
	}

	private float statSize() { return dp(STAT_SIZE) * RhTheme.screenFontScale(); }

	private String bare(int idx)
	{
		return mStatus.bareOr(idx, "?");
	}

	private static String trimmed(String s)
	{
		return s == null ? "" : s.trim();
	}

	private static void appendRaw(StringBuilder sb, String v)
	{
		String t = trimmed(v);
		if(t.length() == 0)
			return;
		if(sb.length() > 0)
			sb.append("  ");
		sb.append(t);
	}

	/** Our own label on the bare value, so a field arrives looking the same whatever its decoration. */
	private void appendLabelled(StringBuilder sb, String label, int idx)
	{
		String v = mStatus.bare(idx);
		if(v == null || v.length() == 0)
			return;
		if(sb.length() > 0)
			sb.append(' ');
		sb.append(label).append(v);
	}

	// ____________________________________________________________________________________
	/**
	 * The message band is glass, not map: a tap on it stops here and opens the
	 * history.  Everything below it passes through to the map, the
	 * status lines included: they are see-through, and the map under them is as
	 * live as the rest (Lucas, 2026-09-24).
	 *
	 * At --More-- the whole glass is the prompt: a tap anywhere on it is Space,
	 * and nothing reaches the map until it is answered -- the tap that answers
	 * never becomes a move or a travel.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		if(mMorePrompt)
		{
			switch(e.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN:
					mDownX = e.getX();
					mDownY = e.getY();
					break;
				case MotionEvent.ACTION_UP:
					if(Math.hypot(e.getX() - mDownX, e.getY() - mDownY) < dp(16f) && mListener != null)
						mListener.onMore();
					break;
			}
			return true;
		}
		if(e.getY() >= msgBandPx(getContext(), mRows))
			return false;
		// A tap on the band opens the history, always: a band that answers only
		// sometimes teaches that it never does (the message band research, step 4)
		switch(e.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				mDownX = e.getX();
				mDownY = e.getY();
				break;
			case MotionEvent.ACTION_UP:
				if(Math.hypot(e.getX() - mDownX, e.getY() - mDownY) < dp(16f) && mListener != null)
					mListener.onHistory();
				break;
		}
		return true;
	}
}
