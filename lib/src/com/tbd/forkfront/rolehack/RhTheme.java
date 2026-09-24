package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.DisplayMetrics;

/**
 * Design tokens for the Rolehack mobile interface.
 *
 * Source of record: design_handoff_rolehack_mobile/README.md ("Design tokens"),
 * geometry from artboard 2a of "NetHack Mobile.dc.html".
 *
 * The handoff specifies the layout inside a 920x420 CSS-pixel device box and
 * states those units map ~1:1 to logical points on a landscape phone, so every
 * number quoted from it is treated as dp here.
 */
public final class RhTheme
{
	private RhTheme() {}

	// ____________________________________________________________________________________
	// Button faces.  All four are vertical gradients, dark at the bottom, so each
	// pair below is { bottom, top } to match GradientDrawable/LinearGradient order
	// when drawn from the bottom of the face upward.

	/** Default.  Any ordinary command. */
	public static final int[] G90   = { 0xff0a1730, 0xff2f63ad };
	/** Hostile.  Attack, kick, and the armed movement ring. */
	public static final int[] R90   = { 0xff3a0d0d, 0xffc2412e };
	/** Amber.  Mode toggles, the primary contextual action, an open drawer, Pray. */
	public static final int[] A90   = { 0xff3a2a06, 0xffc9a227 };
	/** Slate.  The "off" half of a matched pair: Take off, Remove, Unwield. */
	public static final int[] OFF90 = { 0xff141821, 0xff59617a };
	/** Drop, and only Drop.  A single-purpose colour for a single-purpose hub. */
	public static final int[] TEAL  = { 0xff04302c, 0xff1c9c92 };
	/** Consume, and only Consume.  Same single-purpose logic as TEAL. */
	public static final int[] PINK  = { 0xff3d0a22, 0xffc2417a };
	/**
	 * Reference, not action.  Message history is the only thing on it: a control
	 * that shows you what already happened rather than making something happen,
	 * which is why it does not wear a command colour.
	 */
	public static final int[] VIOLET = { 0xff1a1030, 0xff5a4a9c };

	// ____________________________________________________________________________________
	// Condition badges, by how much they should alarm you.

	/** Stoning, slime, strangling, food poisoning, terminal illness. */
	public static final int COND_DEADLY_BG   = 0xffc2412e;
	public static final int COND_DEADLY_TEXT = 0xffffffff;
	/** Blind, deaf, stunned, confused, hallucinating. */
	public static final int COND_IMPAIR_BG   = 0xffc9a227;
	public static final int COND_IMPAIR_TEXT = 0xff1a1206;
	/** Levitating, flying, riding -- worth knowing, not worth alarm. */
	public static final int COND_MOVE_BG     = 0xff2f63ad;
	public static final int COND_MOVE_TEXT   = 0xffffffff;

	// ____________________________________________________________________________________
	// Face treatment -- the 90s hardware look.

	public static final int   FACE_BORDER      = 0xffffffff;
	public static final float FACE_BORDER_W    = 2f;   // dp, box-sizing: border-box
	public static final float FACE_RADIUS      = 4.5f; // dp, "4-5px" for rectangular faces
	public static final float BEVEL_OFFSET     = 1.5f; // dp, hard inset shadow, no blur
	public static final int   BEVEL_LIGHT      = 0x6bffffff; // rgba(255,255,255,.42)
	public static final int   BEVEL_DARK       = 0x8c000000; // rgba(0,0,0,.55)
	public static final int   DROP_SHADOW      = 0x8c000000; // rgba(0,0,0,.55)
	public static final float DROP_SHADOW_DY   = 2f;   // dp
	public static final float DROP_SHADOW_BLUR = 7f;   // dp, CSS blur radius

	/** Press state: rectangular faces brighten and sink 1dp; circles only brighten. */
	public static final float PRESS_BRIGHTNESS_RECT   = 1.4f;
	public static final float PRESS_BRIGHTNESS_CIRCLE = 1.5f;
	public static final float PRESS_SINK_DP           = 1f;

