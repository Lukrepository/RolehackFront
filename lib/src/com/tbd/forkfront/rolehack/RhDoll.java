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
 *  - Body armour and shirts are cut from the item's own floor tile: every suit
 *    tile is the same shirt shape, light on the left and dark on the right,
 *    with the suit's texture across the chest, so the chest is taken at 1:1
 *    and masked to the hero's torso (stampBody).  Gaps in mail show the base.
 *    Dragon scales, which the core flags, are a hide: a scale pattern.
 *    Armour is a whole set (Lucas): a suit carries on down the legs to the
 *    boots, recolouring the base's own leg pixels (stampLegs).
 *  - Any other item's colour is the commonest colour in its own floor tile.
 *  - Gloves and boots recolour the base's hand and foot pixels: at 16x16 they
 *    are only colour.
 *  - Draw order is the occlusion rule.  A cloak is a cape behind the body
 *    (Lucas: it was covering the armour): it paints only background down
 *    both sides and flares at the hem -- plus the drop shadow on the right --
 *    with a clasp at the neck in front.  Robe, apron and mummy wrapping,
 *    which the core marks FRONT, are worn in front and cover the suit.
 *  - Dwarf and gnome have a short frame of their own (Lucas: they came out
 *    mis-sized) -- face row 7, torso rows 9-11, the beard kept over the chest
 *    -- so they get short layers rather than human ones moved (dressShort;
 *    tools/paperdoll/short.py).
 *  - Costume: an item the hero's own role tile already draws (the core marks
 *    it, and only once the player knows the item's type) is left to the
 *    tile: the Archeologist's fedora, the Knight's ring mail, the
 *    Apothecary's apron and boots.
 *  - Dragon scale mail gets pauldrons, one design per dragon (Lucas asked for
 *    them; the designs are tools/paperdoll/pauldrons.py).  They sit on the
 *    shoulders over a cloak too, so the usual mail-and-cloak outfit shows
 *    them.
 *  - Skin: every human and humanoid hero body draws skin with one flat palette
 *    entry, L (255,182,145), so a skin tone is a swap of that colour on the
 *    base, done before any gear (bronze plate and the leather jacket use L in
 *    their own art).  Unset, each character's tone is random and fixed, from a
 *    seed the core derives from ubirthday; OPTIONS=skintone:N in the options
 *    file fixes it (Lucas, 2026-09-25).  The tone applies even with the doll
 *    switched off: it is the hero's look, not a preview feature.
 */
public final class RhDoll
{
	// ---- the look, as winandroid.c lays it out
	public static final int LOOK_LEN = 4 + 3 * 11;      // version 1
	public static final int LOOK_LEN_2 = LOOK_LEN + 2;  // version 2 adds the skin

	/**
	 * Skin tones, fairest to deepest; the skintone option counts from 1, and the
	 * core's RH_SKINTONES must match the length.  The second is NetHack's own L.
	 * The deep end is redder and less saturated than NetHack's browns J and K,
	 * so skin stays distinct from brown hair and costumes (see
	 * tools/paperdoll/skin.py in the workspace).
	 */
	private static final int[] TONES = {
		0xffffd6ba, 0xffffb691, 0xffeca67e, 0xffd28e68,
		0xffb27254, 0xff905844, 0xff704238, 0xff54322e,
	};
	private static final int VANILLA_SKIN = 0xffffb691;
	private static final int HELMET = 0, SUIT = 1, SHIRT = 2, CLOAK = 3, SHIELD = 4,
		GLOVES = 5, BOOTS = 6, EYEWEAR = 7, AMULET = 8, WEAPON = 9, OFFHAND = 10;

	// weapon families (RH_DOLL_* in winandroid.c)
	/** heroLook shape bit: dragon scales, worn as a hide rather than a suit. */
	private static final int HIDE = 0x200;
	/** heroLook shape bit: the hero's role tile already draws this item. */
	private static final int COSTUME = 0x400;
	/** heroLook shape bit: dragon scale mail; the dragon's index is in bits 12-15. */
	private static final int DRAGON = 0x800;
	/** heroLook shape bit: a cloak-slot item worn in front (robe, apron, mummy wrapping). */
	private static final int FRONT = 0x10000;

