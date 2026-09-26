package com.tbd.forkfront.rolehack;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.tbd.forkfront.Tileset;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The paper doll (Rolehack, 2026-09-25): the hero's armour and weapon drawn over
 * the hero's own tile.  PLACEHOLDER ART -- the sprites below are drafts that test
 * the rules on the phone; see paperdoll-census-2026-09-25.md and
 * tools/paperdoll/doll.py in the workspace.
 *
 * Decision of record (Lucas): layers over whatever tile the game picks.  The
 * base is the tile the core already draws for the hero -- the role tile, or the
 * race tile under the showrace option -- and gear is stamped over it.
 *
 * The core sends a "look" (winandroid.c, and_send_hero_look()): the hero's
 * square, the tile of the hero's own glyph, and for each slot the tile the
 * floor would show for the item.  Everything here is derived from those
 * tiles, so the doll shows no more than a glance at the floor would.
 *
 * Model:
 *  - Each base tile has per-region anchors (head and torso offsets from the
 *    human frame, the two grip pixels, the glove pixels, the feet), measured
 *    from win/share/monsters.txt.  A sprite is drawn once, on the human frame.
 *  - A sprite is rows of {x0, chars}: '.' leaves the base alone, '~' clears to
 *    the tile background (a helmet clears a hat brim), l/m/d are the item's
 *    colour ramp, and any other letter is a fixed colour from NetHack's tile
 *    palette.
 *  - An item's colour is the commonest colour in its own floor tile.
 *  - Gloves and boots recolour the base's hand and foot pixels: at 16x16 they
 *    are only colour.
 *  - Draw order is the occlusion rule: a cloak covers the suit.
 */
public final class RhDoll
{
	// ---- the look, as winandroid.c lays it out
	public static final int LOOK_LEN = 4 + 3 * 11;
	private static final int HELMET = 0, SUIT = 1, SHIRT = 2, CLOAK = 3, SHIELD = 4,
		GLOVES = 5, BOOTS = 6, EYEWEAR = 7, AMULET = 8, WEAPON = 9, OFFHAND = 10;

	// weapon families (RH_DOLL_* in winandroid.c)
	private static final int SHORT_BLADE = 1, SWORD = 2, GREAT_SWORD = 3, AXE = 4,
		PICK = 5, BLUNT = 6, STAFF = 7, POLE = 8, LAUNCHER = 9, MISSILE = 10,
		WHIP = 11, HORN = 12;

	public static final class Look
	{
		public final int x, y, base;
		private final int[] mA;
		private final int mKey;

		private Look(int[] a)
		{
			mA = a;
			x = a[1];
			y = a[2];
			base = a[3];
			// The composite depends on everything but the position.
			int[] k = Arrays.copyOfRange(a, 3, a.length);
			mKey = Arrays.hashCode(k);
		}

		/** Null for a look this build doesn't understand. */
		public static Look parse(int[] a)
		{
			if(a == null || a.length < LOOK_LEN || a[0] != 1)
				return null;
			return new Look(a.clone());
		}

		int tile(int slot)  { return mA[4 + 3 * slot]; }
		int color(int slot) { return mA[5 + 3 * slot]; }
		int shape(int slot) { return mA[6 + 3 * slot]; }
		boolean has(int slot) { return tile(slot) >= 0; }
	}

	// ---- anchors per base tile (indices in default_16x16.png)
	private static final class Anchor
	{
		int headDx, headDy, torsoDx, torsoDy;
		int mainX = 4, mainY = 10, offX = 11, offY = 10;
		int[] hands;                 // x,y pairs; null = the two grips
		int feetRow = 13;            // -1: the costume hides the feet
		int[] feetCols = { 5, 6, 9, 10 };

		Anchor head(int dx, int dy)  { headDx = dx; headDy = dy; return this; }
		Anchor torso(int dx, int dy) { torsoDx = dx; torsoDy = dy; return this; }
		Anchor grips(int mx, int my, int ox, int oy) { mainX = mx; mainY = my; offX = ox; offY = oy; return this; }
		Anchor hands(int... xy) { hands = xy; return this; }
		Anchor feet(int row, int... cols) { feetRow = row; feetCols = cols; return this; }
		Anchor robe() { feetRow = -1; return this; }
	}