	// ____________________________________________________________________________________
	// Terminal styles (2026-09-23): the same controls drawn as sculpted keycaps set
	// in a case, the map framed as a CRT.  Source of record: the "Rolehack Terminal
	// Mode" design canvas.  Three skins share that layout (Lucas, 2026-09-24):
	//
	//   Terminal        putty case, dark wells and hood -- the canvas
	//   Terminal light  putty case, tan wells and hood -- the 90s plastic tan, which
	//                   is what the first device build showed by accident
	//   GameCube        indigo case, yellow movement keys, red INTERACT, blue
	//                   EAT/QUAFF/READ, dark-blue SEARCH, grey inventory keys
	//
	// A keycap family is
	// { top-face light, top-face dark, left skirt, skirt, right skirt,
	//   legend, hold legend, raw key }.
	//
	// Families follow today's face colours, so a command keeps its meaning across
	// styles: navy commands become the skin's default key, amber stays orange,
	// red stays red, the "off" slate stays slate.  Drop, Eat/Quaff/Read and Msgs
	// had single-purpose faces; on the terminal skins they become dark keys
	// wearing that colour as their legend.  A few keys also have a role -- the
	// movement pad, INTERACT, EAT/QUAFF/READ, SEARCH, the inventory keys -- which
	// each skin may colour on its own (role()).

	public static final int[] CAP_CREAM = { 0xfffbf8f1, 0xffe3ded2, 0xffdcd6c8, 0xffc9c2b3, 0xffa8a091,
	                                        0xff2a2622, 0xff6e2a0b, 0xff8b4a12 };
	public static final int[] CAP_DARK  = { 0xff4b443e, 0xff35302b, 0xff3b3530, 0xff2c2723, 0xff1b1815,
	                                        0xfff1e8d5, 0xfff2a64a, 0xfff3c46b };
	public static final int[] CAP_SLATE = { 0xff7c7a74, 0xff63615c, 0xff6d6b66, 0xff595853, 0xff43423e,
	                                        0xfffffaf0, 0xffffd796, 0xffffe0a0 };
	public static final int[] CAP_AMBER = { 0xfff8b457, 0xffe38a26, 0xffe59633, 0xffc9761c, 0xff9f5b12,
	                                        0xff2a1503, 0xfffff3dc, 0xff5a3208 };
	public static final int[] CAP_RED   = { 0xffc85240, 0xffa13426, 0xffa83d2d, 0xff8e2e21, 0xff6a1f15,
	                                        0xfffff3ea, 0xffffd796, 0xffffd796 };
	public static final int[] CAP_TEAL  = withLegend(CAP_DARK, 0xff5fd8c9);
	public static final int[] CAP_ROSE  = withLegend(CAP_DARK, 0xfff08bb6);
	public static final int[] CAP_LAV   = withLegend(CAP_DARK, 0xffc2b6ff);

	// GameCube.  Saturated plastics: the C-stick's yellow, a primary red and blue,
	// the X/Y buttons' pale grey, and a hotter red and orange than the terminal's
	// so the skin stays loud all the way through.
	public static final int[] GC_YELLOW = { 0xffffe066, 0xfff5c400, 0xffeab800, 0xffd4a300, 0xffa67f00,
	                                        0xff3a2a00, 0xff4a2f00, 0xff6b4a00 };
	public static final int[] GC_RED    = { 0xfff0505f, 0xffc8102e, 0xffc81e33, 0xffa50f22, 0xff720816,
	                                        0xfffff4f2, 0xffffd9a0, 0xffffe3b0 };
	public static final int[] GC_BLUE   = { 0xff5c93ff, 0xff2a62d8, 0xff2d62cf, 0xff1f4bb0, 0xff153582,
	                                        0xfff2f6ff, 0xffd6e4ff, 0xffdfe9ff };
	public static final int[] GC_NAVY   = { 0xff3a4fb0, 0xff22348a, 0xff273a90, 0xff1c2b74, 0xff121d52,
	                                        0xffe8ecff, 0xffffd21f, 0xffffe066 };
	public static final int[] GC_GREY   = { 0xffe4e4ea, 0xffbdbdc8, 0xffc4c4ce, 0xffa7a7b4, 0xff80808e,
	                                        0xff24242c, 0xff15175a, 0xff15175a };
	public static final int[] GC_ORANGE = { 0xffffa347, 0xffff6f00, 0xfff07000, 0xffd45a00, 0xff9c3f00,
	                                        0xff2a1000, 0xfffff0dc, 0xff5a2600 };
	public static final int[] GC_SCARLET = { 0xffff6b5b, 0xffff2a1a, 0xfff02818, 0xffcc1a0c, 0xff8f1006,
	                                         0xfffff5ee, 0xffffe28a, 0xffffe8a8 };
	public static final int[] GC_DARK   = { 0xff4a4c6e, 0xff34365a, 0xff3a3c62, 0xff2b2d4f, 0xff1a1b36,
	                                        0xffeef0ff, 0xffffd21f, 0xffffd21f };
	public static final int[] GC_TEAL   = withLegend(GC_DARK, 0xff5fe3d2);
	/**
	 * Long rest: Rest's orange, darker (Lucas, 2026-09-24), so the two read apart
	 * when the swipe brings one in over the other.  Legends stay dark on both,
	 * about 4.5:1 or better against the face.
	 */
	public static final int[] CAP_AMBER_DARK = { 0xffe08c3a, 0xffc2661a, 0xffc86e22, 0xffa85716, 0xff7c3e0c,
	                                             0xff2a1503, 0xffffe8c8, 0xff4a2406 };
	public static final int[] GC_ORANGE_DARK = { 0xffe0701f, 0xffc05200, 0xffb84e00, 0xff9a4000, 0xff6a2a00,
	                                             0xff2a1000, 0xfffff0dc, 0xff4a1e00 };
	public static final int[] GC_ROSE   = withLegend(GC_DARK, 0xffff8fc0);
	public static final int[] GC_LAV    = withLegend(GC_DARK, 0xffc8bcff);

