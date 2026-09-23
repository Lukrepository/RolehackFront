package com.tbd.forkfront.rolehack;

import android.content.SharedPreferences;

/**
 * The persisted half of the interface's state.
 *
 * Three of these change the whole interface and were the player's own asks:
 * label mode, the key flash, and which movement pad is fitted.  All three pads
 * occupy the same anchor, so switching moves nothing else on screen.
 */
public final class RhPrefs
{
	private RhPrefs() {}

	public enum LabelMode { WORDS, BOTH, KEYS }
	public enum PadVariant { NUMPAD, RING, PUCK }

	public static final String KEY_ENABLED     = "rhEnabled";
	public static final String KEY_LABEL_MODE  = "rhLabelMode";
	public static final String KEY_KEY_FLASH   = "rhKeyFlash";
	public static final String KEY_PAD         = "rhPad";
	public static final String KEY_PAD_CELL    = "rhPadCell";
	public static final String KEY_UI_SCALE    = "rhUiScale";
	public static final String KEY_ATK_SLOTS   = "rhAtkSlots";
	/** "2" since the matrix: six cells, so the old three-satellite pins must not map onto them. */
	public static final String KEY_EQUIP_SLOTS = "rhEquipSlots2";
	public static final String KEY_COUNTS       = "rhCounts";
	public static final String KEY_MACROS       = "rhMacros";
	public static final String KEY_SEARCH_MODE   = "rhSearchMode";
	public static final String KEY_SEARCH_BEFORE = "rhSearchBefore";
	public static final String KEY_SEARCH_COUNT  = "rhSearchCount";

	private static boolean sEnabled = true;
	private static LabelMode sLabelMode = LabelMode.WORDS;
	private static boolean sKeyFlash = true;
	private static PadVariant sPad = PadVariant.NUMPAD;
	/**
	 * Movement key size in design dp.  A preference rather than a constant because
	 * it is under trial: 46 was the handoff's number, 58 is Parhi, Karlson &
	 * Bederson's 9.2mm floor for discrete thumb targets (MobileHCI 2006).  Bigger
	 * keys cost thumb travel; smaller ones cost corrective re-taps, which are
	 * hidden repetition load.  Only the hand can settle it, so it is one tap away.
	 */
	public static final int PAD_CELL_DEFAULT = 58;
	public static final int PAD_CELL_MIN = 40, PAD_CELL_MAX = 72;
	private static int sPadCell = PAD_CELL_DEFAULT;
	private static String[] sAtkSlots;
	private static String[] sEquipSlots;
	private static String[] sCounts;

	/**
	 * Macro slots: a name and a key sequence each, in the format gurrhack's
	 * command panels already parse ({@code ^D} for Ctrl-D, {@code M-x} for Meta,
	 * {@code \e} Escape, {@code \n} Enter, {@code \b} backspace; anything else is
	 * sent as typed).  Stored as alternating name and keys lines -- a sequence
	 * writes Enter as the two characters backslash-n, so newline is free to be
	 * the delimiter here as everywhere else in these prefs.
	 */
	public static final int MACRO_SLOTS = 1;
	private static String[] sMacros = new String[2 * MACRO_SLOTS];

	public static void load(SharedPreferences prefs)
	{
		sSearchMode   = prefs.getBoolean(KEY_SEARCH_MODE, false);
		sSearchBefore = prefs.getBoolean(KEY_SEARCH_BEFORE, true);
		sSearchCount  = Math.max(1, Math.min(9, prefs.getInt(KEY_SEARCH_COUNT, 1)));
		String macros = prefs.getString(KEY_MACROS, "");
		String[] parts = macros.length() == 0 ? new String[0] : macros.split("\n", -1);
		sMacros = new String[2 * MACRO_SLOTS];
		for(int i = 0; i < sMacros.length; i++)
			sMacros[i] = i < parts.length ? parts[i] : "";
		sEnabled   = prefs.getBoolean(KEY_ENABLED, true);
		sKeyFlash  = prefs.getBoolean(KEY_KEY_FLASH, true);
		sLabelMode = parseLabelMode(prefs.getString(KEY_LABEL_MODE, "words"));
		sPad       = parsePad(prefs.getString(KEY_PAD, "numpad"));
		sPadCell   = parseInt(prefs.getString(KEY_PAD_CELL, null),
		                      PAD_CELL_DEFAULT, PAD_CELL_MIN, PAD_CELL_MAX);
		sAtkSlots   = parseSlots(prefs.getString(KEY_ATK_SLOTS, null),
		                         RhCommands.ATK_SLOT_DEFAULT);
		sEquipSlots = parseSlots(prefs.getString(KEY_EQUIP_SLOTS, null),
		                         RhCommands.EQUIP_SLOT_DEFAULT);
		String counts = prefs.getString(KEY_COUNTS, "");
		sCounts = counts.length() == 0 ? new String[0] : counts.split("\n", -1);
	}

	public static boolean enabled()      { return sEnabled; }
	public static LabelMode labelMode()  { return sLabelMode; }
	public static boolean keyFlash()     { return sKeyFlash; }
	public static PadVariant pad()       { return sPad; }
	public static int padCell()          { return sPadCell; }

	public static String[] atkSlots()    { return sAtkSlots; }
	public static String[] equipSlots()  { return sEquipSlots; }

