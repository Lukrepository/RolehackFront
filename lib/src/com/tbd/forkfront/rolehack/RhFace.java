package com.tbd.forkfront.rolehack;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * One control face of the Rolehack interface.
 *
 * Draws the 90s-hardware treatment from the handoff exactly once per face: a
 * vertical gradient, a 2dp white border inside the declared box, a hard inset
 * bevel (light top-left, dark bottom-right, no blur) and a soft drop shadow.
 *
 * The bevel is the two CSS inset shadows.  An inset shadow with a zero blur and
 * zero spread fills "the padding box minus a copy of itself shifted by the
 * offset", so each band is drawn into its own layer and the shifted copy punched
 * back out with DST_OUT.  Painting order is bottom-up, which puts the light band
 * on top where CSS lists it first.
 */
public class RhFace extends View
{
	/**
	 * RECT and CIRCLE carry their white edge as a stroked border.  PATH is for the
	 * hub silhouettes: a clipped shape cannot use a border, because the clip cuts
	 * the border off with everything else, so those are drawn the way the handoff
	 * specifies -- an outer face in #fff carrying the shape, and an inner face
	 * inset a few dp carrying the gradient.  The white that shows between them is
	 * the edge, and it follows the silhouette for free.
	 */
	public enum Shape { RECT, CIRCLE, PATH, LENS }

	private final Path mBorderPath = new Path();
	private final Path mInnerPath  = new Path();
	private final RectF mBorderRect = new RectF();
	private final RectF mInnerRect  = new RectF();
	private final RectF mOuterRect  = new RectF();

	private final Paint mFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mBand   = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mPunch  = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText   = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

	private Shape mShape = Shape.RECT;
	private int[] mFace = RhTheme.G90;
	private float mRadiusDp = RhTheme.FACE_RADIUS;

	/** Silhouette as unit coordinates (x,y pairs in 0..1), evaluated against the box. */
	private float[] mPoly;
	/** Inset of the gradient face inside the white one, in dp: left, top, right, bottom. */
	private float mInsL, mInsT, mInsR, mInsB;
	/** Extra bottom padding for the label, so a triangle's apex does not have to hold text. */
	private float mLabelPadBottomDp;
	/** Extra right padding, so a chevron's point does not have to hold text. */
	private float mLabelPadRightDp;

	private String mLabelRaw = "";
	private String[] mLabelLines = new String[0];
	/** Text size the wrap pass settled on, in px; recomputed when the label or box changes. */
	private float mFittedSizePx;
	private boolean mLabelDirty = true;
	private String mSub;
	private int mSubColor = RhTheme.TEXT;
	private float mSubAlpha = 0.75f;
	private float mLabelSizeDp = 10f;
	private float mLabelLeading = 1.1f;
	private float mLabelTracking = 0.04f; // em
	private float mSubSizeDp = 8f;
	private boolean mUppercase = true;
	/** Drawer items use Outfit for the word; every other face uses Space Mono. */
	private boolean mLabelInOutfit;
	private int mLabelColor = RhTheme.TEXT;

	private boolean mPressedFace;
	private boolean mPlaceholder;

	// Terminal style: see drawKeycap().
	/** A keycap family that overrides the one the face colour implies, or null. */
	private int[] mCap;
	private int[] mDefaultCap;
	private String mTag;
	public static final int LAMP_NONE = 0, LAMP_OFF = 1, LAMP_ON = 2;
	private int mLamp = LAMP_NONE;
	/** Backlit legend: the context key while it has something to offer. */
	private boolean mLit;
	private final RectF mCapSkirt = new RectF();
	private final RectF mCapTop   = new RectF();
	private final Path  mArrow    = new Path();
	private final float mBorderW;
	private final float mBevel;
	private final float mShadowDy;
	private final float mShadowBlur;