	public static final int CAP_T1 = 0, CAP_T2 = 1, CAP_SL = 2, CAP_SM = 3, CAP_SR = 4,
	                        CAP_LEGEND = 5, CAP_HOLD = 6, CAP_RAW = 7;

	private static int[] withLegend(int[] base, int legend)
	{
		int[] out = base.clone();
		out[CAP_LEGEND] = legend;
		return out;
	}

	/** The keycap family a face colour maps to, in the current skin. */
	public static int[] capFor(int[] face)
	{
		boolean gc = sStyle == STYLE_GAMECUBE;
		if(face == A90)    return gc ? GC_ORANGE  : CAP_AMBER;
		if(face == R90)    return gc ? GC_SCARLET : CAP_RED;
		if(face == OFF90)  return CAP_SLATE;
		if(face == TEAL)   return gc ? GC_TEAL : CAP_TEAL;
		if(face == PINK)   return gc ? GC_ROSE : CAP_ROSE;
		if(face == VIOLET) return gc ? GC_LAV  : CAP_LAV;
		return gc ? GC_DARK : CAP_DARK;
	}

	/** Long rest's keycap in the current skin. */
	public static int[] longRestCap()
	{
		return sStyle == STYLE_GAMECUBE ? GC_ORANGE_DARK : CAP_AMBER_DARK;
	}

	// Key roles: keys a skin may colour on their own rather than by face colour.
	public static final int ROLE_MOVE = 0, ROLE_INTERACT = 1, ROLE_CONSUME = 2,
	                        ROLE_SEARCH = 3, ROLE_INVENTORY = 4;

	/** The keycap family for a key role in the current skin. */
	public static int[] role(int role)
	{
		if(sStyle == STYLE_GAMECUBE)
		{
			switch(role)
			{
				case ROLE_MOVE:      return GC_YELLOW;
				case ROLE_INTERACT:  return GC_RED;
				case ROLE_CONSUME:   return GC_BLUE;
				case ROLE_SEARCH:    return GC_NAVY;
				default:             return GC_GREY;
			}
		}
		switch(role)
		{
			case ROLE_MOVE:
			case ROLE_INTERACT:  return CAP_CREAM;
			case ROLE_CONSUME:   return CAP_ROSE;
			default:             return CAP_DARK;
		}
	}

	/** A context key's legend when it has something to offer: backlit amber. */
	public static final int CAP_LIT      = 0xffffc166;
	public static final int LAMP_AMBER   = 0xffffb347, LAMP_AMBER_OFF = 0xff4a3413;
	public static final int LAMP_RED     = 0xffff5a44, LAMP_RED_OFF   = 0xff4a1712;
	public static final int LAMP_GREEN   = 0xff6dff7a, LAMP_GREEN_OFF = 0xff173a1d;

	public static final int GLASS_BG = 0xff090d0a;