	public static String macroName(int slot) { return sMacros[2 * slot]; }
	public static String macroKeys(int slot) { return sMacros[2 * slot + 1]; }
	public static boolean macroSet(int slot) { return macroKeys(slot).length() > 0; }

	/**
	 * Search mode (2026-09-23): every numpad step also searches, before or after
	 * the move, count times.  See RhOverlay.pressDirection().
	 */
	private static boolean sSearchMode, sSearchBefore = true;
	private static int sSearchCount = 1;

	public static boolean searchMode()   { return sSearchMode; }
	public static boolean searchBefore() { return sSearchBefore; }
	public static int searchCount()      { return sSearchCount; }

	public static void saveSearchMode(SharedPreferences prefs, boolean on, boolean before, int count)
	{
		sSearchMode = on;
		sSearchBefore = before;
		sSearchCount = Math.max(1, Math.min(9, count));
		prefs.edit().putBoolean(KEY_SEARCH_MODE, on).putBoolean(KEY_SEARCH_BEFORE, before)
		     .putInt(KEY_SEARCH_COUNT, sSearchCount).commit();
	}

	public static void saveMacro(SharedPreferences prefs, int slot, String name, String keys)
	{
		sMacros[2 * slot]     = name == null ? "" : name.replace('\n', ' ').trim();
		sMacros[2 * slot + 1] = keys == null ? "" : keys.replace("\n", "");
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < sMacros.length; i++)
		{
			if(i > 0)
				sb.append('\n');
			sb.append(sMacros[i]);
		}
		prefs.edit().putString(KEY_MACROS, sb.toString()).commit();
	}

	/**
	 * Repeat counts for the counted context actions, keyed by their NetHack key.
	 * Stored as alternating key and value lines, which sidesteps the delimiter
	 * problem entirely -- a count's key is a command key like {@code s} or
	 * {@code .}, and there is no separator character those cannot contain.
	 */
	public static int count(String actionKey, int fallback)
	{
		if(sCounts != null)
			for(int i = 0; i + 1 < sCounts.length; i += 2)
				if(sCounts[i].equals(actionKey))
					return parseInt(sCounts[i + 1], fallback);
		return fallback;
	}

	public static void saveCount(SharedPreferences prefs, String actionKey, int value)
	{
		java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<String, Integer>();
		if(sCounts != null)
			for(int i = 0; i + 1 < sCounts.length; i += 2)
				map.put(sCounts[i], parseInt(sCounts[i + 1], 1));
		map.put(actionKey, value);

		StringBuilder sb = new StringBuilder();
		for(java.util.Map.Entry<String, Integer> e : map.entrySet())
		{
			if(sb.length() > 0)
				sb.append('\n');
			sb.append(e.getKey()).append('\n').append(e.getValue());
		}
		String stored = sb.toString();
		sCounts = stored.length() == 0 ? new String[0] : stored.split("\n", -1);
		prefs.edit().putString(KEY_COUNTS, stored).commit();
	}

	private static int parseInt(String s, int fallback)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch(NumberFormatException e)
		{
			return fallback;
		}
	}

	public static void saveAtkSlots(SharedPreferences prefs, String[] slots)
	{
		sAtkSlots = slots;
		prefs.edit().putString(KEY_ATK_SLOTS, joinSlots(slots)).commit();
	}

	public static void saveEquipSlots(SharedPreferences prefs, String[] slots)
	{
		sEquipSlots = slots;
		prefs.edit().putString(KEY_EQUIP_SLOTS, joinSlots(slots)).commit();
	}

	/**
	 * Slots persist as newline-separated keys, with an empty line for an empty
	 * slot.  Newline is the one delimiter no NetHack key sequence contains -- the
	 * keys themselves run to things like {@code M-o}, {@code ^D}, {@code Dm} and
	 * {@code #sit}, so comma, space, pipe and hyphen are all taken.
	 */
	private static String joinSlots(String[] slots)
	{
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < slots.length; i++)
		{
			if(i > 0)
				sb.append('\n');
			if(slots[i] != null)
				sb.append(slots[i]);
		}
		return sb.toString();
	}

	private static String[] parseSlots(String stored, String[] fallback)
	{
		String[] out = new String[fallback.length];
		if(stored == null)
		{
			System.arraycopy(fallback, 0, out, 0, fallback.length);
			return out;
		}
		String[] parts = stored.split("\n", -1);
		for(int i = 0; i < out.length; i++)
			out[i] = (i < parts.length && parts[i].length() > 0) ? parts[i] : null;
		return out;
	}

	/** A ListPreference stores its value as a string; a bad one falls back. */
	private static int parseInt(String v, int fallback, int min, int max)
	{
		if(v == null)
			return fallback;
		try
		{
			return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
		}
		catch(NumberFormatException e)
		{
			return fallback;
		}
	}

	private static LabelMode parseLabelMode(String v)
	{
		if("keys".equals(v))
			return LabelMode.KEYS;
		if("both".equals(v))
			return LabelMode.BOTH;
		return LabelMode.WORDS;
	}

	private static PadVariant parsePad(String v)
	{
		if("ring".equals(v))
			return PadVariant.RING;
		if("puck".equals(v))
			return PadVariant.PUCK;
		return PadVariant.NUMPAD;
	}
}
