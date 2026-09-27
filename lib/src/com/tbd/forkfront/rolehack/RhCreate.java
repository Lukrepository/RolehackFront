package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tbd.forkfront.MenuItem;
import com.tbd.forkfront.R;
import com.tbd.forkfront.Tileset;

import java.util.ArrayList;
import java.util.List;

/**
 * Character creation as keys (Lucas, 2026-09-27: it "should actually feel
 * punchy", and it went by too fast).
 *
 * Player selection is the core's own (role.c, genl_player_setup()), and the
 * window port marks its pick-one menus (winandroid.c, rh_creation()): role,
 * race, gender and alignment, and "Is this ok?".  Each is still an ordinary
 * menu -- the same entries, letters and answers -- so a keyboard types them
 * exactly as before; nothing here waits or animates.  On the mobile interface
 * the entries are drawn as keys instead of list rows: RhFace keycaps with the
 * overlay's own tap (RhFace.onTap), set out on the dialog's bezel the way a
 * keyboard sits under a screen.  The choices carry their pictures; vanilla's
 * other entries -- Random, "Pick race first" and its kin, the role filter,
 * Quit -- are a row of smaller keys under them.  The glass above keeps the
 * menu's title and vanilla's line of the character so far; at "Is this ok?"
 * it shows the hero.
 */
public final class RhCreate
{
	private RhCreate() {}

	public static final int NONE = 0, PICK = 1, CONFIRM = 2;

	public interface Listener
	{
		void onPick(MenuItem item);
	}

	private static final float GAP = 8f;
	private static final float KEY_W = 128f, KEY_W_NARROW = 124f, KEY_W_ANSWER = 164f, KEY_W_EXTRA = 112f;
	private static final float KEY_H = 78f, KEY_H_PLAIN = 50f, KEY_H_EXTRA = 44f;
	private static final float HERO = 96f;

	/** What the core said about the next menu, until a menu takes it. */
	private static int sStep = NONE, sHeroTile = -1, sTone;

	/** From the core: the next pick-one menu is this step of character creation. */
	public static void next(int step, int heroTile, int tone)
	{
		sStep = step;
		sHeroTile = heroTile;
		sTone = tone;
	}

	/**
	 * The step for the menu about to be shown, once: { step, hero tile, tone },
	 * or null for any other menu.  The menu keeps it, so a rotation that
	 * rebuilds the dialog dresses it again.
	 */
	public static int[] take()
	{
		if(sStep == NONE)
			return null;
		int[] step = { sStep, sHeroTile, sTone };
		sStep = NONE;
		return step;
	}

	// ____________________________________________________________________________________
	/**
	 * Dress a pick-one menu dialog, just inflated and skinned by RhDialogSkin,
	 * as keys.  Returns false and leaves the list where there is no skin to
	 * dress (the classic style), so the menu still works as ForkFront's.
	 */
	public static boolean dress(View root, List<MenuItem> items, Tileset tiles, int[] step,
	                            final Listener listener)
	{
		if(root == null || step == null || !RhTheme.terminal())
			return false;
		View found = root.findViewById(R.id.menu_list);
		if(!(found instanceof ListView))
			return false;
		ListView list = (ListView)found;
		ViewParent gp = list.getParent();
		if(!(gp instanceof LinearLayout) || !(gp.getParent() instanceof LinearLayout))
			return false;
		final LinearLayout glass = (LinearLayout)gp;
		LinearLayout box = (LinearLayout)gp.getParent();
		final Context c = root.getContext();
		final boolean confirm = step[0] == CONFIRM;

		int picks = 0;
		boolean pictures = false;
		for(MenuItem item : items)
		{
			if(!item.isSelectable())
				continue;
			picks++;
			if(item.getTile() >= 0)
				pictures = true;
		}
		if(picks == 0)
			return false;

		// The list gives way to the keys; the glass holds only what it says.
		list.setVisibility(View.GONE);
		LinearLayout.LayoutParams glp = (LinearLayout.LayoutParams)glass.getLayoutParams();
		glp.weight = 0f;
		glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		glass.setLayoutParams(glp);

		if(confirm && step[1] >= 0)
		{
			Bitmap hero = tileBitmap(tiles, step[1], step[2]);
			if(hero != null)
			{
				Picture p = new Picture(c, hero);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, RhTheme.dpi(c, HERO + 8f));
				glass.addView(p, lp);
			}
		}