	// The case, per skin: { top, bottom } gradients for the body, the hood and the
	// wells, the lip's text, and how dark the well's overhang shadow is.
	private static final int[][] CASE_BG = {
		{ 0xffe8dfc8, 0xffd5c9ab }, { 0xffe8dfc8, 0xffd5c9ab }, { 0xff3b40a0, 0xff2a2d7c } };
	private static final int[][] HOOD_BG = {
		{ 0xff3a322b, 0xff221d19 }, { 0xffaaa18c, 0xff968d79 }, { 0xff23265e, 0xff171941 } };
	private static final int[][] WELL_BG = {
		{ 0xff171411, 0xff241f1b }, { 0xff958c78, 0xffa39a86 }, { 0xff15173f, 0xff1f2257 } };
	private static final int[] LIP_TEXT  = { 0xffc9bea6, 0xff3b342b, 0xffb9bdf0 };
	private static final int[] WELL_SHADE = { 0xb3000000, 0x66000000, 0xb3000000 };

	private static int skin() { return sStyle == STYLE_TERMINAL_LIGHT ? 1 : sStyle == STYLE_GAMECUBE ? 2 : 0; }

	public static int[] caseBg()   { return CASE_BG[skin()]; }
	public static int[] hoodBg()   { return HOOD_BG[skin()]; }
	public static int[] wellBg()   { return WELL_BG[skin()]; }
	public static int   lipText()  { return LIP_TEXT[skin()]; }
	public static int   wellShade(){ return WELL_SHADE[skin()]; }

	/** Screen text per phosphor: colour, amber, green, white. */
	private static final int[] PHOSPHOR_TEXT = { 0xffd7e3d0, 0xfff5a93a, 0xff52e472, 0xffcdd4e0 };
	private static final int[] PHOSPHOR_DIM  = { 0xff8c9a88, 0xff9c6a22, 0xff2c8c46, 0xff7d8698 };

	public static final int STYLE_COLOURFUL = 0, STYLE_TERMINAL = 1,
	                        STYLE_TERMINAL_LIGHT = 2, STYLE_GAMECUBE = 3;
	private static int sStyle = STYLE_TERMINAL;
	private static int sPhosphor;

	/** True when the interface is drawn as a terminal: keycaps, case, framed map. */
	public static boolean terminal()
	{
		return sStyle != STYLE_COLOURFUL;
	}

	private static boolean sCaseless;

	/**
	 * The terminal without its case (Lucas, 2026-09-24): the map fills the screen,
	 * the keys sit on translucent wells that still swallow a near miss, and the
	 * message and status lines become translucent bands.  For the levels where
	 * the whole map is worth seeing at once.  GAME -> "Case on/off" flips it.
	 */
	public static boolean caseless()
	{
		return sCaseless && terminal();
	}

	/** True when screen text follows the game's own colours rather than one phosphor. */
	public static boolean phosphorColour()
	{
		return sPhosphor == 0;
	}

	public static int phosphorText() { return PHOSPHOR_TEXT[sPhosphor]; }
	public static int phosphorDim()  { return PHOSPHOR_DIM[sPhosphor]; }

	// ____________________________________________________________________________________
	// Terminal fonts, each a preference.  Every face is OFL and bundled (see
	// assets/fonts/OFL.txt); "system" is Android's own condensed sans.  VT323 draws
	// small for its size, so each screen font carries the scale that makes its
	// text read at the same height as the others.

	private static String sKeyFont = "plex";
	private static String sScreenFont = "vt323";
	private static Typeface sCapFont;
	private static Typeface sScreenTypeface;

	/** Keycap legends: IBM Plex Sans Condensed by default, as on the canvas. */
	public static Typeface capFont(Context c)
	{
		if(sCapFont == null)
		{
			if("barlow".equals(sKeyFont))
				sCapFont = load(c, "fonts/BarlowSemiCondensed-SemiBold.ttf", Typeface.SANS_SERIF, Typeface.BOLD);
			else if("spacemono".equals(sKeyFont))
				sCapFont = monoBold(c);
			else if("system".equals(sKeyFont))
				sCapFont = Typeface.create("sans-serif-condensed", Typeface.BOLD);
			else
				sCapFont = load(c, "fonts/IBMPlexSansCondensed-SemiBold.ttf", Typeface.SANS_SERIF, Typeface.BOLD);
		}
		return sCapFont;
	}

	/** The glass's text: VT323 by default. */
	public static Typeface screenFont(Context c)
	{
		if(sScreenTypeface == null)
		{
			if("plexmono".equals(sScreenFont))
				sScreenTypeface = load(c, "fonts/IBMPlexMono-Regular.ttf", Typeface.MONOSPACE, Typeface.NORMAL);
			else if("sharetech".equals(sScreenFont))
				sScreenTypeface = load(c, "fonts/ShareTechMono-Regular.ttf", Typeface.MONOSPACE, Typeface.NORMAL);
			else if("spacemono".equals(sScreenFont))
				sScreenTypeface = monoRegular(c);
			else
				sScreenTypeface = load(c, "fonts/VT323-Regular.ttf", Typeface.MONOSPACE, Typeface.NORMAL);
		}
		return sScreenTypeface;
	}