	/**
	 * Pauldrons for the left shoulder, "x,y,colour" on the human frame, in the
	 * core's dragon order (gray, gold, silver, red, white, orange, black, blue,
	 * green, yellow).  The right shoulder is the mirror (x -> 15 - x) in the
	 * darker shade, since NetHack lights its tiles from the left.
	 */
	private static final String[] PAULDRONS = {
		"4,6,O 5,6,W 3,7,O 4,7,Q 5,7,W 3,8,W",                                    // gray: anti-magic
		"4,5,N 2,6,N 4,6,H 5,6,H 3,7,H 4,7,C 5,7,C 3,8,C",                        // gold: light
		"3,5,N 4,5,N 3,6,N 4,6,B 5,6,N 3,7,B 4,7,N 5,7,B 3,8,N",                  // silver: a mirror
		"3,4,H 3,5,C 5,5,H 3,6,D 4,6,C 5,6,D 3,7,D 4,7,D 3,8,J",                  // red: fire
		"4,6,N 5,6,O 3,7,N 4,7,O 5,7,O 2,8,B 3,8,O 2,9,B",                        // white: icicles
		"4,5,C 5,5,C 3,6,C 5,6,K 3,7,C 4,7,K 5,7,K 3,8,K",                        // orange: a crescent
		"3,3,W 3,4,R 5,4,W 3,5,R 4,5,Q 5,5,R 3,6,Q 4,6,R 5,6,R 3,7,Q 4,7,R 3,8,Q", // black: shards
		"4,3,B 3,4,B 4,5,B 5,5,E 3,6,E 4,6,E 5,6,E 3,7,E 4,7,B 3,8,E",            // blue: a bolt
		"3,5,G 5,5,G 3,6,F 4,6,F 5,6,F 3,7,F 4,7,G 5,7,F 3,8,F 3,10,G",           // green: fangs, a drop
		"4,6,H 5,6,H 3,7,H 4,7,H 5,7,K 3,8,H 3,9,G 3,11,G",                       // yellow: acid
	};
	private static final String DARKER_FROM = "NOWHCKBG", DARKER_TO = "OWVCKJEF";

	private static final int SHORT_BLADE = 1, SWORD = 2, GREAT_SWORD = 3, AXE = 4,
		PICK = 5, BLUNT = 6, STAFF = 7, POLE = 8, LAUNCHER = 9, MISSILE = 10,
		WHIP = 11, HORN = 12;

	public static final class Look
	{
		public final int x, y, base;
		private final int[] mA;
		private final int mKey;
		/** Index into TONES. */
		final int tone;

		private Look(int[] a)
		{
			mA = a;
			x = a[1];
			y = a[2];
			base = a[3];
			if(a[0] >= 2)
			{
				int fixed = a[LOOK_LEN + 1];
				tone = fixed >= 1 && fixed <= TONES.length ? fixed - 1
				                                           : (a[LOOK_LEN] & 0x7fffffff) % TONES.length;
			}
			else
				tone = 1;   // NetHack's own
			// The composite depends on everything but the position.
			int[] k = Arrays.copyOfRange(a, 3, a.length);
			mKey = Arrays.hashCode(k);
		}

		/** Null for a look this build doesn't understand. */
		public static Look parse(int[] a)
		{
			if(a == null || a[0] < 1 || a.length < (a[0] >= 2 ? LOOK_LEN_2 : LOOK_LEN))
				return null;
			return new Look(a.clone());
		}

		int tile(int slot)  { return mA[4 + 3 * slot]; }
		int color(int slot) { return mA[5 + 3 * slot]; }
		int shape(int slot) { return mA[6 + 3 * slot]; }
		boolean has(int slot) { return tile(slot) >= 0; }
		/** Worn, and not already drawn by the hero's own tile. */
		boolean draws(int slot) { return has(slot) && (shape(slot) & COSTUME) == 0; }
	}

	// ---- anchors per base tile (indices in default_16x16.png)
	private static final class Anchor
	{
		int headDx, headDy, torsoDx, torsoDy;
		int mainX = 4, mainY = 10, offX = 11, offY = 10;
		int[] hands;                 // x,y pairs; null = the two grips
		int feetRow = 13;            // -1: the costume hides the feet
		int[] feetCols = { 5, 6, 9, 10 };
		boolean shortFrame;          // dwarf and gnome: their own short body (dressShort)
		int[] keep = {};             // x,y pairs the layers leave alone: a beard

