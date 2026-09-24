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
 */
public class RhScreen extends View
{
	/** Heights of the message and status bands, design dp; the map lives between. */
	public static final float MSG_BAND    = 36f;
	public static final float STATUS_BAND = 48f;

	private static final float PAD_H     = 10f;
	private static final float MSG_SIZE  = 11f;
	private static final float STAT_SIZE = 10.5f;
	private static final float LINE      = 13.5f;
	private static final int   MAX_MSG_LINES = 4;

	public interface Listener
	{
		/** A tap on the message lines while earlier messages have scrolled away. */
		void onHistory();
	}

	private final TextPaint mMsg  = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final Paint mStat = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mScan = new Paint();
	private final Path mClip = new Path();
	private final RectF mRect = new RectF();

	private final Listener mListener;
	private RhStatus mStatus;
	private String mMessage = "";
	private int mMore;
	private StaticLayout mLayout;

	public RhScreen(Context context, Listener listener)
	{
		super(context);
		mListener = listener;
		mMsg.setTypeface(RhTheme.screenFont(context));
		mMsg.setTextSize(msgSize());
		mStat.setTypeface(RhTheme.screenFont(context));
		mStat.setTextSize(statSize());

		// Scanlines: one dark pixel row in every three dp, tiled.
		int period = Math.max(3, Math.round(dp(3f)));
		Bitmap b = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
		b.setPixel(0, 0, 0x3d000000);
		mScan.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
	}

	private float dp(float v) { return RhTheme.dp(getContext(), v); }

	public void setStatus(RhStatus status)
	{
		mStatus = status;
		invalidate();
	}

	public void setMessage(String message, int more)
	{
		String m = message == null ? "" : message.trim();
		if(m.equals(mMessage) && more == mMore)
			return;
		mMessage = m;
		mMore = Math.max(0, more);
		mLayout = null;
		invalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		mLayout = null;
		mClip.reset();
		mRect.set(0f, 0f, w, h);
		float r = dp(RhTheme.caseless() ? 8f : RhCase.GLASS_R);
		mClip.addRoundRect(mRect, r, r, Path.Direction.CW);
	}