		// Vanilla's words that are not choices go on the glass: the character so
		// far (plsel_startmenu) and what a pick forces ("race forces neutral").
		for(MenuItem item : items)
		{
			if(item.isSelectable())
				continue;
			TextView t = new TextView(c);
			t.setText(item.getName().trim());
			t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
			t.setGravity(confirm ? Gravity.CENTER_HORIZONTAL : Gravity.START);
			int pad = RhTheme.dpi(c, 12f);
			t.setPadding(pad, 0, pad, RhTheme.dpi(c, 8f));
			RhDialogSkin.onGlass(t, RhTheme.phosphorText());
			glass.addView(t, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}

		// The keys.  With pictures, the pictured choices are the big keys and
		// everything else is the row of small ones under them.
		boolean split = !confirm && pictures;
		final List<RhFace> main = new ArrayList<RhFace>();
		final List<RhFace> extra = new ArrayList<RhFace>();
		for(final MenuItem item : items)
		{
			if(!item.isSelectable())
				continue;
			boolean small = split && item.getTile() < 0;
			RhFace key = new RhFace(c)
					.cap(family(item, confirm, small))
					.radius(4f)
					.label(keyLabel(item), small ? 8f : 11f, 0f);
			if(item.hasAcc())
				key.sub(String.valueOf(item.getAcc()), 9f, RhTheme.RAW_KEY_DIM, 1f);
			if(item.getTile() >= 0)
			{
				Bitmap b = tileBitmap(tiles, item.getTile(), step[2]);
				if(b != null)
					key.icon(b);
			}
			key.setContentDescription(item.getName());
			key.onTap(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onPick(item);
				}
			});
			(small ? extra : main).add(key);
		}

		final LinearLayout grid = new LinearLayout(c);
		grid.setOrientation(LinearLayout.VERTICAL);
		// The scroll clips.  A keycap sinks inside its own skirt, so nothing has
		// to draw past its box -- and rows scrolled up must not draw over the
		// glass (Lucas, 2026-09-27: in landscape the roles covered the title).
		// It is as tall as its keys, or as the dialog has room for: the bezel
		// measures the glass first and the scroll gets what is left.
		final ScrollView scroll = new ScrollView(c);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.addView(grid, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		slp.gravity = Gravity.CENTER_HORIZONTAL;
		slp.topMargin = RhTheme.dpi(c, 10f);
		box.addView(scroll, box.indexOfChild(glass) + 1, slp);

		// Five to a row in landscape and three in portrait, never wider than the
		// screen.  The activity keeps itself through a rotation, so this dialog
		// is not built again when the phone turns: the keys re-flow whenever the
		// dialog's size changes.
		final float mainH = pictures ? KEY_H : KEY_H_PLAIN;
		final View dialog = root;
		final Runnable flow = new Runnable()
		{
			private int mW = -1, mH = -1;

			@Override
			public void run()
			{
				DisplayMetrics dm = c.getResources().getDisplayMetrics();
				int w = dialog.getWidth(), h = dialog.getHeight();
				if(w <= 0 || h <= 0)
				{
					w = dm.widthPixels;
					h = dm.heightPixels;
				}
				if(w == mW && h == mH)
					return;
				mW = w;
				mH = h;
				float wDp = w / dm.density, hDp = h / dm.density;
				boolean narrow = hDp > wDp;
				// the dialog's own margins: 10dp round the bezel, 12dp inside it
				float avail = wDp - 2 * 10f - 2 * 12f;
				grid.removeAllViews();
				float width = flowKeys(c, grid, main, confirm ? main.size() : narrow ? 3 : 5,
						narrow ? KEY_W_NARROW : confirm ? KEY_W_ANSWER : KEY_W, mainH, avail);
				width = Math.max(width, flowKeys(c, grid, extra, narrow ? 3 : 6,
						narrow ? KEY_W_NARROW : KEY_W_EXTRA, KEY_H_EXTRA, avail));
				ViewGroup.LayoutParams lp = scroll.getLayoutParams();
				lp.width = RhTheme.dpi(c, width);
				scroll.setLayoutParams(lp);
			}
		};
		flow.run();
		root.addOnLayoutChangeListener(new View.OnLayoutChangeListener()
		{
			@Override
			public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob)
			{
				if(r - l != or - ol || b - t != ob - ot)
					v.post(flow);
			}
		});
		return true;
	}

	/**
	 * Add one group of keys to the grid in centred rows, at most maxCols to a
	 * row and each key at most widest dp.  Returns the widest row, dp.
	 */
	private static float flowKeys(Context c, LinearLayout grid, List<RhFace> keys, int maxCols,
	                              float widest, float keyH, float avail)
	{
		int n = keys.size();
		if(n == 0)
			return 0f;
		int cols = Math.min(n, maxCols);
		float keyW = Math.min(widest, (avail - (cols - 1) * GAP) / cols);

		LinearLayout row = null;
		for(int i = 0; i < n; i++)
		{
			RhFace key = keys.get(i);
			if(key.getParent() instanceof ViewGroup)
				((ViewGroup)key.getParent()).removeView(key);
			if(i % cols == 0)
			{
				row = new LinearLayout(c);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT, RhTheme.dpi(c, keyH));
				rlp.gravity = Gravity.CENTER_HORIZONTAL;
				if(grid.getChildCount() > 0)
					rlp.topMargin = RhTheme.dpi(c, GAP);
				grid.addView(row, rlp);
			}
			LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
					RhTheme.dpi(c, keyW), ViewGroup.LayoutParams.MATCH_PARENT);
			if(i % cols != 0)
				klp.leftMargin = RhTheme.dpi(c, GAP);
			row.addView(key, klp);
		}
		return cols * keyW + (cols - 1) * GAP;
	}

	/**
	 * What a key says.  Vanilla writes a role as an("Archeologist"); under its
	 * picture the article is only clutter.  "Yes; start game" breaks at its
	 * semicolon, clear of the corner letter.
	 */
	private static String keyLabel(MenuItem item)
	{
		String name = item.getName();
		if(item.getTile() >= 0)
		{
			if(name.startsWith("an "))
				name = name.substring(3);
			else if(name.startsWith("a "))
				name = name.substring(2);
		}
		return name.replace("; ", ";\n");
	}

	/**
	 * A key's colour.  The choices are the keyboard's letters: cream on the
	 * Terminal skins, grey on the GameCube.  The small keys under them are
	 * dark.  The default -- Random, or "Yes; start game", the one Enter
	 * presses -- is amber, as a dialog's OK is; Quit is red.
	 */
	private static int[] family(MenuItem item, boolean confirm, boolean small)
	{
		if(item.isSelected())
			return RhTheme.capFor(RhTheme.A90);
		if(item.getAcc() == 'q' && "Quit".equals(item.getName()))
			return RhTheme.capFor(RhTheme.R90);
		if(small)
			return RhTheme.capFor(RhTheme.G90);
		return RhTheme.gamecube() ? RhTheme.GC_GREY : RhTheme.CAP_CREAM;
	}

	/** A tile as a bitmap of its own pixels, in the fixed skin tone if one is set. */
	private static Bitmap tileBitmap(Tileset tiles, int tile, int tone)
	{
		if(tiles == null || !tiles.hasTiles())
			return null;
		int w = tiles.getTileWidth(), h = tiles.getTileHeight();
		if(w <= 0 || h <= 0)
			return null;
		int[] px = new int[w * h];
		if(!tiles.getTilePixels(tile, px))
			return null;
		RhDoll.toneTile(px, tone);
		return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
	}

	// ____________________________________________________________________________________
	/** The hero on the glass: the tile, unfiltered, at a whole multiple of its pixels. */
	private static final class Picture extends View
	{
		private final Bitmap mTile;
		private final Paint mPaint = new Paint();
		private final RectF mDst = new RectF();

		Picture(Context c, Bitmap tile)
		{
			super(c);
			mTile = tile;
			mPaint.setFilterBitmap(false);
		}

		@Override
		protected void onDraw(Canvas canvas)
		{
			float target = Math.min(getHeight() - RhTheme.dp(getContext(), 8f), RhTheme.dp(getContext(), HERO));
			int w = mTile.getWidth();
			float size = target >= w ? w * (float)Math.floor(target / w) : target;
			float left = Math.round((getWidth() - size) / 2f);
			float top = Math.round((getHeight() - size) / 2f);
			mDst.set(left, top, left + size, top + size);
			canvas.drawBitmap(mTile, null, mDst, mPaint);
		}
	}
}
