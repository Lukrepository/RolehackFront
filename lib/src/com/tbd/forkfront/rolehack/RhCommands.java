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
 * and C (#call) and M-n (#name) are two different commands.  On this port a
 * typed "#name" never works: '#' always opens the command menu, so explore
 * mode went to its key M-X and Quit opens the menu (2026-09-25).
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
		/**
		 * A section heading in a drawer (Lucas, 2026-09-26): a title over the
		 * keys that follow, not a key.  It has no key, is never pinned, and the
		 * drawer's command count leaves it out.
		 */
		public final boolean heading;
		/** A word for the key's corner in place of its raw key -- "BETA". */
		public final String tag;

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
			this(word, key, face, altKey, false, null);
		}

		private Item(String word, String key, int[] face, String altKey, boolean heading, String tag)
		{
			this.word = word;
			this.key = key;
			this.face = face;
			this.altKey = altKey;
			this.heading = heading;
			this.tag = tag;
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

		/** The commands, headings left out. */
		public int count()
		{
			int n = 0;
			for(Item it : items)
				if(!it.heading)
					n++;
			return n;
		}
	}

	private static Item i(String word, String key)              { return new Item(word, key); }
	private static Item i(String word, String key, int[] face)   { return new Item(word, key, face); }
	private static Item alt(String word, String key, String altKey)
	{
		return new Item(word, key, null, altKey);
	}
	/** A drawer section's heading. */
	private static Item head(String title)
	{
		return new Item(title, null, null, null, true, null);
	}
	/** A command still being tried out: "BETA" in its corner. */
	private static Item beta(String word, String key)
	{
		return new Item(word, key, null, null, false, "BETA");
	}

	// ____________________________________________________________________________________
	// The groups.  A few keys appear in two groups ([ ) = " * M-e C M-n):
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
		// Every item of unknown B/U/C status, in one press (Lucas, 2026-09-26):
		// an altar's test.  D's menu gives the special entries fixed letters --
		// X unknown status, A auto-select (pickup.c query_category()) -- and 5.0
		// rejects A on its own, so with nothing unknown nothing drops.
		i("Drop unknown", "DXA\\n"),
	});

	/**
	 * OFFENSE's drawer.  Throw, Zap and Cast live here rather than on the radial:
	 * the radial carries the three that take a direction, and the rest of the
	 * offensive vocabulary is one level deeper.
	 */
	// COMBAT, not OFFENSE (Lucas, 2026-09-26): "fight" and "attack" each name one
	// command already (F, and the context key's Attack), and the drawer holds more
	// than attacks -- Quiver, Zap, Cast.  "Combat" is the game's own word, from
	// the skills "bare handed combat" and "two weapon combat" (weapon.c).
	public static final Group FIGHT = new Group("fight", "COMBAT", new Item[] {
		// Quiver belongs with fighting at range: long-press OFFENSE and change what
		// f fires (Lucas, 2026-09-23).  It stays in the EQUIP drawer as well.
		i("Quiver", "Q"),
		i("Fight", "F", RhTheme.R90), i("Kick", "^D", RhTheme.R90), i("Fire", "f"),
		i("Throw", "t"), i("Zap wand", "z"), i("Cast spell", "Z"),
		i("Turn undead", "M-t"),
		// Rolehack's #grapple, which had no key and could only be picked from the
		// command menu; the core binds it to M-G (cmd.c).  Appended, so nothing
		// above moves (Lucas, 2026-09-26).
		i("Grapple", "M-G", RhTheme.R90),
	});

	/**
	 * EQUIP's drawer: everything worn or wielded in one place.
	 *
	 * Weapon-swapping moved here from the left thumb -- the standalone SWAP hub is
	 * gone and ATTACK no longer owns steel, so wield, swap and two-weapon belong
	 * with the armor and accessory commands rather than with violence.
	 */
	// Titled for the key that opens it (Lucas, 2026-09-26).
	public static final Group EQUIP = new Group("equip", "INVENTORY", new Item[] {
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

	/**
	 * Search mode's switch.  The overlay intercepts it in execute(); the key is
	 * never sent to the core, and PINNABLE drops it so a pinned copy cannot send
	 * "s" and then "+" by accident.
	 */
	public static final Item SEARCH_MODE = beta("Search mode", "s+");

	/**
	 * The terminal's case, on or off (RhTheme.caseless).  Intercepted like Search
	 * mode, and kept out of PINNABLE for the same reason: its key is never sent.
	 */
	public static final Item CASE_TOGGLE = i("Case on/off", "#case");

	/**
	 * The terminal's status lines: full, compact (no attributes) or hidden, in
	 * turn (RhPrefs.StatusLines).  Intercepted like the case switch, and kept out
	 * of PINNABLE with it.
	 */
	public static final Item STATUS_TOGGLE = i("Status lines", "#status");

	/*
	 * WORLD and GAME are the long tail: the common commands have their own keys,
	 * so these two are for finding a command, not for speed (Lucas, 2026-09-26,
	 * from 4,520 commands counted in the playtest transcripts; see
	 * drawer-plan-2026-09-26.html).  Sections run from most to least used, and
	 * so do the keys in each, except that pairs stay together and Quit and
	 * Explore mode go last.  Keys that also have their own face stay here, where
	 * a player looks when the key has slipped their mind.
	 */
	public static final Group WORLD = new Group("world", "WORLD", new Item[] {
		head("Getting around"),
		i("Travel", "_"), i("Go up", "<"), i("Go down", ">"), i("Jump", "M-j"),
		i("Teleport", "^T"), i("Ride", "M-R"),
		head("Search / wait"),
		i("Rest one", "."), i("Search", "s"), SEARCH_MODE,
		// NetHack's own word for what you can do on your square (#herecmdmenu)
		head("Here"),
		i("Pick up", ","), i("Engrave", "E"), i("Loot box", "M-l"), i("Pay bill", "p"),
		i("Force lock", "M-f"), i("Sit", "M-s"), i("Sacrifice", "M-o"),
		i("Monster power", "M-m"), i("Wipe face", "M-w"),
		// Commands that ask for a direction.  Kick opens locked boxes and doors
		// as well as fighting, so it is here as well as on OFFENSE.
		head("Adjacent"),
		i("Open door", "o"), i("Close door", "c"), i("Kick", "^D"), i("Chat", "M-c"),
		i("Untrap", "M-u"), i("Adjacent trap", "^"),
	});

	public static final Group GAME = new Group("game", "GAME", new Item[] {
		head("Knowledge"),
		i("Discoveries", "\\"), i("Past messages", "^P"), i("Attributes", "^X"), i("Chronicle", "v"),
		i("Enhance skills", "M-e"), i("What is", "/"), i("Known spells", "+"),
		// Terrain is DEL in 5.0 (cmd.c '\177'), written \b for KeySequnece.
		i("Terrain", "\\b"), i("Overview", "M-O"), i("Genocided", "M-g"), i("Vanquished", "M-V"),
		i("Conduct", "M-C"), i("All equipment", "*"),
		/*
		 * The safety net.  `#` opens the core's own command menu, built from
		 * extcmdlist rather than from anything in this file -- so a command this
		 * interface has forgotten, buried or never had a face for is still one
		 * pick away.  Its "(list everything)" entry expands to the complete table,
		 * single-key commands included, and it hides the wizard entries outside
		 * debug mode on its own.
		 */
		head("Help and commands"),
		i("All commands", "#"), i("Help", "?"), i("Version", "V"), i("Repeat", "^A"),
		// Quit has no key: it opens the command menu and the player picks "quit"
		// there (typed "#name" sequences misfire on this port: and_get_ext_cmd).
		head("Save / quit"),
		i("Save", "S"), i("Quit", "#"),
		head("Settings"),
		i("Options", "O"), i("All options", "mO"), i("Autopickup", "@"), i("Explore mode", "M-X"),
		head("Names and notes"),
		i("Call/name", "C"), i("Name type", "M-n"), i("Annotate", "M-A"),
		head("Display"),
		CASE_TOGGLE, STATUS_TOGGLE, i("Redraw", "^R"),
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
		head("Wizard mode"),
		i("Map level", "^F"), i("Detect near", "^E"), i("Create mon", "^G"),
		i("Levelport", "^V"), i("Remake level", "#wizmakemap\n"),
		i("Where am I", "#wizwhere\n"), i("Flip level", "#wizfliplevel\n"),
	};

	public static final Item[] WIZ_GAME = {
		head("Wizard mode"),
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
		for(Group g : new Group[] { INVENT, WEAR, WEAPON, EQUIP, DROP, FIGHT, USE, WORLD, GAME })
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
	public static final Hub HUB_ATTACK = new Hub("fight", "COMBAT", RhTheme.R90,
		// The ordinary hub grammar since the flick moved to its own key (Lucas,
		// 2026-09-26): tap Fight, whose direction the pad gives; hold for the fan;
		// tap the open fan for the drawer.
		i("Fight", "F", RhTheme.R90),
		new Item[] {
			i("Fire", "f"), i("Throw", "t"), i("Zap", "z"),
			i("Kick", "^D"), null, i("Cast", "Z"),
			i("Quiver", "Q"), i("Grapple", "M-G"), i("Turn undead", "M-t"),
		}, FIGHT,
		193f, 62f, 46f, 46f,
		RhFaceShapes.STAR12, 2f, 2f, 2f, 2f, -1f,
		8f, 0.02f, 0f, 0f,
		// Up from the deck, over the glass: nothing on this side is under the
		// thumb that holds it.
		-100f, 26f, 100f, true);

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
			i("Drop all", "Da"), null, i("Tip", "M-T"),
			i("Cursed", "DC"), i("Drop unknown", "DXA\\n"), i("Unpaid", "Du"),
		}, DROP,
		58f, 210f, 92f, 48f,
		RhFaceShapes.CHEVRON, 2f, 2f, 2f, 2f, -1f,
		10f, 0.06f, 0f, 16f,
		-64f, 32f, 110f, true);

	/**
	 * APPLY became INTERACT when it took the dungeon verbs, and is APPLY again
	 * (Lucas, 2026-09-26): Open went to the context key and the dungeon verbs to
	 * WORLD, so the key is about items once more.  A tap is `a`, which the core
	 * calls "apply (use) a tool", so the label says what the tap does, and its
	 * drawer stays USE -- the game's own pairing.
	 */
	public static final Hub HUB_INTERACT = new Hub("apply", "APPLY", RhTheme.G90,
		i("Apply", "a"),
		new Item[] {
			i("Apply", "a"), i("Engrave", "E"), i("Dip", "M-d"),
			i("Rub", "M-r"), null, i("Invoke", "M-i"),
			i("Sit", "M-s"), i("Force lock", "M-f"), null,
		}, USE,
		-58f, 62f, 76f, 76f,
		null, 0f, 0f, 0f, 0f, -1f,
		9.5f, 0.04f, 0f, 0f,
		// 30 degrees apart since Open left (Lucas, 2026-09-24: four nodes felt
		// crowded at the old 24).
		258f, -30f, 118f, false);

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
		// The pinch layer (Lucas asked for suggestions, 2026-09-26): potions to
		// dip, a unicorn horn to apply, a wand to zap your way out.  Pray is
		// kept off every layer: a mistap there costs a run, and a layer puts
		// commands where the thumb presses without looking.
		new Item[] {
			i("Eat", "e"), i("Quaff", "q"), i("Read", "r"),
			i("Dip", "M-d"), null, i("Apply", "a"),
			i("Zap", "z"), null, null,
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
		new Item[] {
			i("By type", "I"), i("Armour", "["), i("Rings", "="),
			i("Wielded", ")"), null, i("Amulet", "\""),
			i("All worn", "*"), i("Gold", "$"), i("Letters", "M-a"),
		}, EQUIP,
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
	public static final float[] ATK_SLOT_BEARING = { -85f, -40f };
	public static final float   ATK_SLOT_RADIUS  = 96f;

	/**
	 * Empty by default now that Swap and Two-weapon have moved to EQUIP's drawer.
	 * An empty point shows a dashed `+` and opens the radial, so the scaffolding
	 * doubles as the way in -- and whatever the player pins lands in exactly the
	 * position the radial showed it, which is the point of putting both on the
	 * same arc.
	 */
	public static final String[] ATK_SLOT_DEFAULT = { null, null };

	/**
	 * The flick key (Lucas, 2026-09-26): the deck's third pinned point became a
	 * macro key that also flicks.  Its tap and its two flicks are macros -- see
	 * RhPrefs.FLICK_TAP -- so a command lands on it the way it lands on M1-M3.
	 * The flicks are the old OFFENSE ones, up and up-and-right: two wedges of
	 * about 60 degrees each, where three were hard to hit.
	 */
	public static final float[] FLICK_BEARING = { -85f, -40f };
	public static final float   FLICK_RADIUS  = 96f;

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
	/** Beside an open door, as Open is beside a closed one (Lucas, 2026-09-24). */
	public static final ContextAction CTX_CLOSE    = new ContextAction("close",   "Close door","c");
	/**
	 * Standing on a container.  The pad's centre cell keeps Pick up -- moving a
	 * chest to a stash is the more common intent, and burying that would cost
	 * more than it saves -- so looting gets a face of its own here instead.
	 */
	public static final ContextAction CTX_LOOT     = new ContextAction("loot",    "Loot",     "M-l");
	public static final ContextAction CTX_SACRIFICE = new ContextAction("offer", "Sacrifice", "M-o");
	/** On an altar, beside Sacrifice: drop everything of unknown B/U/C status to see it flash. */
	public static final ContextAction CTX_DROP_UNKNOWN = new ContextAction("dropunknown", "Drop unknown", "DXA\\n");
	public static final ContextAction CTX_SEARCH   = new ContextAction("search",  "Search",   "s", 1);
	public static final ContextAction CTX_REST     = new ContextAction("rest",    "Rest",     ".", 20);
	/**
	 * Long rest (Lucas, 2026-09-24): waiting out a hundred turns or more comes up
	 * often -- prayer timeout, HP, a unicorn to wander past.  It searches rather
	 * than rests, as his gurrhack panel key did (s100-s400), and it lives
	 * scrolled out of sight beside Rest so it cannot be tapped by accident: see
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
		CTX_ATTACK, CTX_PICKUP, CTX_DESCEND, CTX_OPEN, CTX_CLOSE, CTX_SEARCH, CTX_REST, CTX_LOOK,
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
				if(!it.heading)
					PINNABLE.put(it.key, it);

		for(Hub h : HUBS)
			for(Item it : h.fan)
				if(it != null)   // a layer's centre, and its empty places
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
		PINNABLE.remove(STATUS_TOGGLE.key);
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
