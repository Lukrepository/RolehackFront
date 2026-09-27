package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * A command group's full contents.
 *
 * The fan carries the four commands worth a dedicated face; the drawer carries
 * everything else in the group.  It is the one place in the interface where
 * reading beats scanning, so the item word is set in Outfit while its raw key
 * stays in the mono.
 *
 * Geometry: 604dp wide, top 58dp, max height 342dp, a four-column grid with a
 * 6dp gap and 46dp items.  Long groups scroll -- World has 21 commands.
 */
public class RhDrawer extends FrameLayout
{
	public interface Listener
	{
		void onItem(RhCommands.Item item, View from);
		/**
		 * Long-press a row to pick the command up for pinning.  This is the handoff's
		 * own suggested fix for the one hole in the mechanic: the pickup source is
		 * otherwise the fan, so a command that ships pinned but is not a fan node --
		 * Swap and Two-weapon on ATTACK are exactly this case -- could be cleared and
		 * never restored.  It also gives pinning a discoverable home.
		 */
		void onItemPin(RhCommands.Item item);
		/** DEFAULTS, shown while assigning: put the keys back as they shipped. */
		void onRestoreDefaults();
		void onDismiss();
	}

	private static final float WIDTH      = 604f;
	private static final float TOP        = 58f;
	private static final float MAX_HEIGHT = 342f;
	private static final float PADDING    = 10f;
	private static final float GAP        = 6f;
	private static final float ITEM_H     = 46f;
	private static final int   COLUMNS    = 4;
	/** A section heading's row, and the space above one that follows keys. */
	private static final float HEADING_H   = 20f;
	private static final float HEADING_GAP = 6f;
	/** Portrait: 604dp does not fit a phone's width, so three columns in 420. */
	private static final float NARROW_WIDTH      = 420f;
	private static final float NARROW_MAX_HEIGHT = 520f;
	private static final int   NARROW_COLUMNS    = 3;
	private static final float TITLE_H    = 32f;

	private final Context mContext;
	private final Listener mListener;
	private final View mScrim;
	private final Panel mPanel;
	private final TitleBar mTitle;
	private final LinearLayout mGrid;
	private final int mColumns;
	/**
	 * ASSIGN, in the title bar (Lucas, 2026-09-24).  While it is lit, tapping a
	 * command picks it up for pinning instead of running it -- the long press
	 * did that all along, but nothing on screen said so.
	 */
	private final RhFace mAssign;
	/** Beside ASSIGN while it is lit (Lucas, 2026-09-26): restore the shipped keys. */
	private final RhFace mDefaults;
	private boolean mAssigning;
	private String mCount = "";

	// ____________________________________________________________________________________
	public RhDrawer(Context context, Listener listener)
	{
		this(context, false, listener);
	}

