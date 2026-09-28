package com.tbd.forkfront.rolehack;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.TransformationMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.tbd.forkfront.MenuItem;
import com.tbd.forkfront.MenuSelectMode;
import com.tbd.forkfront.NH_TextView;
import com.tbd.forkfront.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The terminal styles' dialogs (Lucas, 2026-09-26), after the web port's
 * windows.  ForkFront's dialogs are stock layouts (dialog_menu*, dialog_text,
 * dialog_getline, dialog_question_*, amount_selector, textwindow); each one is
 * restyled here as it is inflated into the dialog frame (Util.inflate), from
 * the overlay's own parts:
 *
 *   The game's output -- a menu, a text window, a question, a line to type --
 *   comes up on a second monitor: the case's hood as its bezel, round a glass
 *   like the map's (RhScreen's tube: scanlines, vignette and glare), its text
 *   in the screen font and phosphor.  A menu reads as tty draws it: "a - item",
 *   headings in inverse video, a marked item's letter and "+" in amber.  An
 *   item keeps the game's colour under every phosphor, so menu colours show:
 *   they are the player's own rules, and they carry what the phosphor only
 *   dresses (Lucas, 2026-09-27: blessed, uncursed and cursed had gone from the
 *   inventory under the amber phosphor).  The phosphor is plain text's colour.
 *
 *   The buttons are keycaps -- RhFace.drawKeycap's recipe as a Drawable, since
 *   the dialogs' code holds them as Buttons -- and the one Enter would press
 *   is lit amber.  Their front skirts stay blank: on this interface the skirt
 *   says what a hold does, and these keys have no hold.
 *
 * The classic style is left exactly as ForkFront draws it.
 */
public final class RhDialogSkin
{
	private RhDialogSkin() {}

	/** A title on the glass: the prompt, as the message line's questions are. */
	static final int TITLE = 0xffffc166;
	/** A marked menu item's letter, mark and count. */
	private static final int MARK = 0xffffb347;
	/** A marked menu item's row. */
	private static final int MARK_WASH = 0x21ffb347;

	// ____________________________________________________________________________________
	/** Restyle a dialog just inflated into the dialog frame. */
	public static void apply(View root)
	{
		if(root == null || !RhTheme.terminal())
			return;
		if(root instanceof ScrollView)
		{
			textWindow((ScrollView)root);
			return;
		}
		if(!(root instanceof ViewGroup) || ((ViewGroup)root).getChildCount() == 0)
			return;
		View first = ((ViewGroup)root).getChildAt(0);
		if(!(first instanceof LinearLayout))
			return;

		Context c = root.getContext();
		root.setBackgroundColor(RhTheme.MODAL_SCRIM);
		// the bezel's rounded corners clear the screen's edges
		int m = px(c, 10f);
		root.setPadding(m, m, m, m);
		LinearLayout box = (LinearLayout)first;
		box.setBackground(new Bezel(c));
		box.setPadding(px(c, 12f), px(c, 12f), px(c, 12f), px(c, 10f));
		// a shadow under the bezel; caseless, it would show through the smoked glass
		box.setElevation(RhTheme.caseless() ? 0f : px(c, 14f));
		// keycaps drop when pressed; let the drop show above a row's top
		box.setClipChildren(false);
		box.setClipToPadding(false);
		LinearLayout glass = wrapGlass(box);
		style(box, glass, false);
	}

	/** The full-screen text window (NHW_TEXT: help files and the like): all glass. */
	private static void textWindow(ScrollView scroll)
	{
		Context c = scroll.getContext();
		scroll.setBackground(new Glass(c, true));
		if(Build.VERSION.SDK_INT >= 23)
			scroll.setForeground(new Tube(c, true));
		View text = scroll.findViewById(R.id.text_view);
		if(text instanceof TextView)
		{
			TextView t = (TextView)text;
			onGlass(t, RhTheme.phosphorText());
			t.setPadding(px(c, 14f), px(c, 12f), px(c, 14f), px(c, 12f));
		}
	}