	@SuppressWarnings("deprecation")
	private StaticLayout layout()
	{
		if(mLayout == null && mMessage.length() > 0 && getWidth() > 0)
		{
			int width = Math.max(1, Math.round(getWidth() - 2 * dp(PAD_H)));
			// Lines sit exactly LINE apart whatever the font's own leading.
			Paint.FontMetrics fm = mMsg.getFontMetrics();
			mLayout = new StaticLayout(mMessage, mMsg, width, Layout.Alignment.ALIGN_NORMAL,
			                           1f, dp(LINE) - (fm.descent - fm.ascent), false);
		}
		return mLayout;
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDraw(Canvas canvas)
	{
		float w = getWidth(), h = getHeight();
		int text = RhTheme.phosphorText();
		canvas.save();
		canvas.clipPath(mClip);

		// The two bands are solid glass: the map is centred between them and must
		// not show through the text.  A long message spills over the map's top edge
		// rather than moving it -- the map area never changes size with the text.
		StaticLayout l = layout();
		int lines = l == null ? 0 : Math.min(l.getLineCount(), MAX_MSG_LINES);
		float msgBottom = Math.max(dp(MSG_BAND), dp(4f) + lines * dp(LINE) + dp(5f));
		// Caseless, the bands are smoked glass over the map rather than the tube.
		boolean caseless = RhTheme.caseless();
		mFill.setColor(caseless ? 0xc7070a08 : RhTheme.GLASS_BG);
		canvas.drawRect(0f, 0f, w, msgBottom, mFill);
		canvas.drawRect(0f, h - dp(STATUS_BAND), w, h, mFill);

		if(l != null)
		{
			canvas.save();
			canvas.translate(dp(PAD_H), dp(5f));
			canvas.clipRect(0f, 0f, w - 2 * dp(PAD_H), lines * dp(LINE));
			mMsg.setColor(text);
			mMsg.setShadowLayer(dp(3f), 0f, 0f, (text & 0x00ffffff) | 0x80000000);
			l.draw(canvas);
			mMsg.clearShadowLayer();
			canvas.restore();
		}
		if(mMore > 0)
		{
			mStat.setTextAlign(Paint.Align.RIGHT);
			mStat.setColor(RhTheme.LAMP_AMBER);
			String tag = "+" + mMore + " ▸";
			canvas.drawText(tag, w - dp(PAD_H), msgBottom - dp(4f), mStat);
			mStat.setTextAlign(Paint.Align.LEFT);
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
		if(mStatus == null || !mStatus.isPopulated())
			return;

		int text = RhTheme.phosphorText();
		boolean colour = RhTheme.phosphorColour();
		float x0 = dp(PAD_H);
		float top = h - dp(STATUS_BAND) + dp(3.5f);

		String title = trimmed(mStatus.value(RhStatus.BL_TITLE));
		if(title.length() == 0)
			title = mStatus.name();
		String tail1 = line1Tail();
		String hpText = "HP:" + bare(RhStatus.BL_HP) + "(" + bare(RhStatus.BL_HPMAX) + ")";
		String tail2 = line2Tail();
		String stats = statsText();

		// The three lines shrink together until the widest fits: the glass narrows on
		// a small screen, and the fonts on offer differ in width.
		mStat.setTextSize(statSize());
		float avail = w - 2 * dp(PAD_H);
		float widest = Math.max(mStat.measureText(title + "  " + tail1),
		               Math.max(mStat.measureText(hpText + " " + tail2), mStat.measureText(stats + "  Hungry")));
		if(widest > avail)
			mStat.setTextSize(statSize() * Math.max(0.6f, avail / widest));

		Paint.FontMetrics fm = mStat.getFontMetrics();
		float lineH = dp(LINE);
		float[] base = new float[3];
		for(int i = 0; i < 3; i++)
			base[i] = top + i * lineH + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
		mStat.setTextAlign(Paint.Align.LEFT);
		mStat.setShadowLayer(dp(2.5f), 0f, 0f, (text & 0x00ffffff) | 0x70000000);

		float hp = mStatus.hpFraction();
		int hpColour = !colour ? text
				: hp >= 0.66f ? 0xff63e07c : hp >= 0.33f ? 0xfff5b342 : 0xffff5a44;

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
		canvas.drawText(tail1, x0 + tw + mStat.measureText("  "), base[0], mStat);

		// Line 2: HP in its own colour, then power, armour, experience, alignment.
		float x = x0;
		mStat.setColor(hpColour);
		canvas.drawText(hpText, x, base[1], mStat);
		x += mStat.measureText(hpText + " ");
		mStat.setColor(text);
		canvas.drawText(tail2, x, base[1], mStat);

		// Line 3: attributes, and the conditions right-aligned in inverse video.
		canvas.drawText(stats, x0, base[2], mStat);
		mStat.clearShadowLayer();

		float right = w - dp(PAD_H);
		float left = x0 + mStat.measureText(stats) + dp(8f);
		List<RhBadges.Badge> badges = RhBadges.badgesFor(mStatus);
		float padX = dp(3f), gap = dp(4f);
		// Fit as many as there is room for, worst first; the rest become "+N".
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
			drawBadge(canvas, bx, base[2], fm, more, colour ? 0xff2a302d : text, colour ? 0xffffffff : RhTheme.GLASS_BG);
			bx += mw + (shown > 0 ? gap : 0f);
		}
		for(int i = 0; i < shown; i++)
		{
			RhBadges.Badge b = badges.get(i);
			float bw = mStat.measureText(b.text) + 2 * padX;
			drawBadge(canvas, bx, base[2], fm, b.text,
			          colour ? RhBadges.bg(b.tier) : text, colour ? RhBadges.fg(b.tier) : RhTheme.GLASS_BG);
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
	private float msgSize()  { return dp(MSG_SIZE) * RhTheme.screenFontScale(); }

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
	 * The bands are glass, not map: a tap on them stops here.  Only the message
	 * band answers one, and only while earlier messages have scrolled away -- the
	 * same rule the colourful style's message panel follows.  The middle of the
	 * glass passes every touch through to the map.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		float y = e.getY();
		boolean inMsg = y < dp(MSG_BAND);
		boolean inStatus = y > getHeight() - dp(STATUS_BAND);
		if(!inMsg && !inStatus)
			return false;
		if(inMsg && mMore > 0 && e.getActionMasked() == MotionEvent.ACTION_UP && mListener != null)
			mListener.onHistory();
		return true;
	}
}
