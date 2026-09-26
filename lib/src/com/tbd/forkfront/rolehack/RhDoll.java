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
 *  - Cloaks have art of their own (tools/paperdoll/cloaks.py): the core sends
 *    each worn cloak's style by the words the player sees, so a shuffled
 *    magic cloak is drawn as its appearance, never its identity.  Capes keep
 *    a signature -- the hooded cloak's hood up behind the head, the opera
 *    cloak's tall collar, the ornamental cope's trim, the tattered cape's
 *    rags -- and the robe, apron and mummy wrapping are drawn as themselves.
 *  - Helmets are drawn as their look too (tools/paperdoll/helmets.py), from
 *    the style the core sends: the fedora's brim, the conical hat, the dented
 *    pot's handle, the plume, the crest, the visor -- each drawn for the
 *    human frame and the short one, after clearing what the tile wore above
 *    the eyes.
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

	/**
	 * Helmets, as the core numbers them (rh_doll_look(), rh_helm_looks[]):
	 * "x,y,colour" on the human frame, relative to the head anchor (face x6-9,
	 * eyes on row 4), drawn after clearing rows 0-3, x3-12.
	 */
	private static final String[] HELMS = {
		null,
		"6,2,J 7,2,C 8,2,K 9,2,J 5,3,J 6,3,C 7,3,K 8,3,K 9,3,J 10,3,A 5,4,J 10,4,J 5,5,J 10,5,J",   // leather hat,
		"7,2,B 8,2,P 6,3,N 7,3,B 8,3,P 9,3,P 10,3,A 5,4,J 10,4,J",   // iron skull cap,
		"7,1,P 8,1,P 6,2,P 7,2,N 8,2,P 9,2,P 10,2,A 5,3,P 6,3,P 7,3,P 8,3,P 9,3,P 10,3,P 11,3,A",   // hard hat,
		"6,1,A 7,1,A 8,1,A 9,1,A 6,2,K 7,2,K 8,2,K 9,2,K 4,3,A 5,3,A 6,3,A 7,3,A 8,3,A 9,3,A 10,3,A 11,3,A",   // fedora,
		"8,0,E 7,1,B 8,1,E 6,2,E 7,2,N 8,2,B 9,2,E 5,3,E 6,3,B 7,3,B 8,3,B 9,3,B 10,3,E",   // conical hat,
		"6,1,B 7,1,P 8,1,P 9,1,P 6,2,B 7,2,P 8,2,A 9,2,P 10,2,J 11,1,J 12,0,J 6,3,B 7,3,B 8,3,P 9,3,P 10,3,A",   // dented pot,
		"7,1,N 8,1,N 6,2,N 7,2,I 8,2,B 9,2,N 10,2,A 6,3,B 7,3,N 8,3,I 9,3,B 10,3,A",   // crystal helmet,
		"8,0,D 9,0,D 7,1,I 8,1,D 6,2,P 7,2,N 8,2,P 9,2,P 10,2,A 6,3,N 7,3,P 8,3,P 9,3,P 10,3,A",   // plumed helmet,
		"6,2,P 7,2,N 8,2,B 9,2,P 10,2,A 6,3,N 7,3,B 8,3,N 9,3,B 10,3,A",   // etched helmet,
		"8,0,H 7,1,H 8,1,H 9,1,H 6,2,P 7,2,N 8,2,P 9,2,P 10,2,A 6,3,N 7,3,P 8,3,P 9,3,P 10,3,A",   // crested helmet,
		"6,2,P 7,2,N 8,2,P 9,2,P 10,2,A 6,3,P 7,3,P 8,3,P 9,3,P 10,3,A 6,4,P 7,4,B 8,4,B 9,4,P 6,5,P 9,5,P",   // visored helmet
	};
	/** The same looks on the short frame (tile coordinates), after clearing its hat. */
	private static final String[] HELMS_SHORT = {
		null,
		"5,5,C 6,5,K 7,5,J 4,6,J 5,6,C 6,6,K 7,6,K 8,6,J 4,7,J 8,7,J",   // leather hat,
		"5,5,B 6,5,P 7,5,A 4,6,N 5,6,B 6,6,P 7,6,P 8,6,A 4,7,J 8,7,J",   // iron skull cap,
		"5,4,P 6,4,P 4,5,P 5,5,N 6,5,P 7,5,P 8,5,A 3,6,P 4,6,P 5,6,P 6,6,P 7,6,P 8,6,P 9,6,A",   // hard hat,
		"5,4,A 6,4,A 7,4,A 5,5,K 6,5,K 7,5,K 3,6,A 4,6,A 5,6,A 6,6,A 7,6,A 8,6,A 9,6,A",   // fedora,
		"6,2,E 6,3,B 7,3,E 5,4,E 6,4,N 7,4,E 5,5,E 6,5,B 7,5,B 8,5,E 4,6,E 5,6,B 6,6,B 7,6,B 8,6,E",   // conical hat,
		"5,5,B 6,5,P 7,5,P 8,5,P 4,6,B 5,6,P 6,6,A 7,6,P 8,6,P 9,6,J 10,5,J 11,4,J",   // dented pot,
		"5,5,N 6,5,N 4,6,N 5,6,I 6,6,B 7,6,N 8,6,A",   // crystal helmet,
		"6,3,D 5,4,I 6,4,D 5,5,P 6,5,N 7,5,P 8,5,A 4,6,N 5,6,P 6,6,P 7,6,P 8,6,A",   // plumed helmet,
		"5,5,P 6,5,N 7,5,B 8,5,A 4,6,N 5,6,B 6,6,N 7,6,B 8,6,A",   // etched helmet,
		"6,3,H 5,4,H 6,4,H 5,5,P 6,5,N 7,5,P 8,5,A 4,6,N 5,6,P 6,6,P 7,6,P 8,6,A",   // crested helmet,
		"5,5,P 6,5,N 7,5,P 8,5,A 4,6,P 5,6,P 6,6,P 7,6,P 8,6,A 5,7,B 6,7,B 7,7,B",   // visored helmet
	};

	// Cloak styles, as the core numbers them (rh_doll_look(), rh_cloak_looks[]).
	private static final int C_PALL = 1, C_MANTELET = 2, C_HOOD = 3, C_SLICK = 4, C_LEATHER = 5,
		C_TATTERED = 6, C_OPERA = 7, C_COPE = 8, C_CLOTH = 9, C_ROBE = 10, C_APRON = 11, C_WRAPPING = 12;

	/** A cape's look: left strip, right strip, hem, and its signature. */
	private static final class CapeStyle
	{
		final char l, r, hem;
		char fleck, trim, glint, clasp;
		char[] rough, check, hood, collar;
		boolean ragged;

		CapeStyle(char l, char r, char hem) { this.l = l; this.r = r; this.hem = hem; }
	}

	private static final CapeStyle[] CAPES = new CapeStyle[10];
	static
	{
		CapeStyle c;
		c = CAPES[C_PALL] = new CapeStyle('O', 'F', 'G');     c.fleck = 'G';
		c = CAPES[C_MANTELET] = new CapeStyle('C', 'K', 'J'); c.rough = new char[] { 'C', 'K' };
		c = CAPES[C_HOOD] = new CapeStyle('B', 'P', 'P');     c.hood = new char[] { 'B', 'P' };
		c = CAPES[C_SLICK] = new CapeStyle('J', 'A', 'J');    c.glint = 'N';
		CAPES[C_LEATHER] = new CapeStyle('K', 'J', 'J');
		c = CAPES[C_TATTERED] = new CapeStyle('O', 'P', 'O'); c.ragged = true;
		c = CAPES[C_OPERA] = new CapeStyle('N', 'O', 'O');    c.collar = new char[] { 'N', 'O' }; c.clasp = 'D';
		c = CAPES[C_COPE] = new CapeStyle('A', 'A', 'P');     c.trim = 'P'; c.clasp = 'H';
		c = CAPES[C_CLOTH] = new CapeStyle('P', 'P', 'B');    c.check = new char[] { 'P', 'B' };
	}

	private static final int SHORT_BLADE = 1, SWORD = 2, GREAT_SWORD = 3, AXE = 4,
		PICK = 5, BLUNT = 6, STAFF = 7, POLE = 8, LAUNCHER = 9, MISSILE = 10,
		WHIP = 11, HORN = 12, CHAIN = 13;

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
		/** The artifact's own art (the core's rh_doll_arts[] + 1), or 0: bits 17-22. */
		int art(int slot)   { return (shape(slot) >> 17) & 0x3f; }
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
		int[] cuffs;                 // x,y beside each hand, for a glove's cuff; null = above it
		int feetRow = 13;            // -1: the costume hides the feet
		int[] feetCols = { 5, 6, 9, 10 };
		boolean shortFrame;          // dwarf and gnome: their own short body (dressShort)
		int[] keep = {};             // x,y pairs the layers leave alone: a beard
		int[] offPose = {};          // x,y,colour: repainted while the off hand holds something

		Anchor head(int dx, int dy)  { headDx = dx; headDy = dy; return this; }
		Anchor torso(int dx, int dy) { torsoDx = dx; torsoDy = dy; return this; }
		Anchor grips(int mx, int my, int ox, int oy) { mainX = mx; mainY = my; offX = ox; offY = oy; return this; }
		Anchor hands(int... xy) { hands = xy; return this; }
		Anchor cuffs(int... xy) { cuffs = xy; return this; }
		Anchor feet(int row, int... cols) { feetRow = row; feetCols = cols; return this; }
		Anchor robe() { feetRow = -1; return this; }
		Anchor shortFrame(int... keepXY) { shortFrame = true; keep = keepXY; return this; }
		/** Layers leave these x,y pairs alone (a beard; a flask held up by the head). */
		Anchor keep(int... xy) { keep = xy; return this; }
		/**
		 * The tile's off arm, repainted when a shield or a second weapon is drawn:
		 * x, y, colour -- '~' background, 'L' the hero's skin, else a palette letter.
		 */
		Anchor offPose(int... xyc) { offPose = xyc; return this; }
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
		// apothecary: Claude's tile (2026-09-26, Lucas: "let's use it"), on vanilla's
		// frame, holding a flask up to the light in the off hand.  The flask's neck (12,3)
		// survives a helmet; gloves go on the main hand and either off-hand place; a
		// shield or a second weapon brings the arm down to the usual grip (11,10).
		pair(702, new Anchor().hands(4, 10, 12, 5, 11, 10).cuffs(4, 9, 12, 6, 11, 9).keep(12, 3)
		                      .offPose(11, 7, '~', 12, 6, '~', 12, 5, '~', 12, 4, '~', 13, 4, '~',
		                               12, 3, '~', 13, 5, '~', 11, 8, 'O', 11, 9, 'L', 11, 10, 'L'));
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
	// relative to the off-hand grip: any shield whose look the core does not name
	private static final Sprite S_SHIELD = new Sprite(-2, -1, "lmdA", -1, -1, "mWdA", 0, -1, "mmdA", 1, 0, "dA");
	// Shields, as the core numbers them (rh_doll_look(), rh_shield_looks[]), drawn by their
	// look (tools/paperdoll/shields.py) at the off-hand grip -- full size on every body,
	// dwarves and gnomes too (Lucas, 2026-09-26).
	private static final Sprite[] SHIELDS = {
		null,
		new Sprite(-2, -1, "CKJA", -1, -1, "KKJA", 0, -1, "KJJA", 1, 0, "JA"),   // wooden shield
		new Sprite(-2, -1, "NNNA", -1, -1, "BNGA", 0, -1, "BNGA", 1, 0, "NA"),   // blue and green shield
		new Sprite(-2, -1, "KJJA", -1, -1, "PNPA", 0, -1, "NNPA", 1, 0, "PA"),   // white-handed shield
		new Sprite(-2, -1, "KKJA", -1, -1, "DADA", 0, -1, "PDPA", 1, 0, "PA"),   // red-eyed shield
		new Sprite(-3, -1, "NNNOA", -2, -1, "NPPOA", -1, -1, "NPPOA", 0, -1, "NPPOA", 1, 0, "NPOA", 2, 1, "OA"),   // large shield
		new Sprite(-3, 0, "BB", -2, -1, "BKKBA", -1, -1, "BKJBA", 0, -1, "BJJBA", 1, 0, "BBA"),   // large round shield
		new Sprite(-2, -1, "NNOA", -1, -1, "NNZA", 0, -1, "NZOA", 1, 0, "OA"),   // polished silver shield
	};
	// Gloves and boots, as the core numbers them (rh_glove_looks[], rh_boot_looks[]), drawn by
	// their look (tools/paperdoll/gloves_boots.py; Lucas, 2026-09-26).  Gloves: the hand, then the
	// cuff on the arm beside it.  Boots: the feet (outer, inner), then the leg row above them for a
	// tall boot, and an accent on the first pixel of each leg there.  '.' is none.
	private static final String[] GLOVE_LOOKS = { null, "J.", "CC", "KJ", "NO" };
	private static final String[] BOOT_LOOKS = { null, "JK..", "PN..", "KL..", "JJK.", "RFF.", "JJG.", "KKC.", "KKKH", "QRQ.", "OON." };

	private static Sprite shieldSprite(Look look)
	{
		int st = look.shape(SHIELD) & 0xff;
		return st > 0 && st < SHIELDS.length ? SHIELDS[st] : S_SHIELD;
	}
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
	// a morning star: a spiked ball on a chain, as vanilla's floor tile draws it (Lucas, 2026-09-26)
	private static final Sprite S_CHAIN = new Sprite(
		-8, -3, "d", -7, -3, "lmd", -6, -4, "dmd", -5, -2, "X", -4, -1, "X", -3, 0, "K", -2, 0, "J", -1, 0, "J");

	// Artifacts with art of their own (Lucas, 2026-09-26; tools/paperdoll/artifacts*.py,
	// printed by artgen.py), numbered as the core's rh_doll_arts[]: artilist.h's order.
	private static final int ART_EYES = 26, ART_MITRE = 27, ART_AETHIOPICA = 33;
	/** Held artifacts, relative to the grip; null for the worn ones. */
	private static final Sprite[] ART_HELD = {
		null,
		new Sprite(-7, 0, "N", -6, 0, "N", -5, 0, "N", -4, 0, "M", -3, 0, "N", -2, 0, "M", -1, -2, "HHBHH", 1, 0, "H"),   // 1 Excalibur
		new Sprite(-7, 0, "R", -6, 0, "D", -5, 0, "R", -4, 0, "Q", -3, 0, "D", -2, 0, "R", -1, -1, "QDQ", 1, 0, "D"),   // 2 Stormbringer
		new Sprite(-7, -3, "B", -6, -2, "NOO", -5, -2, "OWW", -4, -2, "B.J", -3, 0, "J", -2, 0, "J", -1, 0, "K"),   // 3 Mjollnir
		new Sprite(-7, -2, "N.J", -6, -3, "NOOJ", -5, -3, "OWWJW", -4, -2, "W.J", -3, 0, "J", -2, 0, "J", -1, 0, "J"),   // 4 Cleaver
		new Sprite(-4, 0, "S", -3, -1, "DR", -2, 0, "G", -1, -1, "RJR"),   // 5 Grimtooth
		new Sprite(-7, 0, "N", -6, -1, "BN", -5, 0, "N", -4, -1, "BN", -3, 0, "N", -2, 0, "O", -1, -1, "FGF", 1, 0, "F"),   // 6 Orcrist
		new Sprite(-4, 0, "B", -3, -1, "BN", -2, 0, "N", -1, -1, "FGF"),   // 7 Sting
		new Sprite(-4, -1, "I.I", -3, 0, "N", -2, 0, "M", -1, -1, "QIQ"),   // 8 Magicbane
		new Sprite(-7, 0, "N", -6, -1, "NB", -5, 0, "B", -4, 0, "N", -3, 0, "B", -2, 0, "B", -1, -1, "PBP", 1, 0, "P"),   // 9 Frost Brand
		new Sprite(-7, 0, "H", -6, 0, "C", -5, 0, "H", -4, -1, "DC", -3, 0, "D", -2, 0, "D", -1, -1, "RDR", 1, 0, "D"),   // 10 Fire Brand
		new Sprite(-7, 0, "N", -6, 0, "N", -5, 0, "N", -4, 0, "N", -3, 0, "M", -2, -2, "D.M.D", -1, -2, "DKKKD", 1, 0, "K"),   // 11 Dragonbane
		new Sprite(-7, 0, "H", -6, -1, "NN", -5, -1, "ZZ", -4, 0, "H", -3, 0, "O", -2, 0, "O", -1, 0, "O"),   // 12 Demonbane
		new Sprite(-7, -1, "N", -6, -1, "Z", -5, 0, "N", -4, 0, "Z", -3, 0, "N", -2, 0, "Z", -1, -1, "IEI", 1, 0, "I"),   // 13 Werebane
		new Sprite(-7, -1, "Z", -6, -1, "Y", -5, 0, "Z", -4, -1, "NZ", -3, 0, "Y", -2, 0, "Z", -1, -1, "TST", 1, 0, "S"),   // 14 Grayswandir
		new Sprite(-7, 0, "Z", -6, -1, "YZ", -5, -1, "YO", -4, -1, "YZ", -3, -1, "YO", -2, -1, "YO", -1, -2, "JKKJ", 1, 0, "K"),   // 15 Giantslayer
		new Sprite(-7, -1, "YYW", -6, -1, "WWS", -5, -1, "WSS", -4, 0, "J", -3, 0, "J", -2, 0, "J", -1, 0, "J"),   // 16 Ogresmasher
		new Sprite(-9, -3, "S", -8, -4, "ZYX", -7, -5, "SYXWS", -6, -4, "XWW", -5, -3, "S.X", -4, -1, "X", -3, 0, "K", -2, 0, "J", -1, 0, "J"),   // 17 Trollsbane
		new Sprite(-9, 0, "N", -8, 0, "M", -7, 0, "N", -6, 0, "M", -5, 0, "N", -4, 0, "M", -3, 0, "N", -2, 0, "M", -1, -1, "SQS", 1, 0, "S"),   // 18 Vorpal Blade
		new Sprite(-7, 1, "N", -6, 0, "N", -5, 0, "O", -4, 0, "N", -3, 0, "O", -2, 0, "N", -1, -1, "AHA", 1, 0, "R", 2, 0, "N"),   // 19 Snickersnee
		new Sprite(-8, 0, "H", -7, -1, "HNH", -6, 0, "H", -5, 0, "N", -4, 0, "H", -3, 0, "H", -2, 0, "N", -1, -1, "CHC", 1, 0, "H"),   // 20 Sunsword
		new Sprite(-3, -2, "NB", -2, -3, "BBP", -1, -2, "PE"),   // 21 Orb of Detection
		new Sprite(-3, -3, "D.D", -2, -3, "DCD", -1, -2, "D"),   // 22 Heart of Ahriman
		new Sprite(-8, -1, "H.H", -7, -1, "HIH", -6, 0, "H", -5, 0, "H", -4, 0, "H", -3, 0, "H", -2, 0, "H", -1, 0, "K"),   // 23 Sceptre of Might
		new Sprite(-8, -1, "GK", -7, 0, "G", -6, 0, "JG", -5, 0, "G", -4, -1, "GJ", -3, 0, "G", -2, 0, "JG", -1, 0, "J", 1, 0, "J", 2, 0, "J"),   // 24 Staff of Aesculapius
		new Sprite(-5, -1, "H", -4, -2, "HBH", -3, -2, "HNH", -2, -1, "H", -1, 0, "H"),   // 25 Magic Mirror of Merlin
		null,   // 26 Eyes of the Overworld (worn)
		null,   // 27 Mitre of Holiness (worn)
		new Sprite(-5, -1, "N", -4, -2, "Z", -3, -2, "Z", -2, -2, "N", -1, -2, "Z", 0, -2, "Z", 1, -2, "Z", 2, -2, "N", 3, -1, "Z"),   // 28 Longbow of Diana
		new Sprite(-5, 0, "H", -4, -1, "HH", -3, -1, "HH", -2, 0, "H", -1, 0, "K", 1, -1, "H.H", 2, 0, "H"),   // 29 Master Key of Thievery
		new Sprite(-10, 0, "N", -9, 0, "O", -8, 0, "D", -7, 0, "N", -6, 0, "O", -5, 0, "D", -4, 0, "N", -3, 0, "O", -2, -1, "AHA", -1, 0, "R", 1, 0, "R"),   // 30 Tsurugi of Muramasa
		new Sprite(-4, -3, "ZNN", -3, -3, "RRS", -2, -3, "HRR", -1, -3, "QQR"),   // 31 Platinum Yendorian Express Card
		new Sprite(-3, -2, "NH", -2, -3, "HHK", -1, -2, "KJ"),   // 32 Orb of Fate
		null,   // 33 Eye of the Aethiopica (worn)
		new Sprite(-5, 0, "H", -4, 0, "N", -3, 0, "H", -2, 0, "N", -1, 0, "H"),   // 34 Lapis Philosophorum
	};
	private static final String ART_MITRE_HUMAN = "7,0,N 8,0,M 6,1,N 7,1,H 8,1,N 9,1,M 6,2,H 7,2,H 8,2,H 9,2,H 5,3,N 6,3,N 7,3,H 8,3,N 9,3,M 10,3,A";
	private static final String ART_MITRE_SHORT = "6,2,N 5,3,N 6,3,H 7,3,M 5,4,H 6,4,H 7,4,H 5,5,N 6,5,H 7,5,M 4,6,N 5,6,N 6,6,H 7,6,N 8,6,M";
	private static final Sprite S_EYES = new Sprite(4, 5, "AHNHNA"), S_EYES_SHORT = new Sprite(7, 4, "HNHNH");
	private static final Sprite S_AETHIOPICA = new Sprite(8, 6, "HEH");
	private static final char AETHIOPICA_SHORT = 'E';
	// The short frame's own sprites, in tile coordinates (tools/paperdoll/short.py).
	// The helmet clears the tile's hat and cheek pieces, then a smaller dome.
	private static final Sprite S_HELMET_SHORT = new Sprite(
		0, 2, "~~~~~~~~~", 1, 2, "~~~~~~~~~", 2, 2, "~~~~~~~~~", 3, 2, "~~~~~~~~~", 4, 2, "~~~~~~~~~",
		5, 2, "~~~lmA~~~", 6, 2, "~~lmmdA~~", 7, 4, "~...~");
	private static final Sprite S_EYEWEAR_SHORT = new Sprite(7, 4, "dmmmd");
	private static final Sprite S_FRONT_SHORT = new Sprite(
		9, 3, "lmmmmmd", 10, 3, "lAmmmAd", 11, 5, "mmm", 12, 4, "lmmmd");
	// Front garments, on the human frame (torso-relative) and the short frame.
	private static final Sprite S_ROBE = new Sprite(
		7, 5, "CCJKKK", 8, 4, "CCCJKKKK", 9, 4, "CACJKKAK", 10, 5, "CCJKKK", 11, 4, "CCCJKKKK", 12, 4, "CCCJKKKK");
	private static final Sprite S_ROBE_SHORT = new Sprite(
		9, 3, "CCJKKKK", 10, 3, "CAJKKAK", 11, 5, "CJK", 12, 4, "CCJKK");
	private static final Sprite S_APRON = new Sprite(
		7, 6, "F..R", 8, 6, "FFFR", 9, 6, "FFFR", 10, 6, "FFFR", 11, 6, "FFFR", 12, 6, "FFFR");
	private static final Sprite S_APRON_SHORT = new Sprite(9, 5, "FFR", 10, 5, "FFR", 11, 5, "FFR", 12, 5, "FFR");

	/** The short torso: rows 9-11.  The arm separators at (4,10), (8,10) stay black. */
	private static final int[] SHORT_Y = { 9, 10, 11 }, SHORT_X0 = { 3, 3, 5 }, SHORT_X1 = { 9, 9, 7 };

	/** Anything that is not a weapon: a thing held up in the hand. */
	private static final Sprite S_HELD = new Sprite(-2, -1, "lm", -1, -1, "md");

	/** What the hand holds: an artifact's own art once its name is known, else its family's. */
	private static Sprite heldSprite(Look look, int slot)
	{
		int art = look.art(slot);
		Sprite s = art > 0 && art < ART_HELD.length ? ART_HELD[art] : null;
		return s != null ? s : weaponSprite(look.shape(slot));
	}

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
		case CHAIN:       return S_CHAIN;
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
		// first, before any layer: the tile's off-hand pose gives way to a shield or
		// a second weapon, so armour then covers the lowered arm as it would any other
		if(look.draws(SHIELD) || look.has(OFFHAND))
			for(int i = 0; i + 2 < a.offPose.length; i += 3)
			{
				char c = (char)a.offPose[i + 2];
				putPx(px, a.offPose[i], a.offPose[i + 1], c == '~' ? bg : c == 'L' ? TONES[look.tone] : fixed(c));
			}
		boolean front = look.has(CLOAK) && (look.shape(CLOAK) & FRONT) != 0;
		boolean cape = look.draws(CLOAK) && !front;
		boolean covered = look.has(SUIT) || front;
		if(cape)
			stampCloakBehind(px, bg, look, a, ts);
		if(look.draws(SHIRT) && !covered)
			stampBody(px, bg, look, SHIRT, a, ts, S_SHIRT);
		if(look.draws(SUIT))
			stampBody(px, bg, look, SUIT, a, ts, S_SUIT);
		if(look.draws(CLOAK) && front)
			stampCloakFront(px, bg, look, a, ts);
		if(cape)
		{
			CapeStyle st = capeStyle(look);
			char clasp = st == null ? 'H' : st.clasp;
			if(clasp != 0)
				putPx(px, 7 + a.torsoDx, 7 + a.torsoDy, fixed(clasp));
		}
		if(look.draws(SUIT) && (look.shape(SUIT) & DRAGON) != 0)
			stampPauldrons(px, (look.shape(SUIT) >> 12) & 0xf, a);
		if(look.draws(AMULET) && !covered)
			stamp(px, bg, look.art(AMULET) == ART_AETHIOPICA ? S_AETHIOPICA : S_AMULET, a.torsoDx, a.torsoDy,
			      false, ramp(look, AMULET, ts, false));
		if(look.draws(BOOTS) && a.feetRow >= 0)
			stampBoots(px, bg, look, a, ts);
		if(look.draws(GLOVES))
			stampGloves(px, bg, look, a, ts,
			            a.hands != null ? a.hands : new int[] { a.mainX, a.mainY, a.offX, a.offY });
		if(look.draws(HELMET))
			stampHelmet(px, bg, look, a, ts);
		if(look.draws(EYEWEAR))
			stamp(px, bg, look.art(EYEWEAR) == ART_EYES ? S_EYES : S_EYEWEAR, a.headDx, a.headDy, false,
			      ramp(look, EYEWEAR, ts, false));
		if(look.draws(SHIELD))
			stamp(px, bg, shieldSprite(look), a.offX, a.offY, false, ramp(look, SHIELD, ts, false));
		if(look.has(WEAPON))
			stamp(px, bg, heldSprite(look, WEAPON), a.mainX, a.mainY, false,
			      ramp(look, WEAPON, ts, false));
		if(look.has(OFFHAND))
			stamp(px, bg, heldSprite(look, OFFHAND), a.offX, a.offY, true,
			      ramp(look, OFFHAND, ts, false));
	}

	/**
	 * Gear on the short frame (dwarf, gnome): the same items, drawn to that body
	 * -- a short suit mask around the beard, a helmet that replaces the tile's
	 * own hat, a cape at x2 and x10 with no clasp (the beard covers the neck),
	 * pauldrons on the lower shoulders, and shields and weapons at full size:
	 * pound for pound the small races are the strong ones (Lucas, 2026-09-26;
	 * weapons were squashed to two-thirds and shields were bucklers before).
	 */
	private void dressShort(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		boolean front = look.has(CLOAK) && (look.shape(CLOAK) & FRONT) != 0;
		boolean cape = look.draws(CLOAK) && !front;
		boolean covered = look.has(SUIT) || front;
		if(cape)
			stampCloakBehind(px, bg, look, a, ts);
		if(look.draws(SHIRT) && !covered)
			stampBodyShort(px, bg, look, SHIRT, a, ts, false);
		if(look.draws(SUIT))
			stampBodyShort(px, bg, look, SUIT, a, ts, true);
		if(look.draws(CLOAK) && front)
			stampCloakFront(px, bg, look, a, ts);
		if(look.draws(SUIT) && (look.shape(SUIT) & DRAGON) != 0)
			stampPauldrons(px, (look.shape(SUIT) >> 12) & 0xf, a);
		if(look.draws(AMULET) && !covered && !a.keeps(6, 10))
			putPx(px, 6, 10, look.art(AMULET) == ART_AETHIOPICA ? fixed(AETHIOPICA_SHORT)
			                                                       : ramp(look, AMULET, ts, false)[1]);
		if(look.draws(BOOTS))
			stampBoots(px, bg, look, a, ts);
		if(look.draws(GLOVES))
			stampGloves(px, bg, look, a, ts, new int[] { a.mainX, a.mainY, a.offX, a.offY });
		if(look.draws(HELMET))
			stampHelmet(px, bg, look, a, ts);
		if(look.draws(EYEWEAR))
			stamp(px, bg, look.art(EYEWEAR) == ART_EYES ? S_EYES_SHORT : S_EYEWEAR_SHORT, 0, 0, false,
			      ramp(look, EYEWEAR, ts, false));
		if(look.draws(SHIELD))
			stamp(px, bg, shieldSprite(look), a.offX, a.offY, false, ramp(look, SHIELD, ts, false));
		if(look.has(WEAPON))
			stamp(px, bg, heldSprite(look, WEAPON), a.mainX, a.mainY, false,
			      ramp(look, WEAPON, ts, false));
		if(look.has(OFFHAND))
			stamp(px, bg, heldSprite(look, OFFHAND), a.offX, a.offY, true,
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

	/**
	 * A helmet, drawn as its look: clear what the tile wears above the eyes
	 * (hair, hats, hoods; on the short frame the tile's own hat and cheek
	 * pieces), then draw the style.  An unknown look (another tileset) falls
	 * back to the tinted dome.
	 */
	/**
	 * Gloves as their look: its hand colour on each hand, its cuff on the arm
	 * beside it (the anchor's cuffs, else the pixel above the hand).  A look the
	 * core does not name keeps the old rule: the tile's own colour on the hands.
	 */
	private void stampGloves(int[] px, int bg, Look look, Anchor a, Tileset ts, int[] h)
	{
		int st = look.shape(GLOVES) & 0xff;
		String g = st > 0 && st < GLOVE_LOOKS.length ? GLOVE_LOOKS[st] : null;
		int hand = g != null ? fixed(g.charAt(0)) : ramp(look, GLOVES, ts, false)[1];
		for(int i = 0; i + 1 < h.length; i += 2)
		{
			recolour(px, bg, h[i], h[i + 1], hand);
			if(g == null || g.charAt(1) == '.')
				continue;
			boolean own = a.cuffs != null && i + 1 < a.cuffs.length;
			recolour(px, bg, own ? a.cuffs[i] : h[i], own ? a.cuffs[i + 1] : h[i + 1] - 1, fixed(g.charAt(1)));
		}
	}

	/**
	 * Boots as their look: each foot's first pixel the outer colour, the rest
	 * the inner; a tall boot also takes the leg row just above the feet, with
	 * its accent (a buckle) on the first pixel of each leg there.  A look the
	 * core does not name keeps the old rule: the tile's own colour on the feet.
	 */
	private void stampBoots(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		int st = look.shape(BOOTS) & 0xff;
		String b = st > 0 && st < BOOT_LOOKS.length ? BOOT_LOOKS[st] : null;
		int tint = b == null ? ramp(look, BOOTS, ts, false)[1] : 0;
		for(int i = 0; i < a.feetCols.length; i++)
		{
			boolean first = i == 0 || a.feetCols[i] != a.feetCols[i - 1] + 1;
			recolour(px, bg, a.feetCols[i], a.feetRow, b == null ? tint : fixed(b.charAt(first ? 0 : 1)));
		}
		if(b == null || b.charAt(2) == '.' || a.feetRow < 1)
			return;
		int y = a.feetRow - 1;
		int x0 = a.shortFrame ? 3 : 4 + a.torsoDx, x1 = a.shortFrame ? 9 : 11 + a.torsoDx;
		boolean prev = false;
		for(int x = Math.max(0, x0); x <= Math.min(15, x1); x++)
		{
			int p = px[y * 16 + x];
			boolean leg = p != bg && (p & 0xffffff) != 0;
			if(leg)
				px[y * 16 + x] = fixed(!prev && b.charAt(3) != '.' ? b.charAt(3) : b.charAt(2));
			prev = leg;
		}
	}

	private void stampHelmet(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		boolean mitre = look.art(HELMET) == ART_MITRE;
		int st = look.shape(HELMET) & 0xff;
		String[] set = a.shortFrame ? HELMS_SHORT : HELMS;
		if(!mitre && (st <= 0 || st >= set.length))
		{
			if(a.shortFrame)
				stamp(px, bg, S_HELMET_SHORT, 0, 0, false, ramp(look, HELMET, ts, false));
			else
				stamp(px, bg, S_HELMET, a.headDx, a.headDy, false, ramp(look, HELMET, ts, false));
			return;
		}
		int dx = a.shortFrame ? 0 : a.headDx, dy = a.shortFrame ? 0 : a.headDy;
		if(a.shortFrame)
		{
			for(int y = 0; y <= 6; y++)
				for(int x = 2; x <= 10; x++)
					px[y * 16 + x] = bg;
			putPx(px, 4, 7, bg);
			putPx(px, 8, 7, bg);
		}
		else
			for(int y = 0; y <= 3; y++)
				for(int x = 3; x <= 12; x++)
					if(!a.keeps(x + dx, y + dy))
						putPx(px, x + dx, y + dy, bg);
		String spec = mitre ? (a.shortFrame ? ART_MITRE_SHORT : ART_MITRE_HUMAN) : set[st];
		for(String p : spec.split(" "))
		{
			String[] f = p.split(",");
			putPx(px, Integer.parseInt(f[0]) + dx, Integer.parseInt(f[1]) + dy, fixed(f[2].charAt(0)));
		}
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
	private static CapeStyle capeStyle(Look look)
	{
		int st = look.shape(CLOAK) & 0xff;
		return st > 0 && st < CAPES.length ? CAPES[st] : null;
	}

	/**
	 * A cape behind the body, in its style: strips down both sides (x3 and x12
	 * on the human frame, x2 and x10 on the short one) from the shoulders to
	 * the feet, a hem, and the style's signature.  Paints only background,
	 * and on the right the drop shadow.  An unknown style (another tileset)
	 * falls back to plain strips in the tile's colour.
	 */
	private void stampCloakBehind(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		CapeStyle st = capeStyle(look);
		if(st == null)
		{
			if(a.shortFrame)
				stampCapeShort(px, bg, ramp(look, CLOAK, ts, true));
			else
				stampCape(px, bg, a, ramp(look, CLOAK, ts, true));
			return;
		}
		int lx, rx, top, bottom, hy, hx0, hx1;
		if(a.shortFrame)
		{
			lx = 2; rx = 10; top = 9; bottom = 13;
			hy = 2; hx0 = 3; hx1 = 9;
		}
		else
		{
			lx = 3 + a.torsoDx; rx = 12 + a.torsoDx; top = 7 + a.torsoDy;
			bottom = a.feetRow >= 0 ? a.feetRow : 13 + a.torsoDy;
			hy = 1 + a.headDy; hx0 = 5 + a.headDx; hx1 = 10 + a.headDx;
		}
		for(int y = top; y <= bottom; y++)
		{
			char cl = st.l, cr = st.r;
			if(st.rough != null)
				cl = st.rough[y % 2];
			if(st.check != null)
			{
				cl = st.check[y % 2];
				cr = st.check[(y + 1) % 2];
			}
			if(st.fleck != 0 && y % 3 == 0)
				cl = st.fleck;
			if(st.ragged && y == bottom && y % 2 == 1)
				continue;
			capePx(px, bg, lx, y, fixed(cl), false);
			capePx(px, bg, rx, y, fixed(cr), true);
		}
		for(int y = bottom - 1; y <= bottom; y++)
		{
			if(st.ragged && y == bottom)
				continue;
			capePx(px, bg, lx - 1, y, fixed(st.hem), false);
			capePx(px, bg, rx + 1, y, fixed(st.r == 'A' ? st.l : st.hem), true);
		}
		if(st.trim != 0)
			for(int y = top; y <= bottom; y++)
			{
				capePx(px, bg, lx - 1, y, fixed(st.trim), false);
				capePx(px, bg, rx + 1, y, fixed(st.trim), true);
			}
		if(st.glint != 0 && top + 2 < 16 && lx >= 0 && px[(top + 2) * 16 + lx] == fixed(st.l))
			px[(top + 2) * 16 + lx] = fixed(st.glint);
		if(st.hood != null)             // the hood, up behind the head
		{
			// On dwarves and gnomes it hugs the head: their heads start on row 4, and
			// a hood from row 2 floated over them (Lucas picked two rows lower).
			int hoodY = a.shortFrame ? 4 : hy, hoodLen = a.shortFrame ? 3 : 5;
			for(int x = hx0 + 1; x < hx1; x++)
				capePx(px, bg, x, hoodY, fixed(2 * x < hx0 + hx1 ? st.hood[0] : st.hood[1]), false);
			for(int y = hoodY + 1; y <= hoodY + hoodLen; y++)
			{
				capePx(px, bg, hx0, y, fixed(st.hood[0]), false);
				capePx(px, bg, hx1, y, fixed(st.hood[1]), true);
			}
		}
		if(st.collar != null)           // a tall collar, flaring out behind the head
		{
			int cy = hy + 3;
			capePx(px, bg, hx0 - 1, cy, fixed(st.collar[0]), false);
			capePx(px, bg, hx0, cy + 1, fixed(st.collar[0]), false);
			capePx(px, bg, hx0, cy + 2, fixed(st.collar[0]), false);
			capePx(px, bg, hx1 + 1, cy, fixed(st.collar[1]), true);
			capePx(px, bg, hx1, cy + 1, fixed(st.collar[1]), true);
			capePx(px, bg, hx1, cy + 2, fixed(st.collar[1]), true);
		}
	}

	/**
	 * A garment worn in front, drawn as itself: a robe with its seam, an apron
	 * with its straps and bib, or bandages round the body, arms and legs.
	 * Anything else marked front (another tileset) keeps the placeholder.
	 */
	private void stampCloakFront(int[] px, int bg, Look look, Anchor a, Tileset ts)
	{
		int st = look.shape(CLOAK) & 0xff;
		int dx = a.shortFrame ? 0 : a.torsoDx, dy = a.shortFrame ? 0 : a.torsoDy;
		int bottom = a.feetRow >= 0 ? a.feetRow - 1 : 12 + dy;
		if(st == C_WRAPPING)
		{
			int top = a.shortFrame ? 9 : 7 + dy;
			for(int y = top; y <= bottom && y < 16; y++)
				for(int x = 0; x < 16; x++)
				{
					if(a.keeps(x, y) || (!a.shortFrame && (x < 3 + dx || x > 12 + dx)))
						continue;
					int p = px[y * 16 + x];
					if(p == bg || (p & 0xffffff) == 0)
						continue;
					px[y * 16 + x] = fixed(y % 2 == 1 ? 'N' : 'O');
				}
			int[] spots = a.shortFrame ? new int[] { 5, 10, 7, 12 } : new int[] { 6 + dx, 9 + dy, 9 + dx, 11 + dy };
			for(int i = 0; i < spots.length; i += 2)
			{
				int x = spots[i], y = spots[i + 1];
				if(x >= 0 && x < 16 && y >= 0 && y < 16
				   && (px[y * 16 + x] == fixed('N') || px[y * 16 + x] == fixed('O')))
					px[y * 16 + x] = fixed('D');
			}
			return;
		}
		Sprite s = st == C_ROBE ? (a.shortFrame ? S_ROBE_SHORT : S_ROBE)
		         : st == C_APRON ? (a.shortFrame ? S_APRON_SHORT : S_APRON) : null;
		if(s == null)
		{
			if(a.shortFrame)
				stampKeep(px, S_FRONT_SHORT, a, ramp(look, CLOAK, ts, true));
			else
				stamp(px, bg, S_CLOAK, a.torsoDx, a.torsoDy, false, ramp(look, CLOAK, ts, true));
			return;
		}
		for(int r = 0; r < s.rows.length; r++)
			for(int i = 0; i < s.rows[r].length(); i++)
			{
				char c = s.rows[r].charAt(i);
				int x = s.x0s[r] + i + dx, y = s.ys[r] + dy;
				if(c == '.' || a.keeps(x, y) || (y > bottom && a.feetRow >= 0))
					continue;
				putPx(px, x, y, fixed(c));
			}
	}

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