	/** The question's default answer: the key Enter presses, lit amber. */
	public static void markDefault(View button)
	{
		if(!RhTheme.terminal() || !(button instanceof Button) || !(button.getBackground() instanceof Keycap))
			return;
		Button b = (Button)button;
		Keycap k = (Keycap)b.getBackground();
		k.family(RhTheme.capFor(RhTheme.A90));
		b.setTextColor(legendColours(k.mCap));
	}

	/**
	 * Text for the glass: plain text takes the phosphor and the game's colours
	 * stay; inverse video becomes the phosphor's; dim becomes its dim.
	 */
	public static CharSequence glassText(CharSequence s)
	{
		if(!RhTheme.terminal() || !(s instanceof Spanned))
			return s;
		SpannableStringBuilder b = new SpannableStringBuilder(s);
		for(BackgroundColorSpan bg : b.getSpans(0, b.length(), BackgroundColorSpan.class))
		{
			int st = b.getSpanStart(bg), en = b.getSpanEnd(bg);
			b.removeSpan(bg);
			for(ForegroundColorSpan fg : b.getSpans(st, en, ForegroundColorSpan.class))
				b.removeSpan(fg);
			b.setSpan(new BackgroundColorSpan(RhTheme.phosphorText()), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			b.setSpan(new ForegroundColorSpan(RhTheme.GLASS_BG), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		for(ForegroundColorSpan fg : b.getSpans(0, b.length(), ForegroundColorSpan.class))
		{
			int col = fg.getForegroundColor();
			if(col == RhTheme.GLASS_BG)
				continue; // inverse video, set above
			int st = b.getSpanStart(fg), en = b.getSpanEnd(fg);
			if(RhTheme.isGameGrey(col))
			{
				b.removeSpan(fg);
				b.setSpan(new ForegroundColorSpan(RhTheme.phosphorDim()), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			else if(isPlain(col))
				b.removeSpan(fg);
		}
		return b;
	}

	/** An item with no colour of its own comes from the port as white (or -1). */
	private static boolean isPlain(int col)
	{
		return col == -1 || RhTheme.isGameWhite(col) || col == Color.BLACK;
	}

	// ____________________________________________________________________________________
	/** One menu row, after MenuItemAdapter has filled it: tty's "a - item" on the glass. */
	public static void menuRow(View row, MenuItem item, MenuSelectMode how)
	{
		if(!RhTheme.terminal() || row == null || item == null)
			return;
		boolean marked = how == MenuSelectMode.PickMany && item.isSelectable() && !item.isHeader()
				&& item.isSelected();
		row.setBackgroundColor(marked ? MARK_WASH : Color.TRANSPARENT);
		Context c = row.getContext();
		// A row as tall as its words, with room to tap an item; a line of plain
		// text packs close, as tty prints it.
		boolean tappable = item.isSelectable() && how != MenuSelectMode.PickNone;
		int pad = px(c, tappable || item.isHeader() ? 4f : 1f);
		row.setMinimumHeight(0);
		row.setPadding(px(c, 4f), pad, px(c, 4f), pad);

		TextView text = row.findViewById(R.id.item_text);
		TextView acc = row.findViewById(R.id.item_acc);
		TextView sub = row.findViewById(R.id.item_sub);
		TextView count = row.findViewById(R.id.item_count);
		View check = row.findViewById(R.id.item_check);

		if(item.isHeader())
		{
			// tty's heading: the words in inverse video
			String name = " " + item.getText().toString().trim() + " ";
			SpannableString s = new SpannableString(name);
			s.setSpan(new BackgroundColorSpan(RhTheme.phosphorText()), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			onGlass(text, RhTheme.GLASS_BG);
			text.setShadowLayer(0f, 0f, 0f, 0);
			text.setText(s);
		}
		else if(!item.isSelectable() && item.hasSubText())
		{
			// ForkFront splits a trailing "(...)" off every line; a line of text
			// keeps its words together
			onGlass(text, itemColour(item));
			SpannableStringBuilder b = new SpannableStringBuilder(glassText(item.getText()));
			b.append(" (").append(item.getSubText().toString()).append(")");
			text.setText(b);
		}
		else
		{
			onGlass(text, itemColour(item));
			text.setText(glassText(item.getText()));
		}
		// The letter on the first line of the words, as tty has it: both sit at
		// the top, on one baseline; the picture stays centred.
		if(text.getParent() instanceof LinearLayout)
		{
			LinearLayout words = (LinearLayout)text.getParent();
			words.setBaselineAlignedChildIndex(0);
			((LinearLayout.LayoutParams)words.getLayoutParams()).gravity = Gravity.TOP;
		}
		if(acc != null && acc.getLayoutParams() instanceof LinearLayout.LayoutParams)
			((LinearLayout.LayoutParams)acc.getLayoutParams()).gravity = Gravity.TOP;

		if(acc != null)
		{
			if(item.isSelectable() && !item.isHeader() && item.getAcc() != 0)
			{
				char mark = !marked ? '-' : item.getCount() > 0 ? '#' : '+';
				acc.setVisibility(View.VISIBLE);
				acc.setMinWidth(0);
				acc.setMaxWidth(Integer.MAX_VALUE);
				acc.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
				onGlass(acc, marked ? MARK : RhTheme.phosphorText());
				acc.setText(item.getAcc() + " " + mark + " ");
			}
			else if(!item.isHeader())
			{
				// a line of text in a menu: tty starts it at the margin
				acc.setVisibility(View.GONE);
			}
		}
		if(sub != null)
		{
			// no empty second line: a name alone centres on its picture
			boolean second = item.hasSubText() && item.isSelectable();
			sub.setVisibility(second ? View.VISIBLE : View.GONE);
			onGlass(sub, RhTheme.phosphorDim());
			if(second)
				sub.setText(item.getSubText().toString());
		}
		if(count != null)
			onGlass(count, MARK);
		if(check != null)
			check.setVisibility(View.GONE); // the mark says it
	}

	private static int itemColour(MenuItem item)
	{
		Spanned t = item.getText();
		ForegroundColorSpan[] fg = t.getSpans(0, t.length(), ForegroundColorSpan.class);
		int col = fg.length > 0 ? fg[0].getForegroundColor() : -1;
		if(RhTheme.isGameGrey(col))
			return RhTheme.phosphorDim();
		return isPlain(col) ? RhTheme.phosphorText() : col;
	}

	/** GetLine's history list: its lines on the glass. */
	public static <T> ArrayAdapter<T> historyAdapter(Context context, List<T> items)
	{
		if(!RhTheme.terminal())
			return new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, items);
		return new ArrayAdapter<T>(context, android.R.layout.simple_list_item_1, items)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View v = super.getView(position, convertView, parent);
				if(v instanceof TextView)
					onGlass((TextView)v, RhTheme.phosphorText());
				return v;
			}
		};
	}

	// ____________________________________________________________________________________
	// The monitor: whatever the dialog shows goes onto a glass inside the bezel;
	// its controls -- buttons, a slider, a checkbox -- stay on the bezel below.

	private static LinearLayout wrapGlass(LinearLayout box)
	{
		Context c = box.getContext();
		List<View> screen = new ArrayList<>();
		for(int i = 0; i < box.getChildCount(); i++)
		{
			View v = box.getChildAt(i);
			if(isControls(v))
				break;
			screen.add(v);
		}
		if(screen.isEmpty())
			return null;

		// The glass takes the place, and the stretch, of what it holds.
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		for(View v : screen)
		{
			ViewGroup.LayoutParams vlp = v.getLayoutParams();
			if(vlp instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams)vlp).weight > 0f)
			{
				lp.height = vlp.height;
				lp.weight = ((LinearLayout.LayoutParams)vlp).weight;
			}
		}

		LinearLayout glass = new LinearLayout(c);
		glass.setOrientation(LinearLayout.VERTICAL);
		int index = box.indexOfChild(screen.get(0));
		for(View v : screen)
		{
			ViewGroup.LayoutParams vlp = v.getLayoutParams();
			box.removeView(v);
			// a divider rule under the title: the glass is the frame now
			if(v instanceof ImageView && v.getId() == View.NO_ID)
				continue;
			glass.addView(v, vlp);
		}
		box.addView(glass, index, lp);
		glass.setBackground(new Glass(c, false));
		if(Build.VERSION.SDK_INT >= 23)
			glass.setForeground(new Tube(c, false));
		int p = px(c, Glass.RING + 2f);
		glass.setPadding(p, p, p, p);
		return glass;
	}

	/** A row that is the dialog's controls rather than what it shows. */
	private static boolean isControls(View v)
	{
		if(v instanceof CompoundButton || v instanceof SeekBar || v instanceof Button)
			return true;
		if(v instanceof ViewGroup && !(v instanceof AdapterView) && !(v instanceof ScrollView))
			return has(v, Button.class) && !has(v, EditText.class);
		return false;
	}

	private static boolean has(View v, Class<?> kind)
	{
		if(kind.isInstance(v))
			return true;
		if(v instanceof ViewGroup && !(v instanceof AdapterView))
		{
			ViewGroup g = (ViewGroup)v;
			for(int i = 0; i < g.getChildCount(); i++)
				if(has(g.getChildAt(i), kind))
					return true;
		}
		return false;
	}

	private static void style(View v, View glass, boolean onGlass)
	{
		Context c = v.getContext();
		if(v == glass)
			onGlass = true;
		if(v instanceof ListView)
		{
			list((ListView)v);
			return;
		}
		if(v instanceof ScrollView)
		{
			v.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
			if(Build.VERSION.SDK_INT >= 29)
				v.setVerticalScrollbarThumbDrawable(new ColorDrawable(RhTheme.phosphorDim()));
		}
		if(v instanceof ViewGroup && !(v instanceof AdapterView))
		{
			ViewGroup g = (ViewGroup)v;
			if(!onGlass && v.getBackground() != null && !(v.getBackground() instanceof Bezel))
			{
				// the stock button bar's own panel: the bezel shows instead
				v.setBackground(null);
				v.setPadding(0, px(c, 10f), 0, 0);
			}
			if(!onGlass)
			{
				// a pressed keycap drops a little below its row; the glass and
				// what scrolls on it keep their clipping
				g.setClipChildren(false);
				g.setClipToPadding(false);
			}
			for(int i = 0; i < g.getChildCount(); i++)
				style(g.getChildAt(i), glass, onGlass);
			return;
		}
		if(v instanceof CompoundButton)
		{
			CompoundButton cb = (CompoundButton)v;
			cb.setTextColor(RhTheme.lipText());
			cb.setTypeface(RhTheme.capFont(c));
			cb.setButtonTintList(ColorStateList.valueOf(MARK));
			return;
		}
		if(v instanceof Button)
		{
			if(v.getId() == R.id.history)
			{
				// GetLine's history: an icon, in the phosphor
				// (the icon is shared across the app: tint a copy)
				if(v.getBackground() != null)
					v.getBackground().mutate().setTint(RhTheme.phosphorText());
				return;
			}
			keycap((Button)v);
			return;
		}
		if(v instanceof EditText)
		{
			line((EditText)v);
			return;
		}
		if(v instanceof TextView)
		{
			TextView t = (TextView)v;
			if(onGlass)
				onGlass(t, t.getId() == R.id.title ? TITLE : RhTheme.phosphorText());
			else
			{
				t.setTextColor(RhTheme.lipText());
				t.setTypeface(RhTheme.capFont(c));
			}
			return;
		}
		if(v instanceof SeekBar)
		{
			SeekBar s = (SeekBar)v;
			s.setProgressTintList(ColorStateList.valueOf(MARK));
			s.setThumbTintList(ColorStateList.valueOf(MARK));
			s.setProgressBackgroundTintList(ColorStateList.valueOf(RhTheme.lipText()));
		}
	}

	private static void list(ListView l)
	{
		l.setDivider(null);
		l.setDividerHeight(0);
		StateListDrawable sel = new StateListDrawable();
		sel.addState(new int[] { android.R.attr.state_pressed },
				new ColorDrawable((RhTheme.phosphorText() & 0x00ffffff) | 0x26000000));
		sel.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
		l.setSelector(sel);
		if(Build.VERSION.SDK_INT >= 29)
			l.setVerticalScrollbarThumbDrawable(new ColorDrawable(RhTheme.phosphorDim()));
	}

	/** Text on the glass: the screen font, the phosphor, and its glow. */
	static void onGlass(TextView t, int colour)
	{
		Context c = t.getContext();
		if(t instanceof NH_TextView)
			((NH_TextView)t).setOnGlass(true);
		else if(t.getTypeface() != RhTheme.screenFont(c))
		{
			// once, even for a recycled list line: the size is scaled from the layout's own
			t.setTypeface(RhTheme.screenFont(c));
			t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * RhTheme.screenFontScale());
		}
		t.setTextColor(colour);
		t.setShadowLayer(RhTheme.rawDp(c, 3f), 0f, 0f, (colour & 0x00ffffff) | 0x80000000);
	}

