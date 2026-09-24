package com.tbd.forkfront.rolehack;

import com.tbd.forkfront.ByteDecoder;

/**
 * The status line, held apart.
 *
 * winandroid.c keeps every status value separate in {@code status_vals[]} and
 * then flattens them into two text rows, because that is what the classic status
 * window wants.  This is the other end of the port-layer callback that hands
 * them over still separated, so the interface can draw an HP bar and a hunger
 * badge rather than re-parsing a formatted line.
 *
 * Field indices are NetHack's own {@code BL_*} constants, so this stays in step
 * with the core rather than with any formatting decision.
 */
public final class RhStatus
{
	// From include/botl.h.  Kept as constants here so the interface never has to
	// guess at an index.
	public static final int BL_TITLE = 0;
	public static final int BL_STR = 1, BL_DX = 2, BL_CO = 3, BL_IN = 4, BL_WI = 5, BL_CH = 6;
	public static final int BL_ALIGN = 7, BL_SCORE = 8, BL_CAP = 9, BL_GOLD = 10;
	public static final int BL_ENE = 11, BL_ENEMAX = 12, BL_XP = 13, BL_AC = 14, BL_HD = 15;
	public static final int BL_TIME = 16, BL_HUNGER = 17, BL_HP = 18, BL_HPMAX = 19;
	public static final int BL_LEVELDESC = 20, BL_EXP = 21, BL_CONDITION = 22;
	public static final int BL_WEAPON = 23, BL_ARMOR = 24, BL_TERRAIN = 25, BL_VERS = 26;
	public static final int MAXBLSTATS = 27;

	/**
	 * Condition bits, transcribed from include/botl.h.
	 *
	 * NetHack 5.0 defines thirty of these in alphabetical order.  An earlier
	 * version of this file took its values from a documentation comment inside
	 * winandroid.c instead, which describes a much older and shorter set -- so
	 * every bit was mapped to the wrong name, and the status panel cheerfully
	 * reported SLIME at being blinded.  Read botl.h, not the commentary.
	 */
	public static final int MASK_BAREH     = 0x00000001;
	public static final int MASK_BLIND     = 0x00000002;
	public static final int MASK_BUSY      = 0x00000004;
	public static final int MASK_CONF      = 0x00000008;
	public static final int MASK_DEAF      = 0x00000010;
	public static final int MASK_ELF_IRON  = 0x00000020;
	public static final int MASK_FLY       = 0x00000040;
	public static final int MASK_FOODPOIS  = 0x00000080;
	public static final int MASK_GLOWHANDS = 0x00000100;
	public static final int MASK_GRAB      = 0x00000200;
	public static final int MASK_HALLU     = 0x00000400;
	public static final int MASK_HELD      = 0x00000800;
	public static final int MASK_ICY       = 0x00001000;
	public static final int MASK_INLAVA    = 0x00002000;
	public static final int MASK_LEV       = 0x00004000;
	public static final int MASK_PARLYZ    = 0x00008000;
	public static final int MASK_RIDE      = 0x00010000;
	public static final int MASK_SLEEPING  = 0x00020000;
	public static final int MASK_SLIME     = 0x00040000;
	public static final int MASK_SLIPPERY  = 0x00080000;
	public static final int MASK_STONE     = 0x00100000;
	public static final int MASK_STRNGL    = 0x00200000;
	public static final int MASK_STUN      = 0x00400000;
	public static final int MASK_SUBMERGED = 0x00800000;
	public static final int MASK_TERMILL   = 0x01000000;
	public static final int MASK_TETHERED  = 0x02000000;
	public static final int MASK_TRAPPED   = 0x04000000;
	public static final int MASK_UNCONSC   = 0x08000000;
	public static final int MASK_WOUNDEDL  = 0x10000000;
	public static final int MASK_HOLDING   = 0x20000000;

	private final String[] mValues = new String[MAXBLSTATS];
	private final int[] mColors = new int[MAXBLSTATS];
	private int mConditions;

	private String mName = "";
	private String mRole = "";
	private String mRace = "";

	// ____________________________________________________________________________________
	// What is underfoot and adjacent.  The interface cannot see any of this on its
	// own -- the hero's glyph covers the square it stands on, so an item or a
	// staircase beneath the player is invisible to the map, and those are two of
	// the context strip's top three priorities.

	public static final int HERE_OBJECT      = 0x01;
	public static final int HERE_STAIRS_DOWN = 0x02;
	public static final int HERE_STAIRS_UP   = 0x04;
	public static final int ADJ_CLOSED_DOOR  = 0x08;
	public static final int ADJ_HOSTILE      = 0x10;
	/** A container underfoot -- a chest, box, or any bag lying on the floor. */
	public static final int HERE_CONTAINER   = 0x20;
	public static final int HERE_ALTAR       = 0x40;
	/** An open door beside you that could be shut: nothing seen in its doorway. */
	public static final int ADJ_OPEN_DOOR    = 0x80;