	// ____________________________________________________________________________________
	public RhFace(Context context)
	{
		super(context);

		mBorderW    = RhTheme.dp(context, RhTheme.FACE_BORDER_W);
		mBevel      = RhTheme.dp(context, RhTheme.BEVEL_OFFSET);
		mShadowDy   = RhTheme.dp(context, RhTheme.DROP_SHADOW_DY);
		mShadowBlur = RhTheme.dp(context, RhTheme.DROP_SHADOW_BLUR);

		mStroke.setStyle(Paint.Style.STROKE);
		mStroke.setStrokeWidth(mBorderW);
		mStroke.setColor(RhTheme.FACE_BORDER);

		mPunch.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
		mPunch.setColor(0xff000000);

		mShadow.setColor(RhTheme.DROP_SHADOW);
		// CSS blur radius is roughly twice the Gaussian standard deviation.
		mShadow.setMaskFilter(new BlurMaskFilter(mShadowBlur / 2f, BlurMaskFilter.Blur.NORMAL));

		mText.setTypeface(RhTheme.monoBold(context));
		mText.setTextAlign(Paint.Align.CENTER);

		// The drop shadow's mask filter and the DST_OUT punch both need a real
		// layer.  These faces are static between presses, so a software layer
		// costs one rasterisation each and nothing per frame.
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}

	// ____________________________________________________________________________________
	public RhFace shape(Shape s)      { mShape = s; invalidate(); return this; }

	/**
	 * Fit a silhouette to this face.  Insets are the handoff's own numbers: 3dp all
	 * round for the star, 2dp for the chevron, and 5/4/3 top/sides/bottom for the
	 * triangle.
	 */
	public RhFace poly(float[] unitPoints, float insL, float insT, float insR, float insB)
	{
		mShape = Shape.PATH;
		mPoly = unitPoints;
		mInsL = insL; mInsT = insT; mInsR = insR; mInsB = insB;
		mLabelDirty = true;
		requestLayout();
		invalidate();
		return this;
	}

	/**
	 * An empty pin slot: a dashed outline over near-nothing, so it reads as a place
	 * a command could go rather than a command.
	 */
	public RhFace placeholder(boolean on)
	{
		mPlaceholder = on;
		invalidate();
		return this;
	}

	public RhFace labelPadBottom(float dp) { mLabelPadBottomDp = dp; invalidate(); return this; }
	public RhFace labelPadRight(float dp) { mLabelPadRightDp = dp; mLabelDirty = true; invalidate(); return this; }
	public RhFace face(int[] tokens)  { mFace = tokens; invalidate(); return this; }
	public RhFace radius(float dp)    { mRadiusDp = dp; invalidate(); return this; }
	public RhFace uppercase(boolean u){ mUppercase = u; mLabelDirty = true; invalidate(); return this; }
	public RhFace outfitLabel(boolean o) { mLabelInOutfit = o; mLabelDirty = true; invalidate(); return this; }

	public int[] faceTokens() { return mFace; }

	/** Terminal style: draw this face in a given keycap family whatever its colour. */
	public RhFace cap(int[] family) { mCap = family; invalidate(); return this; }
	/**
	 * Terminal style: the family for this key while it wears the default colour.
	 * A pinned slot that holds Wear takes it; one that holds Take off keeps the
	 * slate its command asks for.
	 */
	public RhFace defaultCap(int[] family) { mDefaultCap = family; invalidate(); return this; }
	/** Terminal style: a short tag in the face's top-left corner, where a raw key would sit. */
	public RhFace tag(String t)     { mTag = t; invalidate(); return this; }
	/** Terminal style: a small indicator window in the keycap's top-right corner. */
	public RhFace lamp(int state)   { mLamp = state; invalidate(); return this; }
	/** Terminal style: a backlit amber legend. */
	public RhFace lit(boolean on)   { mLit = on; invalidate(); return this; }

	public RhFace label(String text, float sizeDp, float tracking)
	{
		mLabelSizeDp = sizeDp;
		mLabelTracking = tracking;
		mLabelRaw = text == null ? "" : text;
		mLabelDirty = true;
		invalidate();
		return this;
	}

	public RhFace leading(float multiple) { mLabelLeading = multiple; invalidate(); return this; }

	/** Label colour; amber faces carry near-black text rather than white. */
	public RhFace textColor(int argb) { mLabelColor = argb; invalidate(); return this; }

	/** The raw NetHack key printed under the word, or a hub's "hold" hint. */
	public RhFace sub(String text, float sizeDp, int color, float alpha)
	{
		mSub = text;
		mSubSizeDp = sizeDp;
		mSubColor = color;
		mSubAlpha = alpha;
		invalidate();
		return this;
	}