	/** A line to type into: a small well of glass, the phosphor's caret. */
	private static void line(EditText e)
	{
		Context c = e.getContext();
		GradientDrawable well = new GradientDrawable();
		well.setColor(RhTheme.GLASS_BG);
		well.setCornerRadius(RhTheme.rawDp(c, 5f));
		well.setStroke(px(c, 1f), 0x33ffffff);
		e.setBackground(well);
		e.setPadding(px(c, 10f), px(c, 6f), px(c, 10f), px(c, 6f));
		e.setTypeface(RhTheme.screenFont(c));
		e.setTextSize(TypedValue.COMPLEX_UNIT_PX, e.getTextSize() * RhTheme.screenFontScale());
		e.setTextColor(RhTheme.phosphorText());
		e.setHintTextColor(RhTheme.phosphorDim());
		e.setHighlightColor((RhTheme.phosphorText() & 0x00ffffff) | 0x66000000);
		e.setShadowLayer(RhTheme.rawDp(c, 3f), 0f, 0f, (RhTheme.phosphorText() & 0x00ffffff) | 0x80000000);
		if(Build.VERSION.SDK_INT >= 29)
		{
			GradientDrawable caret = new GradientDrawable();
			caret.setColor(RhTheme.phosphorText());
			caret.setSize(px(c, 2f), 1);
			e.setTextCursorDrawable(caret);
			// the selection handles too, in the phosphor rather than the system's accent
			Drawable h = e.getTextSelectHandle();
			if(h != null)
				e.setTextSelectHandle(tinted(h));
			h = e.getTextSelectHandleLeft();
			if(h != null)
				e.setTextSelectHandleLeft(tinted(h));
			h = e.getTextSelectHandleRight();
			if(h != null)
				e.setTextSelectHandleRight(tinted(h));
		}
	}