	private int mHereFlags;
	private String mAdjacentMonster = "";

	public void setHere(int flags, byte[] monsterName, ByteDecoder decoder)
	{
		mHereFlags = flags;
		String n = decode(monsterName, decoder).trim();
		// mon_nam() gives "the jackal"; a 104dp face wants "jackal".
		if(n.startsWith("the "))
			n = n.substring(4);
		mAdjacentMonster = n;
	}

	public boolean here(int mask)
	{
		return (mHereFlags & mask) != 0;
	}

	public String adjacentMonster()
	{
		return mAdjacentMonster;
	}

	// ____________________________________________________________________________________
	public void setField(int idx, byte[] text, int colorOrMask, ByteDecoder decoder)
	{
		if(idx == BL_CONDITION)
		{
			mConditions = colorOrMask;
			return;
		}
		if(idx < 0 || idx >= MAXBLSTATS)
			return;
		mValues[idx] = text == null ? null : decode(text, decoder);
		mColors[idx] = colorOrMask;
	}

	/** Bit 0 of flags is wizard mode. */
	public static final int PLAYER_WIZARD = 0x01;

	private boolean mWizard;

	public void setPlayerInfo(byte[] name, byte[] role, byte[] race, int flags,
	                          ByteDecoder decoder)
	{
		mName = decode(name, decoder);
		mRole = decode(role, decoder);
		mRace = decode(race, decoder);
		mWizard = (flags & PLAYER_WIZARD) != 0;
	}

	/** True in debug mode, which unlocks the wizard commands in the drawers. */
	public boolean isWizard()
	{
		return mWizard;
	}

	private static String decode(byte[] b, ByteDecoder decoder)
	{
		if(b == null)
			return "";
		if(decoder != null)
		{
			String s = decoder.decode(b);
			if(s != null)
				return s;
		}
		return new String(b);
	}

	// ____________________________________________________________________________________
	public String value(int idx)
	{
		if(idx < 0 || idx >= MAXBLSTATS)
			return null;
		return mValues[idx];
	}

	public int color(int idx)
	{
		return (idx < 0 || idx >= MAXBLSTATS) ? 0 : mColors[idx];
	}

	/**
	 * A field's value with the core's own decoration removed.
	 *
	 * Fields arrive already formatted by {@code status_fieldfmt[]} -- "St:14",
	 * "HP:14", "Dlvl:1", "$:0" -- and some arrive decorated rather than labelled:
	 * "(14)" for maximum HP, "/0" for experience points.  The interface supplies
	 * its own labels, so it wants the bare value; prepending a label to the
	 * formatted string is how "StSt:14" happens.
	 */
	public String bare(int idx)
	{
		String s = value(idx);
		if(s == null)
			return null;
		s = s.trim();

		int colon = s.indexOf(':');
		if(colon >= 0)
			s = s.substring(colon + 1).trim();
		if(s.length() >= 2 && s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')')
			s = s.substring(1, s.length() - 1).trim();
		if(s.startsWith("/"))
			s = s.substring(1).trim();
		return s;
	}

	/** {@link #bare} with a placeholder for a field the core has not sent. */
	public String bareOr(int idx, String fallback)
	{
		String s = bare(idx);
		return (s == null || s.length() == 0) ? fallback : s;
	}

	/** A field's value as an integer, or the fallback when it is absent or odd. */
	public int intValue(int idx, int fallback)
	{
		String s = value(idx);
		if(s == null)
			return fallback;
		// Values arrive formatted -- "$123" for gold, "18" for HP -- so take the
		// leading run of digits and ignore any decoration around it.
		int i = 0, n = s.length();
		while(i < n && !Character.isDigit(s.charAt(i)))
			i++;
		int start = i;
		while(i < n && Character.isDigit(s.charAt(i)))
			i++;
		if(start == i)
			return fallback;
		try
		{
			return Integer.parseInt(s.substring(start, i));
		}
		catch(NumberFormatException e)
		{
			return fallback;
		}
	}

	public int conditions()
	{
		return mConditions;
	}

	public boolean hasCondition(int mask)
	{
		return (mConditions & mask) != 0;
	}

	// ____________________________________________________________________________________
	// Conditions, worst first.
	//
	// The core ships the bitmask and leaves the naming and the colouring here.
	// Ordered by severity so that a truncated row still shows the thing that is
	// about to kill you rather than the fact that you are levitating.

	public static final int SEVERITY_DEADLY  = 2;
	public static final int SEVERITY_IMPAIR  = 1;
	public static final int SEVERITY_MOVE    = 0;

	public static final class Condition
	{
		public final int mask;
		public final String name;
		public final int severity;