	private static final Anchor DEFAULT = new Anchor();
	private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
	private static void pair(int maleTile, Anchor a) { ANCHORS.put(maleTile, a); ANCHORS.put(maleTile + 1, a); }
	static
	{
		pair(676, new Anchor().head(0, 1).torso(0, 1).grips(4, 11, 11, 11).feet(14, 5, 6, 7, 9, 10, 11)); // archeologist
		pair(678, new Anchor().feet(14, 5, 6, 9, 10));                                                 // barbarian
		pair(680, new Anchor());                                                                       // cave dweller
		pair(682, new Anchor().robe());                                                                // healer
		pair(684, new Anchor());                                                                       // knight
		pair(686, new Anchor().head(0, 2).hands(6, 9, 7, 9, 9, 9, 10, 9).robe());                     // monk
		ANCHORS.put(688, new Anchor().head(0, -1).grips(4, 9, 11, 9).robe());                          // cleric, male
		ANCHORS.put(689, new Anchor().grips(4, 9, 11, 9).robe());                                      // cleric, female
		pair(690, new Anchor().head(1, 0).torso(1, 0).grips(5, 10, 12, 10).feet(13, 6, 7, 10, 11));   // ranger
		pair(692, new Anchor().head(0, 2).torso(0, 2).grips(4, 12, 11, 12).feet(14, 5, 6, 9, 10));    // rogue
		pair(694, new Anchor().head(0, 1).torso(0, 1).grips(4, 11, 11, 11).feet(14, 5, 6, 9, 10));    // samurai
		pair(696, new Anchor().head(0, 1).torso(0, 1).grips(4, 11, 11, 11).feet(14, 5, 6, 9, 10));    // tourist
		pair(698, new Anchor());                                                                       // valkyrie
		pair(700, new Anchor().robe());                                                                // wizard
		pair(702, new Anchor().robe());                                                                // apothecary (a recoloured wizard)
		pair(532, new Anchor());                                                                       // human (showrace)
		pair(540, new Anchor());                                                                       // elf
		Anchor small = new Anchor().head(-1, 3).torso(-1, 1).grips(4, 11, 8, 11).feet(13, 4, 5, 7, 8);
		pair(92, small);                                                                               // dwarf
		pair(338, small);                                                                              // gnome
		pair(148, new Anchor().head(-2, 1).torso(-2, 0).grips(2, 10, 9, 10).feet(13, 2, 3, 4, 6, 7, 8)); // orc
	}

	// ---- sprites, on the human frame (face x6-9, eyes row 4, shoulders row 7)
	private static final class Sprite
	{
		final int[] ys;
		final int[] x0s;
		final String[] rows;

		Sprite(Object... yXs)
		{
			int n = yXs.length / 3;
			ys = new int[n]; x0s = new int[n]; rows = new String[n];
			for(int i = 0; i < n; i++)
			{
				ys[i] = (Integer)yXs[3 * i];
				x0s[i] = (Integer)yXs[3 * i + 1];
				rows[i] = (String)yXs[3 * i + 2];
			}
		}
	}

	private static final Sprite S_HELMET = new Sprite(
		0, 3, "~~~~~~~~~~", 1, 3, "~~~~~~~~~~", 2, 3, "~~~~lmA~~~", 3, 3, "~~~lmmdA~~");
	private static final Sprite S_EYEWEAR = new Sprite(4, 5, "dmmmmd");
	private static final Sprite S_SUIT = new Sprite(
		7, 5, "lmddmd", 8, 4, "Nlmmmldd", 9, 4, "lAlmmdAd", 10, 6, "lmmd", 11, 6, "mmdd");
	private static final Sprite S_SHIRT = new Sprite(8, 5, "lmmmmd", 9, 6, "mmmd", 10, 6, "mmmd");
	private static final Sprite S_CLOAK = new Sprite(
		7, 5, "lmHmmd", 8, 4, "lmmmmmmd", 9, 4, "lmmmmmmd",
		10, 5, "mmmAmd", 11, 4, "lmmmAmmd", 12, 4, "lmmmAmmd");
	private static final Sprite S_AMULET = new Sprite(8, 7, "lm");
	// relative to the off-hand grip
	private static final Sprite S_SHIELD = new Sprite(-2, -1, "lmdA", -1, -1, "mWdA", 0, -1, "mmdA", 1, 0, "dA");
	// relative to the weapon-hand grip
	private static final Sprite S_SHORT_BLADE = new Sprite(-3, 0, "N", -2, 0, "O", -1, -1, "KHK");
	private static final Sprite S_SWORD = new Sprite(
		-7, 0, "N", -6, 0, "N", -5, 0, "O", -4, 0, "N", -3, 0, "O", -2, 0, "O", -1, -1, "KHK", 1, 0, "J");
	private static final Sprite S_GREAT_SWORD = new Sprite(
		-10, 0, "N", -9, 0, "N", -8, 0, "O", -7, 0, "N", -6, 0, "O", -5, 0, "N", -4, 0, "O", -3, 0, "O",
		-2, -1, "KHK", -1, 0, "J", 1, 0, "J");
	private static final Sprite S_AXE = new Sprite(
		-6, -2, "NOJ", -5, -2, "OWJ", -4, -1, "WJ", -3, 0, "J", -2, 0, "J", -1, 0, "J");
	private static final Sprite S_PICK = new Sprite(
		-6, -2, "OOJOO", -5, -2, "W.J.W", -4, 0, "J", -3, 0, "J", -2, 0, "J", -1, 0, "J");
	private static final Sprite S_BLUNT = new Sprite(
		-6, -1, "lm", -5, -1, "md", -4, 0, "J", -3, 0, "J", -2, 0, "J", -1, 0, "J");
	private static final Sprite S_STAFF = new Sprite(
		-8, 0, "K", -7, 0, "J", -6, 0, "J", -5, 0, "J", -4, 0, "J", -3, 0, "J", -2, 0, "J", -1, 0, "J",
		1, 0, "J", 2, 0, "J");
	private static final Sprite S_POLE = new Sprite(
		-10, 0, "N", -9, 0, "O", -8, -1, "WOW", -7, 0, "J", -6, 0, "J", -5, 0, "J", -4, 0, "J",
		-3, 0, "J", -2, 0, "J", -1, 0, "J", 1, 0, "J", 2, 0, "J");
	private static final Sprite S_LAUNCHER = new Sprite(
		-4, -1, "J", -3, -2, "J", -2, -2, "J", -1, -2, "J", 0, -2, "J", 1, -2, "J", 2, -1, "J");
	private static final Sprite S_MISSILE = new Sprite(-2, 0, "l", -1, 0, "m");
	private static final Sprite S_WHIP = new Sprite(1, -1, "J", 2, -2, "J", 3, -2, "J", 3, -1, "J");
	private static final Sprite S_HORN = new Sprite(-5, 0, "N", -4, 0, "Z", -3, 0, "N", -2, 0, "Z", -1, 0, "N");
	/** Anything that is not a weapon: a thing held up in the hand. */
	private static final Sprite S_HELD = new Sprite(-2, -1, "lm", -1, -1, "md");

