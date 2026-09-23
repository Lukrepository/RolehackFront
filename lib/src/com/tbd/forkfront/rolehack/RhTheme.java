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
	}

	public static float uiScale()
	{
		return sUiScale;
	}

	public static float clamp(float v, float min, float max)
	{
		return v < min ? min : (v > max ? max : v);
	}

	/** Handoff dp -> device pixels, with the UI scale applied. */
	public static float dp(Context c, float designUnits)
	{
		return rawDp(c, designUnits) * sUiScale;
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
