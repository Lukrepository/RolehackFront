package com.tbd.forkfront.rolehack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The command vocabulary of the Rolehack interface, as data.
 *
 * Every key string here is in the notation {@link com.tbd.forkfront.Cmd.KeySequnece}
 * already parses: {@code ^X} for control, {@code M-x} for meta, {@code \e} for
 * escape and {@code \n} for return.  Nothing in this file talks to the game; the
 * overlay turns an {@link Item} into a key sequence and hands it to the existing
 * command pipeline.
 *
 * Source: design_handoff_rolehack_mobile/README.md, "Command groups" and "Fans".
 * The handoff notes four bindings that were wrong in an earlier draft and are
 * corrected here -- Overview is M-O, Explore mode is #exploremode, Quit is #quit,
 * and C (#call) and M-n (#name) are two different commands.
 */
public final class RhCommands
{
	private RhCommands() {}

	// ____________________________________________________________________________________
	public static final class Item
	{
		public final String word;
		public final String key;
		/** Face override; null means the group default (G90). */
		public final int[] face;
		/**
		 * A fuller form of the same command, sent on a long press.  Engrave's
		 * `E?` is the case this exists for: a tap starts engraving, a hold goes
		 * straight to the menu of things you could write with.  Null when the
		 * command has no second form.
		 */
		public final String altKey;

		public Item(String word, String key)
		{
			this(word, key, null, null);
		}

		public Item(String word, String key, int[] face)
		{
			this(word, key, face, null);
		}

		public Item(String word, String key, int[] face, String altKey)
		{
			this.word = word;
			this.key = key;
			this.face = face;
			this.altKey = altKey;
		}

		public boolean hasAlt()
		{
			return altKey != null && altKey.length() > 0;
		}

		/** What the key flash shows, and what "keys only" label mode displays. */
		public String rawKey()
		{
			return key;
		}
	}

	public static final class Group
	{
		public final String id;
		public final String title;
		public final Item[] items;

		Group(String id, String title, Item[] items)
		{
			this.id = id;
			this.title = title;
			this.items = items;
		}

		public int count()
		{
			return items.length;
		}
	}

	private static Item i(String word, String key)              { return new Item(word, key); }
	private static Item i(String word, String key, int[] face)   { return new Item(word, key, face); }
	private static Item alt(String word, String key, String altKey)
	{
		return new Item(word, key, null, altKey);
	}

	// ____________________________________________________________________________________
	// The eleven groups.  A few keys appear in two groups ([ ) = " * M-e C M-n):
	// intentional, per the handoff -- they answer a question and perform a role,
	// and the player will look for them in either place.

	public static final Group INVENT = new Group("invent", "INVENT", new Item[] {
		i("Inventory", "i"), i("By type", "I"), i("Gems", "I*"), i("Blessed", "IB"),
		i("Uncursed", "IU"), i("Cursed", "IC"), i("Unknown B/U/C", "IX"), i("Unpaid", "Iu"),
		i("Count gold", "$"), i("Adjust letters", "M-a"), i("Call/name", "C"), i("Name type", "M-n"),
	});

	public static final Group WEAR = new Group("wear", "WEAR", new Item[] {
		i("Wear armor", "W"), i("Take off", "T", RhTheme.OFF90), i("Take off all", "A", RhTheme.OFF90),
		i("Put on", "P"), i("Remove", "R", RhTheme.OFF90),
		i("Worn armor", "["), i("Worn rings", "="), i("Worn amulet", "\""),
	});

	public static final Group WEAPON = new Group("weapon", "WEAPON", new Item[] {
		i("Wield", "w"), i("Unwield", "w-", RhTheme.OFF90), i("Swap", "x"),
		i("Two-weapon", "X", RhTheme.A90), i("Ready quiver", "Q"),
		i("Wielded", ")"), i("All equipment", "*"), i("Enhance", "M-e"),
	});

	public static final Group DROP = new Group("drop", "DROP", new Item[] {
		i("Drop one", "d"), i("Drop type", "D"), i("Pick from menu", "Dm"), i("Review first", "Di"),
		i("Blessed", "DB"), i("Uncursed", "DU"), i("Cursed", "DC"), i("Unknown", "DX"),
		i("Unpaid", "Du"), i("Drop all", "Da"), i("Tip container", "M-T"),
	});

	/**
	 * OFFENSE's drawer.  Throw, Zap and Cast live here rather than on the radial:
	 * the radial carries the three that take a direction, and the rest of the
	 * offensive vocabulary is one level deeper.
	 */
	public static final Group FIGHT = new Group("fight", "OFFENSE", new Item[] {
		// Quiver belongs with fighting at range: long-press OFFENSE and change what
		// f fires (Lucas, 2026-09-23).  It stays in the EQUIP drawer as well.
		i("Quiver", "Q"),
		i("Fight", "F", RhTheme.R90), i("Kick", "^D", RhTheme.R90), i("Fire", "f"),
		i("Throw", "t"), i("Zap wand", "z"), i("Cast spell", "Z"),
		i("Turn undead", "M-t"),
	});

	/**
	 * EQUIP's drawer: everything worn or wielded in one place.
	 *
	 * Weapon-swapping moved here from the left thumb -- the standalone SWAP hub is
	 * gone and ATTACK no longer owns steel, so wield, swap and two-weapon belong
	 * with the armor and accessory commands rather than with violence.
	 */
	public static final Group EQUIP = new Group("equip", "EQUIP", new Item[] {
		i("Wield", "w"), i("Unwield", "w-", RhTheme.OFF90), i("Swap", "x"),
		i("Two-weapon", "X", RhTheme.A90), i("Ready quiver", "Q"),
		i("Wear armor", "W"), i("Take off", "T", RhTheme.OFF90),
		i("Take off all", "A", RhTheme.OFF90), i("Put on", "P"),
		i("Remove", "R", RhTheme.OFF90),
		i("Inventory", "i"), i("By type", "I"), i("Adjust letters", "M-a"),
		i("Worn armor", "["), i("Worn rings", "="), i("Worn amulet", "\""),
		i("Wielded", ")"), i("All equipment", "*"), i("Enhance", "M-e"),
		// The Invent group had been EQUIP's drawer, and nothing else opens it now
		// that the bottom row is gone.  Its filtered views come along rather than
		// quietly becoming unreachable.
		i("Gems", "I*"), i("Blessed", "IB"), i("Uncursed", "IU"), i("Cursed", "IC"),
		i("Unknown B/U/C", "IX"), i("Unpaid", "Iu"), i("Count gold", "$"),
	});

	public static final Group USE = new Group("use", "USE", new Item[] {
		i("Eat", "e"), i("Quaff", "q"), i("Read", "r"), i("Apply tool", "a"),
		i("Zap wand", "z"), i("Cast spell", "Z"), i("Dip", "M-d"), i("Rub", "M-r"),
		i("Invoke", "M-i"),
	});

	public static final Group LOOK = new Group("look", "LOOK", new Item[] {
		i("Look here", ":"), i("Far look", ";"), i("What is", "/"), i("Adjacent trap", "^"),
		i("Attributes", "^X"), i("Known spells", "+"), i("Worn armor", "["), i("Wielded", ")"),
		i("Worn rings", "="), i("Worn amulet", "\""), i("Tools in use", "("), i("All equipment", "*"),
		i("Discoveries", "\\"), i("Overview", "M-O"), i("Past messages", "^P"), i("Chronicle", "v"),
		i("Enhance skills", "M-e"), i("Conduct", "M-C"), i("What does key", "&"), i("Terrain", "#terrain\n"),
	});

	/**
	 * Search mode's switch.  The overlay intercepts it in execute(); the key is
	 * never sent to the core, and PINNABLE drops it so a pinned copy cannot send
	 * "s" and then "+" by accident.
	 */
	public static final Item SEARCH_MODE = i("Search mode", "s+");

	/**
	 * The terminal's case, on or off (RhTheme.caseless).  Intercepted like Search
	 * mode, and kept out of PINNABLE for the same reason: its key is never sent.
	 */
	public static final Item CASE_TOGGLE = i("Case on/off", "#case");

	public static final Group WORLD = new Group("world", "WORLD", new Item[] {
		i("Pick up", ","), i("Open door", "o"), i("Close door", "c"), i("Search", "s"),
		SEARCH_MODE,
		i("Rest one", "."), i("Travel", "_"), i("Go down", ">"), i("Go up", "<"),
		i("Loot box", "M-l"), i("Force lock", "M-f"), i("Untrap", "M-u"), i("Engrave", "E"),
		i("Chat", "M-c"), i("Pay bill", "p"), i("Sacrifice", "M-o"), i("Sit", "M-s"),
		i("Jump", "M-j"), i("Teleport", "^T"), i("Ride", "M-R"), i("Monster power", "M-m"),
		i("Wipe face", "M-w"),
	});

	public static final Group GAME = new Group("game", "GAME", new Item[] {
		/*
		 * The safety net.  `#` opens the core's own command menu, built from
		 * extcmdlist rather than from anything in this file -- so a command this
		 * interface has forgotten, buried or never had a face for is still one
		 * pick away.  Its "(list everything)" entry expands to the complete table,
		 * single-key commands included, and it hides the wizard entries outside
		 * debug mode on its own.
		 */
		i("All commands", "#"),
		CASE_TOGGLE,
		i("Options", "O"), i("All options", "mO"), i("Save", "S"), i("Help", "?"),
		i("Annotate", "M-A"), i("Call/name", "C"), i("Name type", "M-n"), i("Autopickup", "@"),
		i("Repeat", "^A"), i("Redraw", "^R"), i("Version", "V"),
		i("Explore mode", "#exploremode\n"), i("Quit", "#quit\n"),
	});

	// ____________________________________________________________________________________
	// Wizard mode.
	//
	// Appended to the World and Game drawers only when the core reports debug
	// mode.  Every key below was read off this tree's own WIZMODECMD entries in
	// src/cmd.c rather than remembered -- the six with bindings are C('e'),
	// C('f'), C('g'), C('i'), C('v') and C('w'); the rest have no default key and
	// go through the extended-command line.
	//
	// #panic and #fuzzer are deliberately absent: one crashes the game on purpose
	// and the other drives it randomly, and neither belongs one tap from a face.

	public static final Item[] WIZ_WORLD = {
		i("Map level", "^F"), i("Detect near", "^E"), i("Create mon", "^G"),
		i("Levelport", "^V"), i("Remake level", "#wizmakemap\n"),
		i("Where am I", "#wizwhere\n"), i("Flip level", "#wizfliplevel\n"),
	};

	public static final Item[] WIZ_GAME = {
		i("Wish", "^W"), i("Identify all", "^I"), i("Set intrinsic", "#wizintrinsic\n"),
		i("Level change", "#levelchange\n"), i("Polyself", "#polyself\n"),
		i("Kill monster", "#wizkill\n"), i("Show stats", "#stats\n"),
	};

	/** The wizard-mode additions for a group, or null if it has none. */
	public static Item[] wizardExtras(String groupId)
	{
		if("world".equals(groupId))
			return WIZ_WORLD;
		if("game".equals(groupId))
			return WIZ_GAME;
		return null;
	}

	private static final Map<String, Group> GROUPS = new LinkedHashMap<String, Group>();
	static
	{
		for(Group g : new Group[] { INVENT, WEAR, WEAPON, EQUIP, DROP, FIGHT, USE, LOOK, WORLD, GAME })
			GROUPS.put(g.id, g);
	}

	public static Group group(String id)
	{
		return GROUPS.get(id);
	}

	// ____________________________________________________________________________________
	// Hubs.  Left thumb owns violence and shedding weight; right thumb owns armor,
	// accessories, consumables and the world.  A tap runs quick(); a hold fans the
	// three to six faces; a tap on an open fan opens the whole group.
	//
	// Shape is the identity.  Once colour alone stops distinguishing five hubs,
	// each gets a silhouette.

	public static final class Hub
	{
		public final String id;
		public final String label;
		public final int[] face;
		public final Item quick;
		public final Item[] fan;
		public final Group group;

		/** Centre in design dp; a negative x is measured from the right edge. */
		public final float cx, cyFromBottom;
		public final float w, h;
		/** Silhouette, or null for a plain circle / rounded square. */
		public final float[] poly;
		public final float insL, insT, insR, insB;
		/** Corner radius when poly is null; negative means a circle. */
		public final float radius;
		public final float labelSize;
		public final float labelTracking;
		public final float labelPadBottom, labelPadRight;

		/** Angle of fan slot 1, CSS convention (0 = east, y down, clockwise). */
		public final float fanA0;
		/** Degrees between adjacent slots; negative sweeps anticlockwise. */
		public final float fanStep;
		public final float fanRadius;
		/** Left-hand hubs point their "hold" hint right, right-hand hubs left. */
		public final boolean leftSide;

		Hub(String id, String label, int[] face, Item quick, Item[] fan, Group group,
		    float cx, float cyFromBottom, float w, float h,
		    float[] poly, float insL, float insT, float insR, float insB, float radius,
		    float labelSize, float labelTracking, float labelPadBottom, float labelPadRight,
		    float fanA0, float fanStep, float fanRadius, boolean leftSide)
		{
			this.id = id; this.label = label; this.face = face; this.quick = quick;
			this.fan = fan; this.group = group;
			this.cx = cx; this.cyFromBottom = cyFromBottom; this.w = w; this.h = h;
			this.poly = poly;
			this.insL = insL; this.insT = insT; this.insR = insR; this.insB = insB;
			this.radius = radius;
			this.labelSize = labelSize; this.labelTracking = labelTracking;
			this.labelPadBottom = labelPadBottom; this.labelPadRight = labelPadRight;
			this.fanA0 = fanA0; this.fanStep = fanStep; this.fanRadius = fanRadius;
			this.leftSide = leftSide;
		}
	}

	/**
	 * ATTACK sits at x 208 rather than in the corner because the numpad owns the
	 * corner; it clears the pad's right edge by 4dp.  Zap and Cast live here
	 * because zapping a wand and casting a spell are offensive -- they belong with
	 * Fight and Fire, not with opening doors.
	 */
	/**
	 * OFFENSE.  Tap opens its radial, hold opens the drawer -- the one hub whose
	 * gesture is inverted, because most combat is walking into a monster, so
	 * firing `F` on a bare tap was spending the best target on the left thumb for
	 * a command the player rarely wants.
	 *
	 * It also removes the one path into a direction prompt that bypassed armed
	 * mode.  Tapping the old hub sent a bare `F` and let the core do the
	 * prompting, so the numpad never turned red; every route now goes through
	 * arm() and the colour follows.
	 *
	 * Set 4dp clear of the pad's right edge, which is the relationship the handoff
	 * specifies for this hub.  The cx here is the value for a 154dp pad and is
	 * *not* what gets used: RhOverlay.hubCx() derives it from the pad's actual
	 * width, since the key size is a preference.  It is sized to one pad cell as
	 * well (RhOverlay.hubW), so it grows with the Movement key size setting.
	 */
	public static final Hub HUB_ATTACK = new Hub("fight", "OFFENSE", RhTheme.R90,
		i("Inventory", "i"),   /* unused: the tap opens the radial */
		new Item[0], FIGHT,
		193f, 62f, 46f, 46f,
		RhFaceShapes.STAR12, 2f, 2f, 2f, 2f, -1f,
		7f, 0.02f, 0f, 0f,
		0f, 0f, 0f, true);

	/**
	 * Flush to the left edge, stacked directly under the PRAY/SACRIFICE column.
	 * cy 210 is a floor: RhOverlay.hubCy() lifts it when a larger pad would
	 * otherwise reach it.
	 *
	 * The fan sweeps -64 to 0 rather than the earlier -52 to +12.  Its last node
	 * sits at radius 110 and 46dp across; at +12 its lower edge already grazed
	 * the top row of a 154dp pad by 2dp and would have covered 18dp of `u` on a
	 * 190dp one.  At 0 it clears the pad by 5dp at either size, and the top node
	 * at -64 still clears the PRAY column by 11dp.
	 */
	public static final Hub HUB_DROP = new Hub("drop", "DROP", RhTheme.TEAL,
		i("Drop", "d"),
		new Item[] {
			i("Drop type", "D"), i("From menu", "Dm"), i("Review first", "Di", RhTheme.OFF90),
		}, DROP,
		58f, 210f, 92f, 48f,
		RhFaceShapes.CHEVRON, 2f, 2f, 2f, 2f, -1f,
		10f, 0.06f, 0f, 16f,
		-64f, 32f, 110f, true);

	/**
	 * APPLY became INTERACT and took the dungeon verbs.  The rename is the point:
	 * the hub is now about acting on the world rather than on an item, which is
	 * why it is also the hub that carries no colour of its own.
	 */
	public static final Hub HUB_INTERACT = new Hub("apply", "INTERACT", RhTheme.G90,
		i("Apply", "a"),
		new Item[] {
			i("Apply tool", "a"), i("Open", "o"), i("Sit", "#sit\n"),
			i("Dip", "M-d"),
			// Engrave takes the fifth slot from Tip, which stays in the Use drawer.
			// Elbereth is not a niche command; tipping a container is.  A hold goes
			// straight to the pick-what-to-write-with menu.
			alt("Engrave", "E", "E?"),
		}, USE,
		-58f, 62f, 76f, 76f,
		null, 0f, 0f, 0f, 0f, -1f,
		9.5f, 0.04f, 0f, 0f,
		258f, -24f, 118f, false);

	/**
	 * The three commands you fire under pressure -- a potion of full healing, a
	 * scroll of teleport, a lichen corpse before you faint.  They were two levels
	 * deep in a fan shared with wand-zapping.
	 *
	 * Labelled with its three verbs since 2026-09-23 (Lucas): it was CONSUME, and
	 * no one word covers eating, quaffing and reading -- a scroll is not consumed
	 * in any sense a player would say.  The game's own verbs, stacked, name the
	 * fan exactly, and none is longer than five letters.
	 */
	public static final Hub HUB_CONSUME = new Hub("consume", "EAT\nQUAFF\nREAD", RhTheme.PINK,
		i("Eat", "e"),
		new Item[] {
			i("Eat", "e"), i("Quaff", "q"), i("Read", "r"),
		}, USE,
		-58f, 146f, 63f, 63f,
		null, 0f, 0f, 0f, 0f, 3f,
		8f, 0.04f, 0f, 0f,
		200f, 32f, 110f, false);

	/**
	 * The inventory bar over the equipment matrix (2026-09-23): tap `i`, hold
	 * for the whole gear drawer.  The six cells under it are EQUIP's pin slots,
	 * laid out by RhOverlay.buildEquipMatrix(); cx/cy here are unused, since the
	 * bar is placed with the matrix against the top-right cluster.
	 *
	 * History: a triangle whose tap opened a radial (v2); tap `i`, hold radial
	 * (2026-09-21); then this, because swapping helmets at an altar or taking a
	 * blindfold on and off wants both halves of the pair on screen at once.
	 */
	public static final Hub HUB_EQUIP = new Hub("equip", "INVENTORY", RhTheme.G90,
		i("Inventory", "i"),
		new Item[0], EQUIP,
		-84f, 282f, 154f, 24f,
		null, 0f, 0f, 0f, 0f, 3f,
		8.5f, 0.08f, 0f, 0f,
		0f, 0f, 0f, false);

	public static final Hub[] HUBS = {
		HUB_DROP, HUB_CONSUME, HUB_EQUIP, HUB_ATTACK, HUB_INTERACT,
	};

	/**
	 * Opening a fan or radial dims the other hubs on that thumb's side.  Cheaper
	 * than re-aiming every fan to avoid every neighbour, and it reads correctly:
	 * while you are choosing inside a fan, nothing else on that thumb is live.
	 */
	public static boolean isLeftSide(String hubId)
	{
		return "fight".equals(hubId) || "drop".equals(hubId) || "move".equals(hubId);
	}

	// ____________________________________________________________________________________
	// The movement numpad.  A 3x3 grid of squares in the bottom-left corner -- a
	// numpad's proportions, which is what NetHack's movement keys are.  Reading
	// order is the numpad's own, and the centre cell is REST/HOLD.

	public static final char[] PAD_KEYS = {
		'y', 'k', 'u',
		'h',  0,  'l',
		'b', 'j', 'n',
	};
	public static final String[] PAD_ARROW = {
		"↖", "↑", "↗",
		"←", "",  "→",
		"↙", "↓", "↘",
	};

	/**
	 * The star's three pinnable points, which are also where its radial draws its
	 * three commands and the directions its flick gesture recognises.
	 *
	 * Spread 45 degrees apart rather than the earlier 31: with flick-to-select,
	 * the spacing *is* the wedge width, and thumb-direction studies put 45 at
	 * the narrow end of reliable (see the FLICK_* notes in RhOverlay).  At
	 * radius 96 that is 73dp between adjacent centres against 44dp faces.  The
	 * arc runs from just left of straight up (-85) to just below horizontal (+5)
	 * -- the thumb's outward, extension side, which is its accurate side
	 * (Trudeau et al. 2012); nothing points back toward the pad.
	 */
	public static final float[] ATK_SLOT_BEARING = { -85f, -40f, 5f };
	public static final float   ATK_SLOT_RADIUS  = 96f;

	/**
	 * Empty by default now that Swap and Two-weapon have moved to EQUIP's drawer.
	 * An empty point shows a dashed `+` and opens the radial, so the scaffolding
	 * doubles as the way in -- and whatever the player pins lands in exactly the
	 * position the radial showed it, which is the point of putting both on the
	 * same arc.
	 */
	public static final String[] ATK_SLOT_DEFAULT = { null, null, null };

	/**
	 * OFFENSE's radial, on the same bearings and radius as the pinnable points
	 * they can be promoted into.  Order matters -- it is the wedge order for the
	 * flick: Fight up, Kick up-and-right.
	 *
	 * Two, not three (Lucas, 2026-09-24).  The third wedge, Fire to the right,
	 * was the hard one to reach, and three flicks plus the pinned keys was more
	 * to keep in the hand than combat needs: a fight is as often settled by
	 * engraving, reading, quaffing or rubbing a lamp.  Fire stays in the drawer
	 * and can be pinned.  With two nodes each wedge widens to about 60 degrees.
	 */
	public static final Item[] OFFENSE_RADIAL = {
		i("Fight", "F", RhTheme.R90), i("Kick", "^D", RhTheme.R90),
	};

	/** The old triangle's satellite geometry; nothing is placed with it since 2026-09-23. */
	public static final float[] EQUIP_SAT_BEARING = { 200f, 340f, 90f };
	public static final float[] EQUIP_SAT_RADIUS  = {  58f,  58f, 72f };

	/**
	 * The equipment matrix's six cells, row-major: put-on verbs above, their
	 * undoing below.  W over T (armour); P over R (rings, amulets, a blindfold or
	 * towel -- blind telepathic scouting makes that pair one of the most pressed
	 * in the game); w over x (wield, swap -- a bow and a sword, say).
	 */
	public static final String[] EQUIP_SLOT_DEFAULT = { "W", "P", "w", "T", "R", "x" };

	/**
	 * The equip radial: two staggered arcs, because five 44dp circles cannot fit on
	 * a single arc between the top-right cluster and CONSUME.  A single arc at
	 * radius 92 with a 20 degree step puts them 31.9dp apart, overlapping by 12dp
	 * each; the arcs that do clear push a node into MENU/WORLD/GAME or into
	 * CONSUME.  Do not flatten it back.
	 */
	public static final Item[] EQUIP_RADIAL = {
		i("Wear", "W"), i("Off", "T", RhTheme.OFF90), i("Put on", "P"),
		i("All off", "A", RhTheme.OFF90), i("Remove", "R", RhTheme.OFF90),
	};
	public static final float[] EQUIP_BEARING = { 110f, 148f, 180f, 145f, 180f };
	public static final float[] EQUIP_RADIUS  = {  76f,  80f,  76f, 128f, 128f };

	// ____________________________________________________________________________________
	// Fixed faces.

	public static final class RowFace
	{
		public final String label;
		public final String groupId;
		public final float w, h, bottomMargin;
		/** Swipe up on the face for its bulk form; null when there is none. */
		public final Item bulk;

		RowFace(String label, String groupId, float w, float h, float bottomMargin, Item bulk)
		{
			this.label = label;
			this.groupId = groupId;
			this.w = w;
			this.h = h;
			this.bottomMargin = bottomMargin;
			this.bulk = bulk;
		}
	}

	/**
	 * Top-right cluster: everything the player does not touch mid-fight.  Widths
	 * came down ~18% from 60/66/60 to give the map more of the top edge; heights
	 * stay at 44 because the touch floor holds.
	 */
	public static final RowFace[] TOP_RIGHT = {
		new RowFace("MENU",  "menu",  49, 44, 0, null),
		new RowFace("WORLD", "world", 54, 44, 0, null),
		new RowFace("GAME",  "game",  49, 44, 0, null),
		/*
		 * The soft keyboard.  Not high-frequency, but it is the fallback behind
		 * every command that wants a letter the interface has no face for -- an
		 * item at a prompt, an extended command, an unusual answer to a yn query.
		 * The top-right cluster is exactly where "needed, but never mid-fight"
		 * belongs, and the stock Android build keeps its own toggle permanently
		 * visible for the same reason.
		 */
		new RowFace("KEYS",  "keyboard", 44, 44, 0, null),
	};

	/**
	 * Deliberately awkward: top-left, the furthest point from either thumb.  Prayer
	 * and sacrifice are emergency and altar commands you must never fire by
	 * accident, and they are the only two on amber outside contextual state.
	 */
	public static final Item PRAY      = i("Pray", "M-p", RhTheme.A90);
	public static final Item SACRIFICE = i("Sacrifice", "M-o", RhTheme.A90);

	/**
	 * Message history, beside the message line.
	 *
	 * The handoff calls the message line "never interactive", and this deviates
	 * deliberately: an ascending player reads the log constantly -- what a trap
	 * did, what a monster's attack was, what happened when a ring went down a
	 * sink -- and pairs it with `C` to name the item before it is formally
	 * identified.  Burying it in a drawer taxes a loop that runs all game.
	 */
	public static final Item PREV_MSGS = i("Msgs", "^P");

	/** The numpad's centre cell, tapped -- whichever of these applies this turn. */
	public static final Item SEARCH = i("Search", "s");
	public static final Item PICKUP = i("Pick up", ",");

	// ____________________________________________________________________________________
	// The context strip: Look and Search in fixed slots either side of one
	// reserved slot the turn may fill.  The availability test lives in
	// RhOverlay.recomputeContext(); this is only the vocabulary and the ranking.

	public static final class ContextAction
	{
		public final String id;
		public final String word;
		public final String key;
		/**
		 * Counted actions carry a repeat count, because the right number changes
		 * constantly -- s20 down a dead end, s5 while suspicious, one careful s
		 * after a magic trap has blinded you with monsters closing in.  Zero means
		 * the action takes no count.
		 */
		public final int defaultCount;
		/** A related command on a long press; Look's is Far look. */
		public final String altKey;
		/** Sub-line naming the long press, when the face should advertise it. */
		public final String holdHint;
		/**
		 * What the chosen count is stored under.  Usually the key; Long rest sends
		 * `s` like Search, so it keeps its own count under its own name.
		 */
		public final String countKey;
		/** The counts its chip row offers. */
		public final int[] counts;

		ContextAction(String id, String word, String key)
		{
			this(id, word, key, 0, null);
		}

		ContextAction(String id, String word, String key, int defaultCount)
		{
			this(id, word, key, defaultCount, null);
		}

		ContextAction(String id, String word, String key, int defaultCount, String altKey)
		{
			this(id, word, key, defaultCount, altKey, null);
		}

		ContextAction(String id, String word, String key, int defaultCount, String altKey, String holdHint)
		{
			this(id, word, key, defaultCount, altKey, holdHint, key, COUNT_CHOICES);
		}

		ContextAction(String id, String word, String key, int defaultCount, String altKey, String holdHint,
		              String countKey, int[] counts)
		{
			this.id = id;
			this.word = word;
			this.key = key;
			this.defaultCount = defaultCount;
			this.altKey = altKey;
			this.holdHint = holdHint;
			this.countKey = countKey;
			this.counts = counts;
		}

		public boolean hasAlt()
		{
			return altKey != null && altKey.length() > 0;
		}

		public boolean isCounted()
		{
			return defaultCount > 0;
		}

		/** The key sequence for a given repeat count; 1 sends the bare key. */
		public String keyWithCount(int n)
		{
			return n > 1 ? Integer.toString(n) + key : key;
		}

		/** The face always shows what it will send, so the count rides in the label. */
		public String wordWithCount(int n)
		{
			return n > 1 ? word + " ×" + n : word;
		}
	}

	/** The counts a chip row offers. */
	public static final int[] COUNT_CHOICES = { 1, 5, 10, 20 };

	public static final ContextAction CTX_ATTACK   = new ContextAction("attack",  "Attack",   "F");
	public static final ContextAction CTX_PICKUP   = new ContextAction("pickup",  "Pick up",  ",");
	public static final ContextAction CTX_DESCEND  = new ContextAction("descend", "Descend",  ">");
	public static final ContextAction CTX_ASCEND   = new ContextAction("ascend",  "Ascend",   "<");
	public static final ContextAction CTX_OPEN     = new ContextAction("open",    "Open door","o");
	/**
	 * Standing on a container.  The pad's centre cell keeps Pick up -- moving a
	 * chest to a stash is the more common intent, and burying that would cost
	 * more than it saves -- so looting gets a face of its own here instead.
	 */
	public static final ContextAction CTX_LOOT     = new ContextAction("loot",    "Loot",     "M-l");
	public static final ContextAction CTX_SACRIFICE = new ContextAction("offer", "Sacrifice", "M-o");
	public static final ContextAction CTX_SEARCH   = new ContextAction("search",  "Search",   "s", 1);
	public static final ContextAction CTX_REST     = new ContextAction("rest",    "Rest",     ".", 20);
	/**
	 * Long rest (Lucas, 2026-09-24): waiting out a hundred turns or more comes up
	 * often -- prayer timeout, HP, a unicorn to wander past.  It searches rather
	 * than rests, as his gurrhack panel key did (s100-s400), and it lives
	 * scrolled out of sight under Rest so it cannot be tapped by accident: see
	 * RhScrollWell.
	 */
	public static final int[] LONG_COUNT_CHOICES = { 100, 200, 300, 400 };
	public static final ContextAction CTX_LONG_REST = new ContextAction("longrest", "Long rest", "s", 200,
			null, null, "longrest", LONG_COUNT_CHOICES);
	public static final ContextAction CTX_LOOKHERE = new ContextAction("lookhere","Look here",":");
	/** Hold Far look for Look here: the same question asked of your own square. */
	/**
	 * Look: your own square on a tap, anything in sight on a hold (Lucas,
	 * 2026-09-23 -- the square underfoot gets checked far more often than a
	 * distant one).  It was Far look with Look here on the hold.  It keeps the
	 * lens shape, and its sub-line names the hold.
	 */
	public static final ContextAction CTX_LOOK     = new ContextAction("look",    "Look",     ":", 0, ";", "hold · farlook");
	public static final ContextAction CTX_CHAT     = new ContextAction("chat",    "Chat",     "M-c");

	/** "Attack jackal" -- the hostile's name rides in the label. */
	public static ContextAction attackOn(String monsterName)
	{
		if(monsterName == null || monsterName.length() == 0)
			return CTX_ATTACK;
		return new ContextAction("attack", "Attack " + monsterName, "F");
	}

	/** Priority order for the three-face strip; the first three available win. */
	public static final ContextAction[] CTX_PRIORITY = {
		CTX_ATTACK, CTX_PICKUP, CTX_DESCEND, CTX_OPEN, CTX_SEARCH, CTX_REST, CTX_LOOK,
	};

	/** The context radial extends the strip's list with look here and chat. */
	public static final ContextAction[] CTX_RADIAL = {
		CTX_ATTACK, CTX_PICKUP, CTX_DESCEND, CTX_LOOKHERE, CTX_CHAT,
	};

	// ____________________________________________________________________________________
	// Pinnable commands.
	//
	// Slots persist as raw keys, so a key has to be able to become a face again on
	// the next launch.  Group entries go in first and the hub, fan and radial
	// entries overwrite them, because those carry the short labels a 44dp slot can
	// actually hold ("2Weap", not "Two-weapon"; "All off", not "Take off all").

	private static final Map<String, Item> PINNABLE = new LinkedHashMap<String, Item>();
	static
	{
		for(Group g : GROUPS.values())
			for(Item it : g.items)
				PINNABLE.put(it.key, it);

		for(Hub h : HUBS)
			for(Item it : h.fan)
				PINNABLE.put(it.key, it);

		for(Item it : EQUIP_RADIAL)
			PINNABLE.put(it.key, it);

		// Short labels for the two star defaults, which are not fan nodes.
		PINNABLE.put("x", i("Swap", "x"));
		PINNABLE.put("X", i("2Weap", "X"));

		// The equipment matrix: on over off, and the off side wears the off colour.
		PINNABLE.put("W", i("Wear", "W"));
		PINNABLE.put("T", i("Take off", "T", RhTheme.OFF90));
		PINNABLE.put("P", i("Put on", "P"));
		PINNABLE.put("R", i("Remove", "R", RhTheme.OFF90));
		PINNABLE.put("w", i("Wield", "w"));

		PINNABLE.remove(SEARCH_MODE.key);
		PINNABLE.remove(CASE_TOGGLE.key);
	}

	/** The face for a persisted key, or null if nothing answers to it. */
	public static Item pinnable(String key)
	{
		return key == null ? null : PINNABLE.get(key);
	}

	// ____________________________________________________________________________________
	// Gestures that are not attached to a face.

	public static final Item GESTURE_RUN         = i("Run", "g");       // + direction
	public static final Item GESTURE_REST20      = i("Rest 20", "20.");  // two-finger tap
	public static final Item GESTURE_REPEAT_LAST = i("Repeat", "^A");    // swipe down on the map
	public static final Item GESTURE_FARLOOK     = i("Far look", ";");   // long-press a tile
	public static final String ESC = "\\e";
}