		Condition(int mask, String name, int severity)
		{
			this.mask = mask;
			this.name = name;
			this.severity = severity;
		}
	}

	/**
	 * Listed in the core's own ranking order, lowest first -- that field exists in
	 * botl.c's conditions[] precisely to say which one matters most, so a
	 * truncated badge row keeps the thing that is about to kill you.  Names are
	 * the core's own short forms.
	 */
	private static final Condition[] CONDITIONS = {
		new Condition(MASK_GRAB,      "Grab",     SEVERITY_DEADLY),   /* rank 2 */
		new Condition(MASK_STRNGL,    "Strngl",   SEVERITY_DEADLY),   /* rank 4 */
		new Condition(MASK_FOODPOIS,  "FoodPois", SEVERITY_DEADLY),   /* rank 6 */
		new Condition(MASK_SLIME,     "Slime",    SEVERITY_DEADLY),
		new Condition(MASK_STONE,     "Stone",    SEVERITY_DEADLY),
		new Condition(MASK_TERMILL,   "TermIll",  SEVERITY_DEADLY),
		new Condition(MASK_INLAVA,    "InLava",   SEVERITY_DEADLY),   /* rank 8 */
		new Condition(MASK_BLIND,     "Blind",    SEVERITY_IMPAIR),   /* rank 10 */
		new Condition(MASK_CONF,      "Conf",     SEVERITY_IMPAIR),
		new Condition(MASK_DEAF,      "Deaf",     SEVERITY_IMPAIR),
		new Condition(MASK_HALLU,     "Hallu",    SEVERITY_IMPAIR),
		new Condition(MASK_STUN,      "Stun",     SEVERITY_IMPAIR),
		new Condition(MASK_FLY,       "Fly",      SEVERITY_MOVE),
		new Condition(MASK_LEV,       "Lev",      SEVERITY_MOVE),
		new Condition(MASK_RIDE,      "Ride",     SEVERITY_MOVE),
		new Condition(MASK_ELF_IRON,  "Iron",     SEVERITY_IMPAIR),   /* rank 15 */
		new Condition(MASK_SUBMERGED, "Submrg",   SEVERITY_IMPAIR),
		new Condition(MASK_PARLYZ,    "Parlyz",   SEVERITY_DEADLY),   /* rank 20 */
		new Condition(MASK_UNCONSC,   "Out",      SEVERITY_DEADLY),
		new Condition(MASK_SLEEPING,  "Zzz",      SEVERITY_DEADLY),
		new Condition(MASK_HELD,      "Held",     SEVERITY_IMPAIR),
		new Condition(MASK_TRAPPED,   "Trap",     SEVERITY_IMPAIR),
		new Condition(MASK_TETHERED,  "Teth",     SEVERITY_IMPAIR),
		new Condition(MASK_WOUNDEDL,  "WLegs",    SEVERITY_IMPAIR),
		new Condition(MASK_BUSY,      "Busy",     SEVERITY_IMPAIR),
		new Condition(MASK_HOLDING,   "UHold",    SEVERITY_MOVE),
		new Condition(MASK_ICY,       "Icy",      SEVERITY_MOVE),
		new Condition(MASK_SLIPPERY,  "Slip",     SEVERITY_MOVE),
		new Condition(MASK_GLOWHANDS, "Glow",     SEVERITY_MOVE),
		new Condition(MASK_BAREH,     "Bare",     SEVERITY_MOVE),
	};

	/** The active conditions, worst first. */
	public java.util.List<Condition> activeConditions()
	{
		java.util.List<Condition> out = new java.util.ArrayList<Condition>();
		for(Condition c : CONDITIONS)
			if((mConditions & c.mask) != 0)
				out.add(c);
		return out;
	}

	public String name() { return mName; }
	public String role() { return mRole; }
	public String race() { return mRace; }

	/** "VALKYRIE · LAWFUL · DWARF" -- the header's role line. */
	public String roleLine()
	{
		StringBuilder sb = new StringBuilder();
		append(sb, mRole);
		append(sb, value(BL_ALIGN));
		append(sb, mRace);
		return sb.toString().toUpperCase();
	}

	private static void append(StringBuilder sb, String part)
	{
		if(part == null)
			return;
		part = part.trim();
		if(part.length() == 0)
			return;
		if(sb.length() > 0)
			sb.append(" · ");
		sb.append(part);
	}

	/** HP as a fraction of maximum, clamped, for the bar. */
	public float hpFraction()
	{
		int max = intValue(BL_HPMAX, 0);
		if(max <= 0)
			return 0f;
		float f = intValue(BL_HP, 0) / (float)max;
		return f < 0f ? 0f : (f > 1f ? 1f : f);
	}

	/** True once the core has actually sent something worth drawing. */
	public boolean isPopulated()
	{
		return value(BL_HPMAX) != null;
	}
}