	/** narrow: portrait, where the drawer takes three columns in NARROW_WIDTH. */
	public RhDrawer(Context context, boolean narrow, Listener listener)
	{
		super(context);
		mContext = context;
		mListener = listener;
		mColumns = narrow ? NARROW_COLUMNS : COLUMNS;

		// The scrim covers the map only, not the header band -- except in the
		// terminal style, which has no header band, so it covers everything.
		mScrim = new View(context);
		mScrim.setBackgroundColor(RhTheme.MODAL_SCRIM);
		LayoutParams scrimLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		if(!RhTheme.terminal())
			scrimLp.topMargin = RhTheme.rawDpi(context, RhTheme.HEADER_HEIGHT);
		addView(mScrim, scrimLp);
		mScrim.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mListener.onDismiss();
			}
		});

		mPanel = new Panel(context);
		LayoutParams panelLp = new LayoutParams(RhTheme.dpi(context, narrow ? NARROW_WIDTH : WIDTH),
		                                        LayoutParams.WRAP_CONTENT);
		panelLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
		panelLp.topMargin = RhTheme.dpi(context, TOP);
		addView(mPanel, panelLp);

		mPanel.setOrientation(LinearLayout.VERTICAL);

		// Title bar: the group's name, and ASSIGN at its right end.
		FrameLayout titleRow = new FrameLayout(context);
		mTitle = new TitleBar(context);
		titleRow.addView(mTitle, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		mAssign = new RhFace(context).radius(3f).label("ASSIGN", 8.5f, 0.08f);
		FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
				RhTheme.dpi(context, 84f), RhTheme.dpi(context, TITLE_H - 6f));
		alp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
		alp.rightMargin = RhTheme.dpi(context, 4f);
		titleRow.addView(mAssign, alp);
		mDefaults = new RhFace(context).radius(3f).label("DEFAULTS", 8.5f, 0.08f);
		FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
				RhTheme.dpi(context, 76f), RhTheme.dpi(context, TITLE_H - 6f));
		dlp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
		dlp.rightMargin = RhTheme.dpi(context, 4f + 84f + 6f);
		titleRow.addView(mDefaults, dlp);
		mDefaults.setVisibility(GONE);
		mDefaults.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				RhFeedback.press(v);
				mListener.onRestoreDefaults();
			}
		});
		mAssign.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				RhFeedback.press(v);
				setAssigning(!mAssigning);
			}
		});
		mPanel.addView(titleRow, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, RhTheme.dpi(context, TITLE_H)));

		ScrollView scroll = new ScrollView(context);
		mGrid = new LinearLayout(context);
		mGrid.setOrientation(LinearLayout.VERTICAL);
		int pad = RhTheme.dpi(context, PADDING);
		mGrid.setPadding(pad, pad, pad, pad);
		scroll.addView(mGrid, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		mPanel.addView(scroll, scrollLp);
		mPanel.setMaxHeightDp(narrow ? NARROW_MAX_HEIGHT : MAX_HEIGHT);
	}

	// ____________________________________________________________________________________
	/**
	 * @param items the group's own commands, plus any wizard-mode additions the
	 *              caller has appended -- the drawer does not decide what is in it
	 */
	public void show(RhCommands.Group group, RhCommands.Item[] items)
	{
		int commands = 0;
		for(RhCommands.Item it : items)
			if(!it.heading)
				commands++;
		mCount = commands + " commands";
		setAssigning(false);
		mTitle.set(group.title, mCount);
		mGrid.removeAllViews();

		int gap = RhTheme.dpi(mContext, GAP);
		int itemH = RhTheme.dpi(mContext, ITEM_H);
		LinearLayout row = null;
		// the column the next key goes in; a heading starts the next row afresh
		int col = 0;

		for(int i = 0; i < items.length; i++)
		{
			final RhCommands.Item item = items[i];
			if(item.heading)
			{
				padRow(row, col, gap, itemH);
				row = null;
				col = 0;
				Heading h = new Heading(mContext, item.word);
				LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, RhTheme.dpi(mContext, HEADING_H));
				if(mGrid.getChildCount() > 0)
					hLp.topMargin = RhTheme.dpi(mContext, HEADING_GAP);
				mGrid.addView(h, hLp);
				continue;
			}
			if(col == 0)
			{
				row = new LinearLayout(mContext);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, itemH);
				if(mGrid.getChildCount() > 0)
					rowLp.topMargin = gap;
				mGrid.addView(row, rowLp);
			}

			final RhFace f = new RhFace(mContext)
					.face(item.face != null ? item.face : RhTheme.G90)
					.radius(4f)
					.uppercase(false)
					.outfitLabel(true)
					.label(item.word, 11f, 0f);
			// A tag ("BETA") takes the corner the raw key would have.
			if(item.tag != null)
				f.tag(item.tag);
			else
				f.sub(item.key, 9f, RhTheme.RAW_KEY_DIM, 1f);

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, itemH, 1f);
			if(col != 0)
				lp.leftMargin = gap;
			row.addView(f, lp);
			col = (col + 1) % mColumns;

			f.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					RhFeedback.press(v);
					if(mAssigning)
						mListener.onItemPin(item);
					else
						mListener.onItem(item, f);
				}
			});
			f.setOnLongClickListener(new OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					RhFeedback.held(v);
					mListener.onItemPin(item);
					return true;
				}
			});
		}

		padRow(row, col, gap, itemH);

		setVisibility(VISIBLE);
	}

	/** Pad a part-filled row, so three keys do not stretch across four columns. */
	private void padRow(LinearLayout row, int col, int gap, int itemH)
	{
		if(row == null || col == 0)
			return;
		for(int i = col; i < mColumns; i++)
		{
			View spacer = new View(mContext);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, itemH, 1f);
			lp.leftMargin = gap;
			row.addView(spacer, lp);
		}
	}

	/**
	 * A section's heading across the drawer (Lucas, 2026-09-26): its name in
	 * small capitals, the lettering of the drawer's title bar, and a hairline
	 * running on to the right edge.
	 */
	private static final class Heading extends View
	{
		private final String mText;
		private final android.graphics.Paint mPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

		Heading(Context c, String text)
		{
			super(c);
			mText = text.toUpperCase(java.util.Locale.ROOT);
			mPaint.setTypeface(RhTheme.capFont(c));
			mPaint.setTextSize(RhTheme.dp(c, 9.5f));
			if(android.os.Build.VERSION.SDK_INT >= 21)
				mPaint.setLetterSpacing(0.16f);
			setContentDescription(text);
		}

		@Override
		protected void onDraw(Canvas canvas)
		{
			float h = getHeight();
			android.graphics.Paint.FontMetrics fm = mPaint.getFontMetrics();
			float base = h - RhTheme.dp(getContext(), 4f) - fm.descent;
			mPaint.setColor(0x9effffff);
			canvas.drawText(mText, RhTheme.dp(getContext(), 2f), base, mPaint);
			float x = RhTheme.dp(getContext(), 2f) + mPaint.measureText(mText) + RhTheme.dp(getContext(), 8f);
			float y = base - (fm.ascent + fm.descent) / 2f;
			mPaint.setColor(0x33ffffff);
			canvas.drawRect(x, y, getWidth(), y + Math.max(1f, RhTheme.dp(getContext(), 1f)), mPaint);
		}
	}

	public void hide()
	{
		setVisibility(GONE);
		mGrid.removeAllViews();
		mAssigning = false;
	}

	/**
	 * Opened by an empty pin key's long press (Lucas, 2026-09-24): the drawer
	 * comes up already assigning, and says what the pick is for.
	 */
	public void promptAssign(String prompt)
	{
		setAssigning(true);
		mTitle.setCount(prompt);
	}

	private void setAssigning(boolean on)
	{
		mAssigning = on;
		mAssign.face(on ? RhTheme.A90 : RhTheme.G90)
		       .textColor(on ? RhTheme.BADGE_TEXT : RhTheme.TEXT)
		       .label(on ? "ASSIGNING" : "ASSIGN", 8.5f, 0.08f);
		mTitle.setCount(on ? "tap a command to pin it" : mCount);
		mDefaults.setVisibility(on ? VISIBLE : GONE);
	}

	// ____________________________________________________________________________________
	/** The drawer body: #080c16, 2dp white border, 5dp radius, height-capped. */
	private static class Panel extends LinearLayout
	{
		private final Paint mFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF mRect   = new RectF();
		private final Path mClip    = new Path();
		private final float mBorderW;
		private final float mRadius;
		private int mMaxHeightPx = Integer.MAX_VALUE;

		Panel(Context c)
		{
			super(c);
			setWillNotDraw(false);
			mBorderW = RhTheme.dp(c, RhTheme.FACE_BORDER_W);
			mRadius  = RhTheme.dp(c, RhTheme.DRAWER_RADIUS);
			mFill.setColor(RhTheme.DRAWER_BG);
			mStroke.setColor(RhTheme.FACE_BORDER);
			mStroke.setStyle(Paint.Style.STROKE);
			mStroke.setStrokeWidth(mBorderW);
		}

		void setMaxHeightDp(float dp)
		{
			mMaxHeightPx = RhTheme.dpi(getContext(), dp);
			requestLayout();
		}

		/*
		 * Cap the height before the children are measured.  Clamping only our
		 * own measured size afterwards left the ScrollView measured at its full
		 * content height: it had nothing to scroll, and the rows past the cap
		 * were simply clipped (Lucas, 2026-09-25: the drawers do not scroll --
		 * GAME's last row and the wizard-mode extras were unreachable).
		 */
		@Override
		protected void onMeasure(int widthSpec, int heightSpec)
		{
			int cap = mMaxHeightPx;
			if(MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
				cap = Math.min(cap, MeasureSpec.getSize(heightSpec));
			super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
		}

		@Override
		protected void onSizeChanged(int w, int h, int ow, int oh)
		{
			super.onSizeChanged(w, h, ow, oh);
			mRect.set(mBorderW / 2f, mBorderW / 2f, w - mBorderW / 2f, h - mBorderW / 2f);
			mClip.reset();
			mClip.addRoundRect(new RectF(0, 0, w, h), mRadius, mRadius, Path.Direction.CW);
		}

		@Override
		protected void dispatchDraw(Canvas canvas)
		{
			canvas.drawRoundRect(mRect, mRadius, mRadius, mFill);
			canvas.save();
			canvas.clipPath(mClip);
			super.dispatchDraw(canvas);
			canvas.restore();
			canvas.drawRoundRect(mRect, mRadius, mRadius, mStroke);
		}
	}

	// ____________________________________________________________________________________
	/** Title bar: the group title and a command count, over a blue gradient. */
	private static class TitleBar extends View
	{
		private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mRule = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
		private String mTitle = "";
		private String mCount = "";
		private final float mPad;
		private final float mRuleW;

		TitleBar(Context c)
		{
			super(c);
			mPad = RhTheme.dp(c, 12f);
			mRuleW = RhTheme.dp(c, RhTheme.HEADER_RULE_W);
			mRule.setColor(RhTheme.HEADER_RULE);
			mRule.setStrokeWidth(mRuleW);
			mText.setTypeface(RhTheme.monoBold(c));
			mText.setTextAlign(Paint.Align.LEFT);
		}

		void set(String title, String count)
		{
			mTitle = title;
			mCount = count;
			invalidate();
		}

		void setCount(String count)
		{
			mCount = count;
			invalidate();
		}

		@Override
		protected void onDraw(Canvas canvas)
		{
			int w = getWidth(), h = getHeight();
			mFill.setShader(new LinearGradient(0, h, 0, 0,
					RhTheme.DRAWER_TITLE_BAR[0], RhTheme.DRAWER_TITLE_BAR[1], Shader.TileMode.CLAMP));
			canvas.drawRect(0, 0, w, h, mFill);
			canvas.drawLine(0, h - mRuleW / 2f, w, h - mRuleW / 2f, mRule);

			mText.setColor(RhTheme.TEXT);
			mText.setTextSize(RhTheme.dp(getContext(), 11f));
			Paint.FontMetrics fm = mText.getFontMetrics();
			float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
			canvas.drawText(mTitle, mPad, baseline, mText);
			float titleW = mText.measureText(mTitle);

			mText.setTypeface(RhTheme.monoRegular(getContext()));
			mText.setColor(0x73ffffff);
			mText.setTextSize(RhTheme.dp(getContext(), 9f));
			canvas.drawText(mCount, mPad + titleW + RhTheme.dp(getContext(), 10f), baseline, mText);
			mText.setTypeface(RhTheme.monoBold(getContext()));
		}
	}
}