	public void setFacePressed(boolean pressed)
	{
		if(mPressedFace == pressed)
			return;
		mPressedFace = pressed;
		// A keycap sinks inside its own skirt (drawKeycap), so the view stays put.
		if(mShape == Shape.RECT && !RhTheme.terminal())
			setTranslationY(pressed ? RhTheme.dp(getContext(), RhTheme.PRESS_SINK_DP) : 0f);
		invalidate();
	}

	public boolean isFacePressed()
	{
		return mPressedFace;
	}

	// ____________________________________________________________________________________
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		rebuildPaths(w, h);
		mLabelDirty = true;
	}

	private void rebuildPaths(int w, int h)
	{
		// The drop shadow is drawn inside the view, so keep a margin for its blur.
		float inset = 0f;
		mOuterRect.set(inset, inset, w - inset, h - inset);

		// box-sizing: border-box -- the 2dp border sits inside the declared size,
		// so the stroke is centred half a border-width in.
		mBorderRect.set(mOuterRect.left + mBorderW / 2f, mOuterRect.top + mBorderW / 2f,
		                mOuterRect.right - mBorderW / 2f, mOuterRect.bottom - mBorderW / 2f);
		mInnerRect.set(mOuterRect.left + mBorderW, mOuterRect.top + mBorderW,
		               mOuterRect.right - mBorderW, mOuterRect.bottom - mBorderW);

		mBorderPath.reset();
		mInnerPath.reset();

		if(mShape == Shape.PATH && mPoly != null)
		{
			// The white face fills the whole box; the gradient face is the same
			// silhouette re-evaluated on an inset box, exactly as the CSS does it
			// with `inset:` on a child carrying the same clip-path.
			mInnerRect.set(mOuterRect.left + RhTheme.dp(getContext(), mInsL),
			               mOuterRect.top + RhTheme.dp(getContext(), mInsT),
			               mOuterRect.right - RhTheme.dp(getContext(), mInsR),
			               mOuterRect.bottom - RhTheme.dp(getContext(), mInsB));
			buildPoly(mBorderPath, mOuterRect);
			buildPoly(mInnerPath, mInnerRect);
		}
		else if(mShape == Shape.LENS)
		{
			buildLens(mBorderPath, mBorderRect);
			buildLens(mInnerPath, mInnerRect);
		}
		else if(mShape == Shape.CIRCLE)
		{
			mBorderPath.addOval(mBorderRect, Path.Direction.CW);
			mInnerPath.addOval(mInnerRect, Path.Direction.CW);
		}
		else
		{
			float r = RhTheme.dp(getContext(), mRadiusDp);
			mBorderPath.addRoundRect(mBorderRect, r, r, Path.Direction.CW);
			mInnerPath.addRoundRect(mInnerRect, Math.max(0f, r - mBorderW), Math.max(0f, r - mBorderW),
			                        Path.Direction.CW);
		}

		mFill.setShader(new LinearGradient(0, mInnerRect.bottom, 0, mInnerRect.top,
		                                   mFace[0], mFace[1], Shader.TileMode.CLAMP));
	}

	/**
	 * An eye: two arcs bulging to the full height at the centre and meeting at
	 * points on the left and right.
	 *
	 * The handoff writes this as `border-radius: 50% / 100%`, which is the CSS way
	 * to ask for it -- but CSS clamps radii that overflow their box, and on a
	 * 104x52 face that clamp turns the intended lens into a plain pill.  Drawing
	 * the two curves directly gives the shape the handoff actually describes, and
	 * the border still follows it because the same path is stroked.
	 *
	 * Each quadratic's control point sits a full half-height beyond the edge,
	 * which puts the apex exactly on the box boundary at the midpoint.
	 */
	private static void buildLens(Path out, RectF box)
	{
		float midY = box.centerY();
		float h = box.height();

		out.reset();
		out.moveTo(box.left, midY);
		out.quadTo(box.centerX(), midY - h, box.right, midY);
		out.quadTo(box.centerX(), midY + h, box.left, midY);
		out.close();
	}

	private void buildPoly(Path out, RectF box)
	{
		out.reset();
		for(int i = 0; i + 1 < mPoly.length; i += 2)
		{
			float x = box.left + mPoly[i] * box.width();
			float y = box.top + mPoly[i + 1] * box.height();
			if(i == 0)
				out.moveTo(x, y);
			else
				out.lineTo(x, y);
		}
		out.close();
	}


	// ____________________________________________________________________________________
	@SuppressLint("DrawAllocation")
	@Override
	protected void onDraw(Canvas canvas)
	{
		if(RhTheme.terminal())
		{
			drawKeycap(canvas);
			return;
		}

		if(mInnerPath.isEmpty())
			rebuildPaths(getWidth(), getHeight());

		if(mPlaceholder)
		{
			// No gradient, no bevel, no shadow -- an outline and a faint wash.
			mBand.setColor(0x0dffffff);
			canvas.drawPath(mInnerPath, mBand);
			mStroke.setColor(0x4dffffff);
			mStroke.setPathEffect(new android.graphics.DashPathEffect(
					new float[] { RhTheme.dp(getContext(), 4f), RhTheme.dp(getContext(), 3f) }, 0f));
			canvas.drawPath(mBorderPath, mStroke);
			mStroke.setPathEffect(null);
			mStroke.setColor(RhTheme.FACE_BORDER);
			drawLabel(canvas);
			return;
		}

		float brightness = 1f;
		if(mPressedFace)
			brightness = mShape == Shape.CIRCLE
					? RhTheme.PRESS_BRIGHTNESS_CIRCLE
					: RhTheme.PRESS_BRIGHTNESS_RECT;

		// 0 2px 7px rgba(0,0,0,.55)
		canvas.save();
		canvas.translate(0, mShadowDy);
		canvas.drawPath(mBorderPath, mShadow);
		canvas.restore();

		mFill.setShader(new LinearGradient(0, mInnerRect.bottom, 0, mInnerRect.top,
		                                   RhTheme.brighten(mFace[0], brightness),
		                                   RhTheme.brighten(mFace[1], brightness),
		                                   Shader.TileMode.CLAMP));

		// A silhouette carries its white edge as the outer face showing through,
		// since a clip cuts a stroked border off with everything else.
		if(mShape == Shape.PATH)
		{
			mBand.setColor(RhTheme.FACE_BORDER);
			canvas.drawPath(mBorderPath, mBand);
		}

		canvas.save();
		canvas.clipPath(mInnerPath);
		canvas.drawPath(mInnerPath, mFill);
		// Dark band first (bottom-right), light band over it (top-left).
		drawInsetBand(canvas, -mBevel, -mBevel, RhTheme.BEVEL_DARK);
		drawInsetBand(canvas,  mBevel,  mBevel, RhTheme.BEVEL_LIGHT);
		canvas.restore();

		if(mShape != Shape.PATH)
			canvas.drawPath(mBorderPath, mStroke);

		drawLabel(canvas);
	}

	/**
	 * One CSS inset box-shadow with zero blur and zero spread: the inner shape
	 * minus a copy of itself shifted by (dx, dy).
	 */
	private void drawInsetBand(Canvas canvas, float dx, float dy, int color)
	{
		int layer = canvas.saveLayer(mOuterRect, null, Canvas.ALL_SAVE_FLAG);
		mBand.setColor(color);
		canvas.drawPath(mInnerPath, mBand);
		canvas.save();
		canvas.translate(dx, dy);
		canvas.drawPath(mInnerPath, mPunch);
		canvas.restore();
		canvas.restoreToCount(layer);
	}

	// ____________________________________________________________________________________
	// Terminal style: a sculpted keycap, per the design canvas's recipe.
	//
	// The skirt is lit from the left and darkens toward the bottom; the top face
	// is a dished rounded rectangle set back from a front skirt that is taller than
	// the sides, which is what makes the key read as raised; a hard 2dp shadow and
	// a soft blur fall onto the well.  The tap legend sits on the top face, a hold
	// legend is printed on the front skirt, and a raw key sits in the face's
	// top-left corner.  Pressed, the face drops inside its skirt and the shadow
	// shortens -- the view itself never moves.
	//
	// Every shape draws as the same keycap.  Silhouettes told the hubs apart when
	// colour alone could not; a keyboard does that with size and position instead.

	private final RectF mLampRect = new RectF();
	private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	/** The front skirt's height: about a fifth of the key, within 7..12dp. */
	private float capFront(float h)
	{
		return Math.max(RhTheme.dp(getContext(), 7f), Math.min(RhTheme.dp(getContext(), 12f), h * 0.2f));
	}

	private void capGeometry()
	{
		Context c = getContext();
		float w = getWidth(), h = getHeight();
		float shadow = RhTheme.dp(c, 2.5f);
		float front = capFront(h);
		float side = Math.min(RhTheme.dp(c, 4f), w / 12f);
		float sink = mPressedFace ? Math.max(0f, Math.min(RhTheme.dp(c, 3f), front - RhTheme.dp(c, 3f))) : 0f;
		mCapSkirt.set(0f, 0f, w, h - shadow);
		mCapTop.set(side, RhTheme.dp(c, 2f) + sink, w - side, h - shadow - front + sink);
	}

	private int[] capFamily()
	{
		if(mCap != null)
			return mCap;
		if(mPlaceholder)
			return RhTheme.capFor(RhTheme.G90);
		if(mDefaultCap != null && (mFace == RhTheme.G90 || mFace == null))
			return mDefaultCap;
		return RhTheme.capFor(mFace);
	}

	/** Raw keys ride in the sub-line in the raw-key colours; anything else there is a hint. */
	private boolean subIsRawKey()
	{
		return mSubColor == RhTheme.RAW_KEY || mSubColor == RhTheme.RAW_KEY_DIM;
	}

	private boolean hasRawKey()
	{
		return mSub != null && mSub.length() > 0 && subIsRawKey();
	}

	/** The front-skirt legend: a hold hint, shortened to what the hold does. */
	private String holdLegend()
	{
		if(mSub == null || mSub.length() == 0 || subIsRawKey())
			return null;
		String t = mSub.trim();
		if(t.startsWith("hold · "))
			t = t.substring(7);
		else if(t.equals("hold to set"))
			t = "set ×n";
		else if(t.equals("hold to edit"))
			t = "edit";
		return t.toUpperCase();
	}

	/** The numpad's arrow labels become drawn arrows; NaN for any other label. */
	private static float arrowDegrees(String s)
	{
		if(s == null || s.length() != 1)
			return Float.NaN;
		switch(s.charAt(0))
		{
			case '↑': return 0f;
			case '↗': return 45f;
			case '→': return 90f;
			case '↘': return 135f;
			case '↓': return 180f;
			case '↙': return 225f;
			case '←': return 270f;
			case '↖': return 315f;
		}
		return Float.NaN;
	}

	private void drawKeycap(Canvas canvas)
	{
		Context c = getContext();
		int[] cap = capFamily();
		capGeometry();
		if(mLabelDirty)
			fitLabel();

		float w = getWidth();
		float r = Math.min(RhTheme.dp(c, 6f), mCapSkirt.height() / 5f);
		float fr = Math.max(0f, r - RhTheme.dp(c, 1f));

		// Shadows onto the well: the soft blur, then the hard edge.
		canvas.save();
		canvas.translate(0f, RhTheme.dp(c, mPressedFace ? 1f : 2f));
		canvas.drawRoundRect(mCapSkirt, r, r, mShadow);
		mBand.setShader(null);
		mBand.setColor(0x8c000000);
		canvas.drawRoundRect(mCapSkirt, r, r, mBand);
		canvas.restore();

		// The skirt, lit from the left, darkening toward the front.
		mFill.setShader(new LinearGradient(mCapSkirt.left, 0f, mCapSkirt.right, 0f,
				new int[] { cap[RhTheme.CAP_SL], cap[RhTheme.CAP_SM], cap[RhTheme.CAP_SM], cap[RhTheme.CAP_SR] },
				new float[] { 0f, 0.14f, 0.86f, 1f }, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(mCapSkirt, r, r, mFill);
		// Opaque first: the paint's alpha would scale the gradient.
		mBand.setColor(0xff000000);
		mBand.setShader(new LinearGradient(0f, mCapSkirt.top, 0f, mCapSkirt.bottom,
				new int[] { 0x24ffffff, 0x00ffffff, 0x52000000 },
				new float[] { 0f, 0.3f, 1f }, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(mCapSkirt, r, r, mBand);
		mBand.setShader(null);

		// The hold legend goes down before the face, so a pressed face covers it
		// the way a real keycap's top hides its own front print.
		String hold = holdLegend();
		if(hold != null)
		{
			mText.setTypeface(RhTheme.capFont(getContext()));
			mText.setTextAlign(Paint.Align.CENTER);
			setTracking(mText, 0.09f);
			float size = RhTheme.dp(c, 7.5f);
			float avail = w - 2 * mCapTop.left - RhTheme.dp(c, 2f);
			mText.setTextSize(size);
			while(size > RhTheme.dp(c, 5.5f) && mText.measureText(hold) > avail)
			{
				size *= 0.92f;
				mText.setTextSize(size);
			}
			mText.setColor(cap[RhTheme.CAP_HOLD]);
			Paint.FontMetrics fm = mText.getFontMetrics();
			float bandTop = mCapSkirt.bottom - capFront(getHeight());
			float mid = (bandTop + mCapSkirt.bottom) / 2f;
			canvas.drawText(hold, w / 2f, mid - (fm.ascent + fm.descent) / 2f, mText);
		}

		// The top face: a dish, brightest a little above centre, with a lit rim.
		float fw = mCapTop.width(), fh = mCapTop.height();
		if(fw <= 0f || fh <= 0f)
			return;
		mFill.setShader(new RadialGradient(mCapTop.centerX(), mCapTop.top + fh * 0.15f,
				Math.max(fw, fh) * 0.95f, new int[] { cap[RhTheme.CAP_T1], cap[RhTheme.CAP_T2] },
				new float[] { 0f, 0.85f }, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(mCapTop, fr, fr, mFill);
		mBand.setColor(mPressedFace ? 0x38ffffff : 0x66ffffff);
		canvas.drawRect(mCapTop.left + fr, mCapTop.top, mCapTop.right - fr,
		                mCapTop.top + RhTheme.dp(c, 1f), mBand);

		if(mLamp != LAMP_NONE)
		{
			float inset = RhTheme.dp(c, 4f);
			mLampRect.set(mCapTop.right - inset - RhTheme.dp(c, 10f), mCapTop.top + inset,
			              mCapTop.right - inset, mCapTop.top + inset + RhTheme.dp(c, 4f));
			boolean on = mLamp == LAMP_ON;
			mBand.setColor(on ? RhTheme.LAMP_AMBER : RhTheme.LAMP_AMBER_OFF);
			if(on)
				mBand.setShadowLayer(RhTheme.dp(c, 4f), 0f, 0f, RhTheme.LAMP_AMBER);
			canvas.drawRoundRect(mLampRect, RhTheme.dp(c, 2f), RhTheme.dp(c, 2f), mBand);
			mBand.clearShadowLayer();
		}

		int legend = mLit ? RhTheme.CAP_LIT : cap[RhTheme.CAP_LEGEND];
		int legendAlpha = mPlaceholder ? 110 : 255;

		// A raw key rides in the corner only while it is a key's worth: an
		// extended command such as #exploremode ran into its own label, and the
		// case switch's "#case" is not a key at all.  The label says what it does.
		String corner = hasRawKey() ? mSub.trim() : mTag;
		if(hasRawKey() && corner.length() > 4)
			corner = null;
		if(corner != null && corner.length() > 0)
		{
			mText.setTypeface(RhTheme.capFont(getContext()));
			mText.setTextAlign(Paint.Align.LEFT);
			setTracking(mText, 0f);
			mText.setTextSize(Math.min(RhTheme.dp(c, 8.5f), fh * 0.32f));
			mText.setColor(cap[RhTheme.CAP_RAW]);
			Paint.FontMetrics fm = mText.getFontMetrics();
			canvas.drawText(corner, mCapTop.left + RhTheme.dp(c, 3f),
			                mCapTop.top + RhTheme.dp(c, 2f) - fm.ascent, mText);
		}

		float deg = arrowDegrees(mLabelRaw);
		if(!Float.isNaN(deg))
		{
			float a = Math.min(fw, fh) * 0.24f;
			mArrowPaint.setStyle(Paint.Style.STROKE);
			mArrowPaint.setStrokeCap(Paint.Cap.ROUND);
			mArrowPaint.setStrokeJoin(Paint.Join.ROUND);
			mArrowPaint.setStrokeWidth(a * 0.23f);
			mArrowPaint.setColor(legend);
			mArrowPaint.setAlpha(legendAlpha);
			mArrow.reset();
			mArrow.moveTo(0f, a);
			mArrow.lineTo(0f, -a);
			mArrow.moveTo(-0.55f * a, -0.45f * a);
			mArrow.lineTo(0f, -a);
			mArrow.lineTo(0.55f * a, -0.45f * a);
			canvas.save();
			canvas.translate(mCapTop.centerX(), mCapTop.centerY());
			canvas.rotate(deg);
			canvas.drawPath(mArrow, mArrowPaint);
			canvas.restore();
			return;
		}

		if(mLabelLines.length == 0)
			return;
		mText.setTypeface(RhTheme.capFont(getContext()));
		mText.setTextAlign(Paint.Align.CENTER);
		setTracking(mText, 0.05f);
		mText.setTextSize(mFittedSizePx);
		mText.setColor(legend);
		mText.setAlpha(legendAlpha);
		if(mLit)
			mText.setShadowLayer(RhTheme.dp(c, 5f), 0f, 0f, 0xbfffaa3c);
		Paint.FontMetrics fm = mText.getFontMetrics();
		float lineH = mFittedSizePx * mLabelLeading;
		float y = mCapTop.top + (fh - mLabelLines.length * lineH) / 2f;
		for(String line : mLabelLines)
		{
			float baseline = y + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			canvas.drawText(line, mCapTop.centerX(), baseline, mText);
			y += lineH;
		}
		mText.clearShadowLayer();
	}

	// ____________________________________________________________________________________
	private void drawLabel(Canvas canvas)
	{
		if(mLabelDirty)
			fitLabel();
		if(mLabelLines.length == 0 && mSub == null)
			return;

		float padRight = RhTheme.dp(getContext(), mLabelPadRightDp);
		float cx = (getWidth() - padRight) / 2f;
		float labelSize = mFittedSizePx;
		float subSize   = RhTheme.dp(getContext(), mSubSizeDp);
		float lineH     = labelSize * mLabelLeading;
		float subH      = mSub != null ? subSize * 1.15f : 0f;

		float blockH = mLabelLines.length * lineH + subH;
		// A triangle's apex cannot hold text, so its label is pinned to the base
		// rather than centred in the box.
		float y = mLabelPadBottomDp > 0f
				? getHeight() - RhTheme.dp(getContext(), mLabelPadBottomDp) - blockH
				: (getHeight() - blockH) / 2f;

		mText.setTypeface(mLabelInOutfit ? RhTheme.outfitSemi(getContext()) : RhTheme.monoBold(getContext()));
		mText.setTextSize(labelSize);
		mText.setColor(mLabelColor);
		mText.setAlpha(255);
		setTracking(mText, mLabelTracking);

		Paint.FontMetrics fm = mText.getFontMetrics();
		for(String line : mLabelLines)
		{
			// Centre each line in its own leading box.
			float baseline = y + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
			canvas.drawText(line, cx, baseline, mText);
			y += lineH;
		}

		if(mSub != null)
		{
			mText.setTypeface(RhTheme.monoRegular(getContext()));
			mText.setTextSize(subSize);
			mText.setColor(mSubColor);
			mText.setAlpha(Math.round(255 * mSubAlpha * ((mSubColor >>> 24) / 255f)));
			setTracking(mText, 0f);
			Paint.FontMetrics sfm = mText.getFontMetrics();
			float baseline = y + (subH - (sfm.descent - sfm.ascent)) / 2f - sfm.ascent;
			canvas.drawText(mSub, cx, baseline, mText);
		}
	}

	private static void setTracking(Paint p, float em)
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			p.setLetterSpacing(em);
	}

	/**
	 * Wrap the label to the face, shrinking only if wrapping alone cannot make it
	 * fit.
	 *
	 * The prototype gets this free -- its faces are flex boxes and the browser
	 * wraps "Two-weapon" and "Wear armor" onto two lines inside their circles.
	 * Without it a 40dp fan slot renders "THROW" edge to edge and the longer
	 * weapon and armor labels run straight off the face.
	 */
	private void fitLabel()
	{
		mLabelDirty = false;
		boolean keycap = RhTheme.terminal();
		// A condensed face sets the same words narrower, so keycap legends run a
		// little larger than the Space Mono labels they replace.
		mFittedSizePx = RhTheme.dp(getContext(), keycap ? Math.max(mLabelSizeDp, 8f) * 1.15f : mLabelSizeDp);

		if(mLabelRaw.length() == 0)
		{
			mLabelLines = new String[0];
			return;
		}

		String text = mUppercase ? mLabelRaw.toUpperCase() : mLabelRaw;

		// An explicit newline is an author's decision; honour it and do not re-wrap.
		if(text.indexOf('\n') >= 0)
		{
			mLabelLines = text.split("\n");
			return;
		}

		int w = getWidth();
		if(w <= 0)
		{
			mLabelLines = new String[] { text };
			mLabelDirty = true; // retry once the face has been measured
			return;
		}

		// Usable width inside the border.  A circle is narrower than its box
		// wherever the text actually sits, so allow for the chord rather than the
		// diameter.
		float avail;
		if(keycap)
		{
			capGeometry();
			// A keycap honours the right pad too -- drawLabel() shifts the centre for
			// it either way, and the swipe well's dots need the room.
			avail = mCapTop.width() - RhTheme.dp(getContext(), 6f)
			        - RhTheme.dp(getContext(), mLabelPadRightDp);
		}
		else
		{
			avail = w - 2 * mBorderW - RhTheme.dp(getContext(), 3f)
			        - RhTheme.dp(getContext(), mLabelPadRightDp);
			if(mShape == Shape.CIRCLE)
				avail *= 0.86f;
			else if(mShape == Shape.PATH)
				avail *= 0.72f; // silhouettes are narrower than their box wherever text sits
			else if(mShape == Shape.LENS)
				avail *= 0.8f;  // the eye is full width only exactly on its centreline
		}
		if(avail <= 0)
		{
			mLabelLines = new String[] { text };
			return;
		}

		if(keycap)
		{
			mText.setTypeface(RhTheme.capFont(getContext()));
			setTracking(mText, 0.05f);
		}
		else
		{
			mText.setTypeface(mLabelInOutfit ? RhTheme.outfitSemi(getContext()) : RhTheme.monoBold(getContext()));
			setTracking(mText, mLabelTracking);
		}

		float size = mFittedSizePx;
		float minSize = RhTheme.dp(getContext(), 6f);
		while(true)
		{
			mText.setTextSize(size);
			String[] lines = wrap(text, avail);
			// A keycap's face is shorter than its box, so its legend must fit the
			// height as well as the width.
			boolean fits = lines != null
					&& (!keycap || lines.length * size * mLabelLeading <= mCapTop.height());
			if(fits || size <= minSize)
			{
				mLabelLines = lines != null ? lines : new String[] { text };
				mFittedSizePx = size;
				return;
			}
			size *= 0.92f;
		}
	}

	/**
	 * Greedy wrap at spaces and after hyphens.  Returns null when some single
	 * fragment still overflows, which tells the caller to try a smaller size.
	 */
	private String[] wrap(String text, float avail)
	{
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		java.util.ArrayList<String> out = new java.util.ArrayList<String>();

		for(String word : words)
		{
			if(mText.measureText(word) > avail)
				return null;

			if(line.length() == 0)
			{
				line.append(word);
				continue;
			}
			String candidate = line + " " + word;
			if(mText.measureText(candidate) <= avail)
			{
				line.setLength(0);
				line.append(candidate);
			}
			else
			{
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if(line.length() > 0)
			out.add(line.toString());

		// Two lines is the most a fan slot or a drawer item can carry legibly.
		if(out.size() > 2)
			return null;
		return out.toArray(new String[out.size()]);
	}
}