	private static Sprite weaponSprite(int shape)
	{
		switch(shape & 0xff)
		{
		case SHORT_BLADE: return S_SHORT_BLADE;
		case SWORD:       return S_SWORD;
		case GREAT_SWORD: return S_GREAT_SWORD;
		case AXE:         return S_AXE;
		case PICK:        return S_PICK;
		case BLUNT:       return S_BLUNT;
		case STAFF:       return S_STAFF;
		case POLE:        return S_POLE;
		case LAUNCHER:    return S_LAUNCHER;
		case MISSILE:     return S_MISSILE;
		case WHIP:        return S_WHIP;
		case HORN:        return S_HORN;
		default:          return S_HELD;
		}
	}

	// NetHack's tile palette (win/share/monsters.txt), for the fixed colours.
	private static int fixed(char c)
	{
		switch(c)
		{
		case 'A': return 0xff000000;
		case 'H': return 0xffffff00;
		case 'J': return 0xff914700;
		case 'K': return 0xffcc4f00;
		case 'N': return 0xffffffff;
		case 'O': return 0xffd7d7d7;
		case 'W': return 0xff838383;
		case 'Z': return 0xffc3c3c3;
		default:  return 0xffff00ff;   // a typo shows up magenta
		}
	}

	// ---- compositing
	private final Map<Integer, Integer> mTint = new HashMap<>();
	private final Map<Integer, Bitmap> mCache = new LinkedHashMap<Integer, Bitmap>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> e) { return size() > 16; }
	};
	private int[] mBase = new int[256];
	private int[] mItem = new int[256];

	public void clear()
	{
		mTint.clear();
		mCache.clear();
	}

	/** The dressed hero for this look, or null to draw the plain tile. */
	public Bitmap compose(Look look, Tileset ts)
	{
		if(look == null || look.base < 0 || ts.getTileWidth() != 16 || ts.getTileHeight() != 16)
			return null;
		Bitmap cached = mCache.get(look.mKey);
		if(cached != null)
			return cached;
		if(!ts.getTilePixels(look.base, mBase))
			return null;

		Anchor a = ANCHORS.get(look.base);
		if(a == null)
			a = DEFAULT;
		int[] px = mBase.clone();
		int bg = px[0];

		boolean covered = look.has(SUIT) || look.has(CLOAK);
		if(look.has(SHIRT) && !covered)
			stamp(px, bg, S_SHIRT, a.torsoDx, a.torsoDy, false, ramp(look, SHIRT, ts, true));
		if(look.has(SUIT))
			stamp(px, bg, S_SUIT, a.torsoDx, a.torsoDy, false, ramp(look, SUIT, ts, true));
		if(look.has(CLOAK))
			stamp(px, bg, S_CLOAK, a.torsoDx, a.torsoDy, false, ramp(look, CLOAK, ts, true));
		if(look.has(AMULET) && !covered)
			stamp(px, bg, S_AMULET, a.torsoDx, a.torsoDy, false, ramp(look, AMULET, ts, false));
		if(look.has(BOOTS) && a.feetRow >= 0)
		{
			int c = ramp(look, BOOTS, ts, false)[1];
			for(int x : a.feetCols)
				recolour(px, bg, x, a.feetRow, c);
		}
		if(look.has(GLOVES))
		{
			int c = ramp(look, GLOVES, ts, false)[1];
			int[] h = a.hands != null ? a.hands : new int[] { a.mainX, a.mainY, a.offX, a.offY };
			for(int i = 0; i + 1 < h.length; i += 2)
				recolour(px, bg, h[i], h[i + 1], c);
		}
		if(look.has(HELMET))
			stamp(px, bg, S_HELMET, a.headDx, a.headDy, false, ramp(look, HELMET, ts, false));
		if(look.has(EYEWEAR))
			stamp(px, bg, S_EYEWEAR, a.headDx, a.headDy, false, ramp(look, EYEWEAR, ts, false));
		if(look.has(SHIELD))
			stamp(px, bg, S_SHIELD, a.offX, a.offY, false, ramp(look, SHIELD, ts, false));
		if(look.has(WEAPON))
			stamp(px, bg, weaponSprite(look.shape(WEAPON)), a.mainX, a.mainY, false,
			      ramp(look, WEAPON, ts, false));
		if(look.has(OFFHAND))
			stamp(px, bg, weaponSprite(look.shape(OFFHAND)), a.offX, a.offY, true,
			      ramp(look, OFFHAND, ts, false));

		Bitmap bmp = Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888);
		mCache.put(look.mKey, bmp);
		return bmp;
	}

	private static void stamp(int[] px, int bg, Sprite s, int ox, int oy, boolean mirror, int[] ramp)
	{
		for(int r = 0; r < s.rows.length; r++)
		{
			String row = s.rows[r];
			for(int i = 0; i < row.length(); i++)
			{
				char c = row.charAt(i);
				if(c == '.')
					continue;
				int dx = s.x0s[r] + i;
				int x = ox + (mirror ? -dx : dx);
				int y = oy + s.ys[r];
				if(x < 0 || x >= 16 || y < 0 || y >= 16)
					continue;
				int col;
				switch(c)
				{
				case '~': col = bg; break;
				case 'l': col = ramp[0]; break;
				case 'm': col = ramp[1]; break;
				case 'd': col = ramp[2]; break;
				default:  col = fixed(c);
				}
				px[y * 16 + x] = col;
			}
		}
	}

	private static void recolour(int[] px, int bg, int x, int y, int col)
	{
		if(x < 0 || x >= 16 || y < 0 || y >= 16)
			return;
		int p = px[y * 16 + x];
		if(p == bg || (p & 0xffffff) == 0)     // background or the black outline
			return;
		px[y * 16 + x] = col;
	}

	/** light, mid, dark from the item's own floor tile. */
	private int[] ramp(Look look, int slot, Tileset ts, boolean largeSurface)
	{
		int c = tint(look.tile(slot), look.color(slot), ts);
		if(largeSurface)   // suits and cloaks: darker, or plate reads as a white smock
			return new int[] { c, mix(c, 0xff000000, 0.2f), mix(c, 0xff000000, 0.5f) };
		return new int[] { mix(c, 0xffffffff, 0.45f), c, mix(c, 0xff000000, 0.45f) };
	}

	/** The commonest colour in the tile, leaving out its background and black. */
	private int tint(int tile, int fallback, Tileset ts)
	{
		Integer known = mTint.get(tile);
		if(known != null)
			return known;
		int best = 0xff000000 | fallback;
		if(ts.getTilePixels(tile, mItem))
		{
			int bg = mItem[0];
			Map<Integer, Integer> n = new HashMap<>();
			int bestN = 0;
			for(int p : mItem)
			{
				if(p == bg || Color.red(p) + Color.green(p) + Color.blue(p) < 90)
					continue;
				Integer k = n.get(p);
				int v = k == null ? 1 : k + 1;
				n.put(p, v);
				if(v > bestN)
				{
					bestN = v;
					best = p;
				}
			}
		}
		mTint.put(tile, best);
		return best;
	}

	private static int mix(int c, int to, float f)
	{
		int r = Math.round(Color.red(c) + (Color.red(to) - Color.red(c)) * f);
		int g = Math.round(Color.green(c) + (Color.green(to) - Color.green(c)) * f);
		int b = Math.round(Color.blue(c) + (Color.blue(to) - Color.blue(c)) * f);
		return Color.argb(255, r, g, b);
	}
}