		Anchor head(int dx, int dy)  { headDx = dx; headDy = dy; return this; }
		Anchor torso(int dx, int dy) { torsoDx = dx; torsoDy = dy; return this; }
		Anchor grips(int mx, int my, int ox, int oy) { mainX = mx; mainY = my; offX = ox; offY = oy; return this; }
		Anchor hands(int... xy) { hands = xy; return this; }
		Anchor feet(int row, int... cols) { feetRow = row; feetCols = cols; return this; }
		Anchor robe() { feetRow = -1; return this; }
		Anchor shortFrame(int... keepXY) { shortFrame = true; keep = keepXY; return this; }
		boolean keeps(int x, int y)
		{
			for(int i = 0; i + 1 < keep.length; i += 2)
				if(keep[i] == x && keep[i + 1] == y)
					return true;
			return false;
		}
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
		// apothecary: Lucas's tile (2026-09-25) -- eyes on row 3, face x5-8, the pistol
		// hand at (4,10), the hanging hand at (9,10), high boots on row 13
		pair(702, new Anchor().head(-1, -1).grips(4, 10, 9, 10).hands(3, 9, 9, 10).feet(13, 5, 6, 8, 9));
		pair(532, new Anchor());                                                                       // human (showrace)
		pair(540, new Anchor());                                                                       // elf
		// dwarf and gnome: a short frame of their own (dressShort) -- face on row 7, torso
		// rows 9-11, hands (4,11) and (8,11) -- with the beard over the chest kept.  Dwarf
		// women are bearded too; gnome women are not.
		Anchor bearded = new Anchor().grips(4, 11, 8, 11).feet(13, 4, 5, 7, 8)
		                             .shortFrame(5, 9, 6, 9, 7, 9, 6, 10);
		pair(92, bearded);                                                                             // dwarf
		ANCHORS.put(338, bearded);                                                                     // gnome, male
		ANCHORS.put(339, new Anchor().grips(4, 11, 8, 11).feet(13, 4, 5, 7, 8).shortFrame());         // gnome, female
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
	private static final Sprite S_HIDE = new Sprite(
		7, 5, "lmmmmd", 8, 4, "lmlmlmdd", 9, 4, "mAmlmdAd", 10, 6, "lmld", 11, 6, "mlmd");
	// The torso a cut suit covers on the human frame: rows 7-11, and the two
	// black arm separators the hero tiles keep at (5,9) and (10,9).
	private static final int[] BODY_Y  = { 7, 8, 9, 10, 11 };
	private static final int[] BODY_X0 = { 5, 4, 4, 6, 6 };
	private static final int[] BODY_X1 = { 10, 11, 11, 9, 9 };
	/** Gray dragon scale mail draws its grey as background: 56% of its chest. */
	private static final float GAP_FILL_ABOVE = 0.5f;
	/** Black dragon scales are 66% outline black; every other dragon 43%. */
	private static final float BLACK_HIDE_ABOVE = 0.55f;
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
	// The short frame's own sprites, in tile coordinates (tools/paperdoll/short.py).
	// The helmet clears the tile's hat and cheek pieces, then a smaller dome.
	private static final Sprite S_HELMET_SHORT = new Sprite(
		0, 2, "~~~~~~~~~", 1, 2, "~~~~~~~~~", 2, 2, "~~~~~~~~~", 3, 2, "~~~~~~~~~", 4, 2, "~~~~~~~~~",
		5, 2, "~~~lmA~~~", 6, 2, "~~lmmdA~~", 7, 4, "~...~");
	private static final Sprite S_EYEWEAR_SHORT = new Sprite(7, 4, "dmmmd");
	private static final Sprite S_FRONT_SHORT = new Sprite(
		9, 3, "lmmmmmd", 10, 3, "lAmmmAd", 11, 5, "mmm", 12, 4, "lmmmd");
	/** A buckler on the arm, relative to the off-hand grip. */
	private static final Sprite S_SHIELD_SHORT = new Sprite(-2, 0, "lmA", -1, 0, "WdA", 0, 0, "mdA");
	/** The short torso: rows 9-11.  The arm separators at (4,10), (8,10) stay black. */
	private static final int[] SHORT_Y = { 9, 10, 11 }, SHORT_X0 = { 3, 3, 5 }, SHORT_X1 = { 9, 9, 7 };

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
		case 'B': return 0xff00b6ff;
		case 'C': return 0xffff6c00;
		case 'D': return 0xffff0000;
		case 'E': return 0xff0000ff;
		case 'F': return 0xff009100;
		case 'G': return 0xff6cff00;
		case 'H': return 0xffffff00;
		case 'I': return 0xffff00ff;
		case 'J': return 0xff914700;
		case 'K': return 0xffcc4f00;
		case 'L': return 0xffffb691;
		case 'M': return 0xffededed;
		case 'N': return 0xffffffff;
		case 'O': return 0xffd7d7d7;
		case 'P': return 0xff6c91b6;
		case 'Q': return 0xff121212;
		case 'R': return 0xff363636;
		case 'S': return 0xff494949;
		case 'T': return 0xff525252;
		case 'U': return 0xffcdcdcd;
		case 'V': return 0xff686868;
		case 'W': return 0xff838383;
		case 'X': return 0xff8c8c8c;
		case 'Y': return 0xff959595;
		case 'Z': return 0xffc3c3c3;
		case '0': return 0xff646464;
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