	/** Size multiplier for the screen font, so each reads at about the same height. */
	public static float screenFontScale()
	{
		if("vt323".equals(sScreenFont))
			return 1.35f;
		if("sharetech".equals(sScreenFont))
			return 1.1f;
		return 1f;
	}

	// ____________________________________________________________________________________
	// Surfaces.

	public static final int   DEVICE_BG          = 0xff05070d;
	public static final int   MAP_BG             = 0xff000000;
	public static final int[] HEADER_BAND        = { 0xff070b14, 0xff101a2e }; // { bottom, top }
	public static final float HEADER_HEIGHT      = 38f;
	public static final int   HEADER_RULE        = 0x80ffffff; // rgba(255,255,255,.5)
	public static final float HEADER_RULE_W      = 2f;
	public static final int   INFO_PANEL_BG      = 0xeb04070e; // rgba(4,7,14,.92)
	/**
	 * The same colour, fully opaque, for a popover that opens across live text.
	 * The chip row's unselected chips sit at 66% by design, so anything behind
	 * the row reads straight through them.
	 */
	public static final int   POPOVER_BG         = 0xff04070e;
	public static final int   INFO_PANEL_BORDER  = 0x59ffffff; // rgba(255,255,255,.35)
	public static final float INFO_PANEL_RADIUS  = 3f;
	public static final int   DRAWER_BG          = 0xff080c16;
	public static final float DRAWER_RADIUS      = 5f;
	public static final int[] DRAWER_TITLE_BAR   = { 0xff0a1730, 0xff1c3a68 }; // { bottom, top }
	public static final int   MODAL_SCRIM        = 0xbd020409; // rgba(2,4,9,.74)
	public static final int   RING_BACKPLATE     = 0x332f63ad; // rgba(47,99,173,.2) centre
	public static final int   RING_BACKPLATE_RIM = 0x1fffffff; // rgba(255,255,255,.12)

	// ____________________________________________________________________________________
	// Semantic colors.

	public static final int RAW_KEY          = 0xffffd97a;
	public static final int RAW_KEY_DIM      = 0xd9ffd97a; // rgba(255,217,122,.85)
	public static final int HP_FILL          = 0xffe8c04a;
	public static final int PW_FILL          = 0xff6ea8ff;
	public static final int GOLD             = 0xffe8c04a;
	public static final int BADGE_BG         = 0xffc9a227;
	public static final int BADGE_TEXT       = 0xff1a1206;
	/** Serious status -- Hungry, Stressed: between the amber caution and the red. */
	public static final int STATUS_SERIOUS_BG   = 0xffd9772b;
	public static final int STATUS_SERIOUS_TEXT = 0xff1a1206;
	public static final int BAR_TROUGH_BG    = 0x24ffffff; // rgba(255,255,255,.14)
	public static final int BAR_TROUGH_RIM   = 0x40ffffff; // rgba(255,255,255,.25)
	public static final int LABEL_MUTED      = 0x80ffffff; // rgba(255,255,255,.5)
	public static final int VALUE_SECONDARY  = 0x9effffff; // rgba(255,255,255,.62)
	public static final int TEXT             = 0xffffffff;
	public static final int MESSAGE_TEXT     = 0xffe6e9ef;

	// ____________________________________________________________________________________
	// Typography.  Two families only: Space Mono for every control label, raw key
	// and stat; Outfit for drawer item words, the one place where reading beats
	// scanning.

	private static Typeface sMonoBold;
	private static Typeface sMonoRegular;
	private static Typeface sOutfitSemi;

	public static Typeface monoBold(Context c)
	{
		if(sMonoBold == null)
			sMonoBold = load(c, "fonts/SpaceMono-Bold.ttf", Typeface.MONOSPACE, Typeface.BOLD);
		return sMonoBold;
	}

	public static Typeface monoRegular(Context c)
	{
		if(sMonoRegular == null)
			sMonoRegular = load(c, "fonts/SpaceMono-Regular.ttf", Typeface.MONOSPACE, Typeface.NORMAL);
		return sMonoRegular;
	}

