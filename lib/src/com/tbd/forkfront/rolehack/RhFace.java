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
		if(mShape == Shape.RECT)
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
		mFittedSizePx = RhTheme.dp(getContext(), mLabelSizeDp);

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
		float avail = w - 2 * mBorderW - RhTheme.dp(getContext(), 3f)
		              - RhTheme.dp(getContext(), mLabelPadRightDp);
		if(mShape == Shape.CIRCLE)
			avail *= 0.86f;
		else if(mShape == Shape.PATH)
			avail *= 0.72f; // silhouettes are narrower than their box wherever text sits
		else if(mShape == Shape.LENS)
			avail *= 0.8f;  // the eye is full width only exactly on its centreline
		if(avail <= 0)
		{
			mLabelLines = new String[] { text };
			return;
		}

		mText.setTypeface(mLabelInOutfit ? RhTheme.outfitSemi(getContext()) : RhTheme.monoBold(getContext()));
		setTracking(mText, mLabelTracking);

		float size = mFittedSizePx;
		float minSize = RhTheme.dp(getContext(), 6f);
		while(true)
		{
			mText.setTextSize(size);
			String[] lines = wrap(text, avail);
			if(lines != null || size <= minSize)
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