	/**
	 * The hero for this look -- skin toned, and dressed when gear is true -- or
	 * null to draw the plain tile.
	 */
	public Bitmap compose(Look look, Tileset ts, boolean gear)
	{
		if(look == null || look.base < 0 || ts.getTileWidth() != 16 || ts.getTileHeight() != 16)
			return null;
		int skin = TONES[look.tone];
		if(!gear && skin == VANILLA_SKIN)
			return null;
		int key = look.mKey * 31 + (gear ? 1 : 0);
		Bitmap cached = mCache.get(key);
		if(cached != null)
			return cached;
		if(!ts.getTilePixels(look.base, mBase))
			return null;

		Anchor a = ANCHORS.get(look.base);
		if(a == null)
			a = DEFAULT;
		int[] px = mBase.clone();
		int bg = px[0];

		for(int i = 0; i < px.length; i++)
			if(px[i] == VANILLA_SKIN)
				px[i] = skin;
		if(!gear)
		{
			Bitmap bmp = Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888);
			mCache.put(key, bmp);
			return bmp;
		}

		if(a.shortFrame)
			dressShort(px, bg, look, a, ts);
		else
			dressHuman(px, bg, look, a, ts);

		Bitmap bmp = Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888);
		mCache.put(key, bmp);
		return bmp;
	}

	/** Gear on the human frame, which every hero body but dwarf and gnome shares. */
	private void dressHuman(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		boolean front = look.has(CLOAK) && (look.shape(CLOAK) & FRONT) != 0;
		boolean cape = look.draws(CLOAK) && !front;
		boolean covered = look.has(SUIT) || front;
		if(cape)
			stampCape(px, bg, a, ramp(look, CLOAK, ts, true));
		if(look.draws(SHIRT) && !covered)
			stampBody(px, bg, look, SHIRT, a, ts, S_SHIRT);
		if(look.draws(SUIT))
			stampBody(px, bg, look, SUIT, a, ts, S_SUIT);
		if(look.draws(CLOAK) && front)
			stamp(px, bg, S_CLOAK, a.torsoDx, a.torsoDy, false, ramp(look, CLOAK, ts, true));
		if(cape)
			putPx(px, 7 + a.torsoDx, 7 + a.torsoDy, fixed('H'));   // the clasp
		if(look.draws(SUIT) && (look.shape(SUIT) & DRAGON) != 0)
			stampPauldrons(px, (look.shape(SUIT) >> 12) & 0xf, a);
		if(look.draws(AMULET) && !covered)
			stamp(px, bg, S_AMULET, a.torsoDx, a.torsoDy, false, ramp(look, AMULET, ts, false));
		if(look.draws(BOOTS) && a.feetRow >= 0)
		{
			int c = ramp(look, BOOTS, ts, false)[1];
			for(int x : a.feetCols)
				recolour(px, bg, x, a.feetRow, c);
		}
		if(look.draws(GLOVES))
		{
			int c = ramp(look, GLOVES, ts, false)[1];
			int[] h = a.hands != null ? a.hands : new int[] { a.mainX, a.mainY, a.offX, a.offY };
			for(int i = 0; i + 1 < h.length; i += 2)
				recolour(px, bg, h[i], h[i + 1], c);
		}
		if(look.draws(HELMET))
			stamp(px, bg, S_HELMET, a.headDx, a.headDy, false, ramp(look, HELMET, ts, false));
		if(look.draws(EYEWEAR))
			stamp(px, bg, S_EYEWEAR, a.headDx, a.headDy, false, ramp(look, EYEWEAR, ts, false));
		if(look.draws(SHIELD))
			stamp(px, bg, S_SHIELD, a.offX, a.offY, false, ramp(look, SHIELD, ts, false));
		if(look.has(WEAPON))
			stamp(px, bg, weaponSprite(look.shape(WEAPON)), a.mainX, a.mainY, false,
			      ramp(look, WEAPON, ts, false));
		if(look.has(OFFHAND))
			stamp(px, bg, weaponSprite(look.shape(OFFHAND)), a.offX, a.offY, true,
			      ramp(look, OFFHAND, ts, false));
	}

	/**
	 * Gear on the short frame (dwarf, gnome): the same items, drawn to that body
	 * -- a short suit mask around the beard, a helmet that replaces the tile's
	 * own hat, a cape at x2 and x10 with no clasp (the beard covers the neck),
	 * pauldrons on the lower shoulders, a buckler, and weapons at two-thirds
	 * height, so a longsword is no taller than a dwarf.
	 */
	private void dressShort(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		boolean front = look.has(CLOAK) && (look.shape(CLOAK) & FRONT) != 0;
		boolean cape = look.draws(CLOAK) && !front;
		boolean covered = look.has(SUIT) || front;
		if(cape)
			stampCapeShort(px, bg, ramp(look, CLOAK, ts, true));
		if(look.draws(SHIRT) && !covered)
			stampBodyShort(px, bg, look, SHIRT, a, ts, false);
		if(look.draws(SUIT))
			stampBodyShort(px, bg, look, SUIT, a, ts, true);
		if(look.draws(CLOAK) && front)
			stampKeep(px, S_FRONT_SHORT, a, ramp(look, CLOAK, ts, true));
		if(look.draws(SUIT) && (look.shape(SUIT) & DRAGON) != 0)
			stampPauldrons(px, (look.shape(SUIT) >> 12) & 0xf, a);
		if(look.draws(AMULET) && !covered && !a.keeps(6, 10))
			putPx(px, 6, 10, ramp(look, AMULET, ts, false)[1]);
		if(look.draws(BOOTS))
		{
			int c = ramp(look, BOOTS, ts, false)[1];
			for(int x : a.feetCols)
				recolour(px, bg, x, a.feetRow, c);
		}
		if(look.draws(GLOVES))
		{
			int c = ramp(look, GLOVES, ts, false)[1];
			recolour(px, bg, a.mainX, a.mainY, c);
			recolour(px, bg, a.offX, a.offY, c);
		}
		if(look.draws(HELMET))
			stamp(px, bg, S_HELMET_SHORT, 0, 0, false, ramp(look, HELMET, ts, false));
		if(look.draws(EYEWEAR))
			stamp(px, bg, S_EYEWEAR_SHORT, 0, 0, false, ramp(look, EYEWEAR, ts, false));
		if(look.draws(SHIELD))
			stamp(px, bg, S_SHIELD_SHORT, a.offX, a.offY, false, ramp(look, SHIELD, ts, false));
		if(look.has(WEAPON))
			stampSquashed(px, weaponSprite(look.shape(WEAPON)), a.mainX, a.mainY, false,
			              ramp(look, WEAPON, ts, false));
		if(look.has(OFFHAND))
			stampSquashed(px, weaponSprite(look.shape(OFFHAND)), a.offX, a.offY, true,
			              ramp(look, OFFHAND, ts, false));
	}

	/**
	 * A suit or shirt on the short frame: the floor tile's chest at 1:1 (tile
	 * x = frame x + 1), laid on rows 9-11 around the beard; a suit carries on
	 * down the legs (row 12).  Hides and tiles with no suit shape alternate
	 * two colours, as on the human frame.
	 */
	private void stampBodyShort(int[] px, int bg, Look look, int slot, Anchor a, Tileset ts, boolean pants)
	{
		int[] two = null;
		int sy = -1, ibg = 0;
		if((look.shape(slot) & HIDE) != 0)
		{
			int[] r = hideRamp(look, slot, ts);
			two = new int[] { r[0], r[1] };
		}
		else if(ts.getTilePixels(look.tile(slot), mItem))
		{
			ibg = mItem[0];
			sy = shoulderRow(ibg);
		}
		if(two == null && sy < 0)
		{
			int[] r = ramp(look, slot, ts, true);
			two = new int[] { r[1], r[1] };
		}
		int[] gaps = new int[32];
		int nGaps = 0, cells = 0;
		for(int r = 0; r < SHORT_Y.length; r++)
		{
			int y = SHORT_Y[r];
			for(int x = SHORT_X0[r]; x <= SHORT_X1[r]; x++)
			{
				if(a.keeps(x, y))
					continue;
				if(y == 10 && (x == 4 || x == 8))
				{
					px[y * 16 + x] = 0xff000000;
					continue;
				}
				cells++;
				if(two != null)
				{
					px[y * 16 + x] = two[(x + y) % 2];
					continue;
				}
				int fy = sy + 2 + (y - 9);
				int c = fy < 16 ? mItem[fy * 16 + x + 1] : ibg;
				if(c != ibg)
					px[y * 16 + x] = c;
				else
					gaps[nGaps++] = y * 16 + x;
			}
		}
		int fill = two == null && nGaps > GAP_FILL_ABOVE * cells ? gapFill(sy, ibg) : 0;
		if(fill != 0)
			for(int i = 0; i < nGaps; i++)
				px[gaps[i]] = fill;
		if(!pants)
			return;
		for(int x = 4; x <= 8; x++)
		{
			int p = px[12 * 16 + x];
			if(p == bg || (p & 0xffffff) == 0)
				continue;
			if(two != null)
				px[12 * 16 + x] = two[(x + 12) % 2];
			else
			{
				int c = sy + 2 < 16 ? mItem[(sy + 2) * 16 + x + 1] : ibg;
				if(c != ibg)
					px[12 * 16 + x] = c;
				else if(fill != 0)
					px[12 * 16 + x] = fill;
			}
		}
	}

	/** The cape on the short frame: strips at x2 and x10, rows 9-13, flaring at the hem. */
	private static void stampCapeShort(int[] px, int bg, int[] ramp)
	{
		for(int y = 9; y <= 13; y++)
		{
			capePx(px, bg, 2, y, ramp[0], false);
			capePx(px, bg, 10, y, ramp[2], true);
		}
		for(int y = 12; y <= 13; y++)
		{
			capePx(px, bg, 1, y, ramp[1], false);
			capePx(px, bg, 11, y, ramp[2], true);
		}
	}

	/** A tile-coordinate sprite that leaves the anchor's kept pixels (a beard) alone. */
	private static void stampKeep(int[] px, Sprite s, Anchor a, int[] ramp)
	{
		for(int r = 0; r < s.rows.length; r++)
			for(int i = 0; i < s.rows[r].length(); i++)
			{
				char c = s.rows[r].charAt(i);
				int x = s.x0s[r] + i, y = s.ys[r];
				if(c == '.' || a.keeps(x, y))
					continue;
				putPx(px, x, y, c == 'l' ? ramp[0] : c == 'm' ? ramp[1] : c == 'd' ? ramp[2] : fixed(c));
			}
	}

	/** A weapon on the short frame: rows above the grip at two-thirds height. */
	private static void stampSquashed(int[] px, Sprite s, int ox, int oy, boolean mirror, int[] ramp)
	{
		for(int r = 0; r < s.rows.length; r++)
		{
			int ry = s.ys[r];
			if(ry < -1)
				ry = -1 + Math.floorDiv((ry + 1) * 2, 3);
			for(int i = 0; i < s.rows[r].length(); i++)
			{
				char c = s.rows[r].charAt(i);
				if(c == '.')
					continue;
				int dx = s.x0s[r] + i;
				putPx(px, ox + (mirror ? -dx : dx), oy + ry,
				      c == 'l' ? ramp[0] : c == 'm' ? ramp[1] : c == 'd' ? ramp[2] : fixed(c));
			}
		}
	}

	/** The suit tile's shoulder row, the first with six drawn pixels across x3-12 (mItem). */
	private int shoulderRow(int ibg)
	{
		for(int y = 0; y < 16; y++)
		{
			int n = 0;
			for(int x = 3; x <= 12; x++)
				if(mItem[y * 16 + x] != ibg)
					n++;
			if(n >= 6)
				return y;
		}
		return -1;
	}

	/** The commonest colour of the suit's darker right half, below its shoulders (mItem); 0 if none. */
	private int gapFill(int sy, int ibg)
	{
		Map<Integer, Integer> n = new HashMap<>();
		int fill = 0, best = 0;
		for(int y = sy; y < 16; y++)
			for(int x = 8; x < 16; x++)
			{
				int c = mItem[y * 16 + x];
				if(c == ibg || (c & 0xffffff) == 0)
					continue;
				Integer k = n.get(c);
				int v = k == null ? 1 : k + 1;
				n.put(c, v);
				if(v > best)
				{
					best = v;
					fill = c;
				}
			}
		return fill;
	}

	/**
	 * A suit or shirt, cut from the item's floor tile: find its shoulder row
	 * (the first with six drawn pixels across x3-12), take the five rows below
	 * it at 1:1, and lay them on the torso.  Where the floor tile shows
	 * background inside the suit -- between links, rings, bands -- the base
	 * shows through, unless the chest is mostly background by design, when the
	 * gaps take the commonest colour of the suit's darker right half.  A tile
	 * with no suit shape falls back to the placeholder sprite.
	 */
	private void stampBody(int[] px, int bg, Look look, int slot, Anchor a, Tileset ts, Sprite fallback)
	{
		boolean pants = slot == SUIT;
		if((look.shape(slot) & HIDE) != 0)
		{
			int[] ramp = hideRamp(look, slot, ts);
			stamp(px, bg, S_HIDE, a.torsoDx, a.torsoDy, false, ramp);
			if(pants)
				stampLegs(px, bg, a, -1, 0, 0, ramp);
			return;
		}
		int sy = ts.getTilePixels(look.tile(slot), mItem) ? shoulderRow(mItem[0]) : -1;
		if(sy < 0)
		{
			int[] ramp = ramp(look, slot, ts, true);
			stamp(px, bg, fallback, a.torsoDx, a.torsoDy, false, ramp);
			if(pants)
				stampLegs(px, bg, a, -1, 0, 0, new int[] { ramp[1], ramp[1] });
			return;
		}

		int ibg = mItem[0];
		int[] gaps = new int[64];
		int nGaps = 0, cells = 0;
		for(int r = 0; r < BODY_Y.length; r++)
		{
			int y = BODY_Y[r];
			int fy = sy + 1 + (y - 7);
			for(int x = BODY_X0[r]; x <= BODY_X1[r]; x++)
			{
				int X = x + a.torsoDx, Y = y + a.torsoDy;
				boolean inside = X >= 0 && X < 16 && Y >= 0 && Y < 16;
				if(y == 9 && (x == 5 || x == 10))
				{
					if(inside)
						px[Y * 16 + X] = 0xff000000;
					continue;
				}
				cells++;
				int c = fy >= 0 && fy < 16 ? mItem[fy * 16 + x] : ibg;
				if(c != ibg)
				{
					if(inside)
						px[Y * 16 + X] = c;
				}
				else if(inside)
					gaps[nGaps++] = Y * 16 + X;
			}
		}
		int fill = nGaps > GAP_FILL_ABOVE * cells ? gapFill(sy, ibg) : 0;
		if(fill != 0)
			for(int i = 0; i < nGaps; i++)
				px[gaps[i]] = fill;
		if(pants)
			stampLegs(px, bg, a, sy, ibg, fill, null);
	}

	/**
	 * The suit's legs: between the torso's last row and the feet, recolour the
	 * base's own leg pixels -- not background, not the black gap between the
	 * legs.  A cut suit repeats the three rows of chest texture below its
	 * shoulders (from mItem, the suit's tile); a hide or a placeholder
	 * alternates the two colours given.  A robe (no feet) already covers the
	 * legs, so there is nothing to do.
	 */
	private void stampLegs(int[] px, int bg, Anchor a, int sy, int ibg, int fill, int[] two)
	{
		if(a.feetRow < 0)
			return;
		for(int Y = 12 + a.torsoDy; Y < a.feetRow; Y++)
		{
			int yf = Y - a.torsoDy;
			int fy = sy + 2 + ((yf - 12) % 3 + 3) % 3;
			for(int xf = 4; xf <= 11; xf++)
			{
				int X = xf + a.torsoDx;
				if(X < 0 || X >= 16 || Y < 0 || Y >= 16)
					continue;
				int p = px[Y * 16 + X];
				if(p == bg || (p & 0xffffff) == 0)
					continue;
				if(two != null)
					px[Y * 16 + X] = two[(xf + yf) % 2];
				else if(fy >= 0 && fy < 16)
				{
					int c = mItem[fy * 16 + xf];
					if(c != ibg)
						px[Y * 16 + X] = c;
					else if(fill != 0)
						px[Y * 16 + X] = fill;
				}
			}
		}
	}

	/** Dragon scales: their dragon's colour, or black for a tile that is mostly black. */
	private int[] hideRamp(Look look, int slot, Tileset ts)
	{
		if(ts.getTilePixels(look.tile(slot), mItem))
		{
			int ibg = mItem[0], drawn = 0, black = 0;
			for(int p : mItem)
			{
				if(p == ibg)
					continue;
				drawn++;
				if((p & 0xffffff) == 0)
					black++;
			}
			if(drawn > 0 && black > BLACK_HIDE_ABOVE * drawn)
				return new int[] { 0xff6c91b6, 0xff363636, 0xff121212 };
		}
		return ramp(look, slot, ts, false);
	}

	private static void stampPauldrons(int[] px, int dragon, Anchor a)
	{
		if(dragon < 0 || dragon >= PAULDRONS.length)
			return;
		for(String p : PAULDRONS[dragon].split(" "))
		{
			String[] f = p.split(",");
			int x = Integer.parseInt(f[0]), y = Integer.parseInt(f[1]);
			char c = f[2].charAt(0);
			int d = DARKER_FROM.indexOf(c);
			char cr = d >= 0 ? DARKER_TO.charAt(d) : c;
			if(a.shortFrame)
			{
				// down to the short shoulders and in by one, mirrored about x = 6
				putPx(px, x - 1, y + 2, fixed(c));
				putPx(px, 13 - x, y + 2, fixed(cr));
			}
			else
			{
				putPx(px, x + a.torsoDx, y + a.torsoDy, fixed(c));
				putPx(px, 15 - x + a.torsoDx, y + a.torsoDy, fixed(cr));
			}
		}
	}

	/**
	 * A cape behind the body: a strip down each side from the shoulders to the
	 * feet, flaring at the hem, light on the left and dark on the right.  It
	 * paints only background -- and, on the right, the black drop shadow --
	 * so the hero's own arms, hair and anything held stay in front of it.
	 */
	private static void stampCape(int[] px, int bg, Anchor a, int[] ramp)
	{
		int bottom = a.feetRow >= 0 ? a.feetRow : 13 + a.torsoDy;
		for(int y = 7 + a.torsoDy; y <= bottom; y++)
		{
			capePx(px, bg, 3 + a.torsoDx, y, ramp[0], false);
			capePx(px, bg, 12 + a.torsoDx, y, ramp[2], true);
		}
		for(int y = bottom - 1; y <= bottom; y++)
		{
			capePx(px, bg, 2 + a.torsoDx, y, ramp[1], false);
			capePx(px, bg, 13 + a.torsoDx, y, ramp[2], true);
		}
	}

	private static void capePx(int[] px, int bg, int x, int y, int col, boolean overShadow)
	{
		if(x < 0 || x >= 16 || y < 0 || y >= 16)
			return;
		int p = px[y * 16 + x];
		if(p == bg || (overShadow && (p & 0xffffff) == 0))
			px[y * 16 + x] = col;
	}

	private static void putPx(int[] px, int x, int y, int col)
	{
		if(x >= 0 && x < 16 && y >= 0 && y < 16)
			px[y * 16 + x] = col;
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