	private static Drawable tinted(Drawable d)
	{
		Drawable t = d.mutate();
		t.setTint(RhTheme.phosphorText());
		return t;
	}

	// ____________________________________________________________________________________
	// Keycaps.

	private static final View.OnTouchListener FEEDBACK = new View.OnTouchListener()
	{
		@Override
		public boolean onTouch(View v, MotionEvent e)
		{
			if(!v.isEnabled())
				return false;
			if(e.getActionMasked() == MotionEvent.ACTION_DOWN)
				RhFeedback.press(v);
			else if(e.getActionMasked() == MotionEvent.ACTION_UP)
				RhFeedback.up(v);
			return false;
		}
	};

	/** Word labels in capitals, as the keys' are; a single letter is a key, and keeps its case. */
	private static final TransformationMethod LABEL = new TransformationMethod()
	{
		@Override
		public CharSequence getTransformation(CharSequence source, View view)
		{
			return source != null && source.length() > 1 ? source.toString().toUpperCase(Locale.ROOT) : source;
		}

		@Override
		public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction, Rect previouslyFocusedRect) {}
	};

	private static void keycap(Button b)
	{
		Context c = b.getContext();
		boolean ok = b.getId() == R.id.btn_ok || "ok".equalsIgnoreCase(b.getText().toString().trim());
		Keycap k = new Keycap(c, RhTheme.capFor(ok ? RhTheme.A90 : RhTheme.G90));
		b.setBackground(k);
		b.setStateListAnimator(k.animator(b));
		b.setElevation(0f);
		b.setAllCaps(false);
		b.setTransformationMethod(LABEL);
		b.setTypeface(RhTheme.capFont(c));
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
		b.setLetterSpacing(0.05f);
		b.setTextColor(legendColours(k.mCap));
		b.setGravity(Gravity.CENTER);
		b.setMinHeight(px(c, Keycap.HEIGHT));
		b.setMinimumHeight(px(c, Keycap.HEIGHT));
		b.setMinWidth(px(c, 52f));
		b.setMinimumWidth(px(c, 52f));
		// the legend centred on the top face, above the front of the skirt
		b.setPadding(px(c, 8f), px(c, 3f), px(c, 8f), px(c, Keycap.SHADOW + Keycap.front(Keycap.HEIGHT)));
		b.setOnTouchListener(FEEDBACK);
		if(Build.VERSION.SDK_INT >= 26)
			b.setDefaultFocusHighlightEnabled(false);
		ViewGroup.LayoutParams lp = b.getLayoutParams();
		if(lp instanceof ViewGroup.MarginLayoutParams)
		{
			ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams)lp;
			m.leftMargin = m.rightMargin = px(c, 4f);
			b.setLayoutParams(m);
		}
	}

	private static ColorStateList legendColours(int[] cap)
	{
		int legend = cap[RhTheme.CAP_LEGEND];
		return new ColorStateList(new int[][] { { -android.R.attr.state_enabled }, {} },
				new int[] { (legend & 0x00ffffff) | 0x73000000, legend });
	}

	/**
	 * RhFace.drawKeycap as a Drawable: the soft shadow and hard edge, the skirt
	 * lit from the left, the dished top face.  Pressed, the Button drops by the
	 * sink (its StateListAnimator, so the legend goes down with the face) and
	 * the skirt is drawn that much higher, so it stays put.
	 */
	static final class Keycap extends Drawable
	{
		static final float HEIGHT = 46f, SHADOW = 2.5f;

		private final Context mContext;
		private int[] mCap;
		private boolean mPressed, mEnabled = true;
		private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mBand = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF mSkirt = new RectF(), mTop = new RectF();

		Keycap(Context c, int[] cap)
		{
			mContext = c;
			mCap = cap;
			mShadow.setColor(0x73000000);
			mShadow.setMaskFilter(new BlurMaskFilter(RhTheme.rawDp(c, 3f), BlurMaskFilter.Blur.NORMAL));
		}

		void family(int[] cap) { mCap = cap; invalidateSelf(); }

		/** The front of the skirt, design dp, for a key this tall (RhFace's clamp). */
		static float front(float heightDp) { return Math.max(7f, Math.min(12f, heightDp * 0.2f)); }

		private float dp(float v) { return RhTheme.rawDp(mContext, v); }

		StateListAnimator animator(View v)
		{
			StateListAnimator s = new StateListAnimator();
			s.addState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled },
					ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, dp(3f)).setDuration(0));
			s.addState(new int[0], ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f).setDuration(0));
			return s;
		}

		@Override
		public void draw(Canvas canvas)
		{
			Rect b = getBounds();
			float w = b.width(), h = b.height();
			float hDp = h / mContext.getResources().getDisplayMetrics().density;
			float shadow = dp(SHADOW), front = dp(front(hDp)), side = Math.min(dp(4f), w / 12f);
			float r = Math.min(dp(6f), (h - shadow) / 5f), fr = Math.max(0f, r - dp(1f));
			// the view has dropped by dp(3) when pressed (animator); keep the skirt where it was
			float drop = mPressed ? dp(3f) : 0f;
			int alpha = mEnabled ? 255 : 115;

			mSkirt.set(b.left, b.top - drop, b.right, b.bottom - shadow - drop);
			canvas.save();
			canvas.translate(0f, dp(mPressed ? 1f : 2f));
			mShadow.setAlpha(alpha * 0x73 / 255);
			canvas.drawRoundRect(mSkirt, r, r, mShadow);
			mBand.setShader(null);
			mBand.setColor(0x8c000000);
			mBand.setAlpha(alpha * 0x8c / 255);
			canvas.drawRoundRect(mSkirt, r, r, mBand);
			canvas.restore();

			mFill.setShader(new LinearGradient(mSkirt.left, 0f, mSkirt.right, 0f,
					new int[] { mCap[RhTheme.CAP_SL], mCap[RhTheme.CAP_SM], mCap[RhTheme.CAP_SM], mCap[RhTheme.CAP_SR] },
					new float[] { 0f, 0.14f, 0.86f, 1f }, Shader.TileMode.CLAMP));
			mFill.setAlpha(alpha);
			canvas.drawRoundRect(mSkirt, r, r, mFill);
			mBand.setColor(0xff000000);
			mBand.setAlpha(alpha);
			mBand.setShader(new LinearGradient(0f, mSkirt.top, 0f, mSkirt.bottom,
					new int[] { 0x24ffffff, 0x00ffffff, 0x52000000 },
					new float[] { 0f, 0.3f, 1f }, Shader.TileMode.CLAMP));
			canvas.drawRoundRect(mSkirt, r, r, mBand);
			mBand.setShader(null);

			// The top face, where it sits in the (dropped) view.
			mTop.set(b.left + side, b.top + dp(2f), b.right - side, b.bottom - shadow - front);
			float fw = mTop.width(), fh = mTop.height();
			if(fw <= 0f || fh <= 0f)
				return;
			mFill.setShader(new RadialGradient(mTop.centerX(), mTop.top + fh * 0.15f,
					Math.max(fw, fh) * 0.95f, new int[] { mCap[RhTheme.CAP_T1], mCap[RhTheme.CAP_T2] },
					new float[] { 0f, 0.85f }, Shader.TileMode.CLAMP));
			mFill.setAlpha(alpha);
			canvas.drawRoundRect(mTop, fr, fr, mFill);
			mFill.setShader(null);
			mBand.setColor(mPressed ? 0x38ffffff : 0x66ffffff);
			canvas.drawRect(mTop.left + fr, mTop.top, mTop.right - fr, mTop.top + dp(1f), mBand);
		}

		@Override public boolean isStateful() { return true; }

		@Override
		protected boolean onStateChange(int[] state)
		{
			boolean pressed = false, enabled = false;
			for(int s : state)
			{
				if(s == android.R.attr.state_pressed)
					pressed = true;
				else if(s == android.R.attr.state_enabled)
					enabled = true;
			}
			pressed &= enabled;
			if(pressed == mPressed && enabled == mEnabled)
				return false;
			mPressed = pressed;
			mEnabled = enabled;
			invalidateSelf();
			return true;
		}

		@Override public void setAlpha(int alpha) {}
		@Override public void setColorFilter(ColorFilter cf) {}
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	// ____________________________________________________________________________________
	// The monitor's parts.

	/** The bezel: the case's hood, lit along its top; caseless, smoked glass with a rim. */
	static final class Bezel extends Drawable
	{
		private final Context mContext;
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF mRect = new RectF();

		Bezel(Context c) { mContext = c; }

		private float dp(float v) { return RhTheme.rawDp(mContext, v); }

		@Override
		public void draw(Canvas canvas)
		{
			mRect.set(getBounds());
			float r = dp(14f);
			mPaint.setShader(null);
			mPaint.setStyle(Paint.Style.FILL);
			if(RhTheme.caseless())
			{
				mPaint.setColor(0xd6070605);
				canvas.drawRoundRect(mRect, r, r, mPaint);
				mPaint.setStyle(Paint.Style.STROKE);
				mPaint.setStrokeWidth(dp(1f));
				mPaint.setColor(0x33ffffff);
				canvas.drawRoundRect(mRect, r, r, mPaint);
				return;
			}
			int[] hood = RhTheme.hoodBg();
			mPaint.setColor(0xff000000);
			mPaint.setShader(new LinearGradient(0f, mRect.top, 0f, mRect.bottom, hood[0], hood[1], Shader.TileMode.CLAMP));
			canvas.drawRoundRect(mRect, r, r, mPaint);
			mPaint.setShader(null);
			mPaint.setColor(0x24ffffff);
			canvas.drawRect(mRect.left + r, mRect.top, mRect.right - r, mRect.top + dp(1f), mPaint);
			mPaint.setColor(0x59000000);
			canvas.drawRect(mRect.left + r, mRect.bottom - dp(2f), mRect.right - r, mRect.bottom, mPaint);
		}

		@Override
		public void getOutline(Outline outline)
		{
			outline.setRoundRect(getBounds(), dp(14f));
		}

		@Override public void setAlpha(int alpha) {}
		@Override public void setColorFilter(ColorFilter cf) {}
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	/** The glass, in its dark bezel ring (RhCase's tube ring); the ring is inside the bounds. */
	static final class Glass extends Drawable
	{
		static final float RING = 4f, RADIUS = 7f;

		private final Context mContext;
		private final boolean mFull;
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF mRect = new RectF();

		/** full: a whole screen of glass, no ring. */
		Glass(Context c, boolean full) { mContext = c; mFull = full; }

		private float dp(float v) { return RhTheme.rawDp(mContext, v); }

		@Override
		public void draw(Canvas canvas)
		{
			mPaint.setStyle(Paint.Style.FILL);
			if(mFull)
			{
				mPaint.setColor(RhTheme.caseless() ? 0xf2090d0a : RhTheme.GLASS_BG);
				canvas.drawRect(getBounds(), mPaint);
				return;
			}
			mRect.set(getBounds());
			mRect.inset(dp(RING), dp(RING));
			float r = dp(RADIUS);
			if(RhTheme.caseless())
			{
				mPaint.setColor(0xe6090d0a);
				canvas.drawRoundRect(mRect, r, r, mPaint);
				return;
			}
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeWidth(dp(3f));
			mPaint.setColor(0xff110e0c);
			RectF ring = new RectF(mRect);
			ring.inset(-dp(1.5f), -dp(1.5f));
			canvas.drawRoundRect(ring, r + dp(1.5f), r + dp(1.5f), mPaint);
			mPaint.setStrokeWidth(dp(1f));
			mPaint.setColor(0x12ffffff);
			ring.inset(-dp(2f), -dp(2f));
			canvas.drawRoundRect(ring, r + dp(3.5f), r + dp(3.5f), mPaint);
			mPaint.setStyle(Paint.Style.FILL);
			mPaint.setColor(RhTheme.GLASS_BG);
			canvas.drawRoundRect(mRect, r, r, mPaint);
		}

		@Override public void setAlpha(int alpha) {}
		@Override public void setColorFilter(ColorFilter cf) {}
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	/** The tube over the glass (RhScreen's): scanlines, then a vignette and a faint glare. */
	static final class Tube extends Drawable
	{
		private final Context mContext;
		private final boolean mFull;
		private final Paint mScan = new Paint();
		private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF mRect = new RectF();
		private final Path mClip = new Path();

		Tube(Context c, boolean full)
		{
			mContext = c;
			mFull = full;
			int period = Math.max(3, Math.round(RhTheme.rawDp(c, 3f)));
			Bitmap b = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
			b.setPixel(0, 0, 0x3d000000);
			mScan.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
		}

		private float dp(float v) { return RhTheme.rawDp(mContext, v); }

		@Override
		public void draw(Canvas canvas)
		{
			if(RhTheme.caseless())
				return;
			mRect.set(getBounds());
			if(!mFull)
				mRect.inset(dp(Glass.RING), dp(Glass.RING));
			float w = mRect.width(), h = mRect.height();
			canvas.save();
			if(!mFull)
			{
				mClip.reset();
				mClip.addRoundRect(mRect, dp(Glass.RADIUS), dp(Glass.RADIUS), Path.Direction.CW);
				canvas.clipPath(mClip);
			}
			canvas.drawRect(mRect, mScan);
			mFill.setColor(0xff000000);
			mFill.setShader(new RadialGradient(mRect.centerX(), mRect.top + h * 0.45f, Math.max(w, h) * 0.62f,
					new int[] { 0x00000000, 0x00000000, 0x73000000 },
					new float[] { 0f, 0.7f, 1f }, Shader.TileMode.CLAMP));
			canvas.drawRect(mRect, mFill);
			mFill.setShader(new RadialGradient(mRect.left + w * 0.28f, mRect.top + h * 0.06f, Math.max(w, h) * 0.5f,
					0x10ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
			canvas.drawRect(mRect, mFill);
			mFill.setShader(null);
			canvas.restore();
		}

		@Override public void setAlpha(int alpha) {}
		@Override public void setColorFilter(ColorFilter cf) {}
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	private static int px(Context c, float dp)
	{
		return Math.round(RhTheme.rawDp(c, dp));
	}
}