	public static Typeface outfitSemi(Context c)
	{
		if(sOutfitSemi == null)
			sOutfitSemi = load(c, "fonts/Outfit-SemiBold.ttf", Typeface.SANS_SERIF, Typeface.BOLD);
		return sOutfitSemi;
	}

	private static Typeface load(Context c, String asset, Typeface fallback, int style)
	{
		try
		{
			Typeface tf = Typeface.createFromAsset(c.getAssets(), asset);
			if(tf != null)
				return tf;
		}
		catch(Exception e)
		{
			// Asset missing or unreadable -- fall back rather than take the app down.
		}
		return Typeface.create(fallback, style);
	}

	// ____________________________________________________________________________________
	// Scaling.  UI scale sizes the thumb clusters independently of map zoom
	// (handoff, "Settings"); the map has its own pinch zoom and must not follow it.

	public static final float UI_SCALE_MIN = 0.85f;
	public static final float UI_SCALE_MAX = 1.2f;

	private static float sUiScale = 1f;

	public static void loadPrefs(SharedPreferences prefs)
	{
		sUiScale = clamp(prefs.getInt("rhUiScale", 100) / 100f, UI_SCALE_MIN, UI_SCALE_MAX);
		// The colourful style is retired (Lucas, 2026-09-24).  What it had that the
		// terminal lacked was the map showing beneath the controls, and that is now
		// the caseless terminal -- so a saved "colourful" opens caseless.
		String style = prefs.getString("rhStyle", "terminal");
		sStyle = "terminal_light".equals(style) ? STYLE_TERMINAL_LIGHT
		       : "gamecube".equals(style) ? STYLE_GAMECUBE
		       : STYLE_TERMINAL;
		sCaseless = !prefs.getBoolean("rhCase", !"colourful".equals(style));
		String p = prefs.getString("rhPhosphor", "color");
		sPhosphor = "amber".equals(p) ? 1 : "green".equals(p) ? 2 : "white".equals(p) ? 3 : 0;

		String key = prefs.getString("rhKeyFont", "plex");
		if(!key.equals(sKeyFont))
		{
			sKeyFont = key;
			sCapFont = null;
		}
		String screen = prefs.getString("rhScreenFont", "vt323");
		if(!screen.equals(sScreenFont))
		{
			sScreenFont = screen;
			sScreenTypeface = null;
		}
	}

	/**
	 * The terminal style is a whole case that has to fit the screen, so it caps
	 * the scale at the largest one where both banks fit the height and the glass
	 * keeps a usable width (RhOverlay.updateFitLimit).  The player's own scale
	 * still applies below the cap.  The colourful style has no cap: its controls
	 * float, and nothing in it has to meet an edge.
	 */
	private static float sFitLimit = Float.MAX_VALUE;

	/** Set the cap; true when it moved, which means every face needs rebuilding. */
	public static boolean setFitLimit(float limit)
	{
		if(Math.abs(limit - sFitLimit) < 0.005f)
			return false;
		sFitLimit = limit;
		return true;
	}

	public static float uiScale()
	{
		return Math.min(sUiScale, sFitLimit);
	}

	public static float clamp(float v, float min, float max)
	{
		return v < min ? min : (v > max ? max : v);
	}

	/** Handoff dp -> device pixels, with the UI scale applied. */
	public static float dp(Context c, float designUnits)
	{
		return rawDp(c, designUnits) * uiScale();
	}

	/** Handoff dp -> device pixels, ignoring the UI scale (for map-locked geometry). */
	public static float rawDp(Context c, float designUnits)
	{
		DisplayMetrics m = c.getResources().getDisplayMetrics();
		return designUnits * m.density;
	}

	public static int dpi(Context c, float designUnits)
	{
		return Math.round(dp(c, designUnits));
	}

	public static int rawDpi(Context c, float designUnits)
	{
		return Math.round(rawDp(c, designUnits));
	}

	// ____________________________________________________________________________________
	/** Per-channel multiply used for the press state's brightness filter. */
	public static int brighten(int argb, float factor)
	{
		if(factor == 1f)
			return argb;
		int a = (argb >>> 24) & 0xff;
		int r = Math.min(255, Math.round(((argb >> 16) & 0xff) * factor));
		int g = Math.min(255, Math.round(((argb >>  8) & 0xff) * factor));
		int b = Math.min(255, Math.round(( argb        & 0xff) * factor));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
