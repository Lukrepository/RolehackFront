package com.tbd.forkfront.rolehack;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rolehack mobile interface: a thumb-anchored control system that floats
 * over a full-bleed map.
 *
 * Layout is artboard 2a of the design handoff, whose geometry is quoted inside a
 * 920x420 box of CSS pixels; the handoff states those map ~1:1 to logical points
 * on a landscape phone, so every number below is design dp fed through
 * {@link RhTheme#dp}.  Controls are anchored to screen edges, not laid out in a
 * flow, so the arrangement survives any aspect ratio.
 *
 * This class owns geometry and gesture state only.  It emits raw NetHack key
 * sequences through {@link Host} and never inspects game state directly.
 *
 * Two styles share every control and gesture (RhTheme.terminal()).  The
 * colourful style floats the controls over a full-bleed map.  The terminal
 * style (2026-09-23, from the "Rolehack Terminal Mode" design canvas) sets
 * them as keycaps in the three wells of a terminal case -- a bank each side
 * and a deck under the screen -- and frames the map as a CRT: see RhCase for
 * the frame, RhScreen for the glass, and the T_ constants below for where
 * each key sits.  Positions are kept to the same thumb as in the colourful
 * style, so switching styles does not retrain a hand.
 */
public class RhOverlay extends FrameLayout
{
	/** The seam between the interface and the rest of ForkFront. */
	public interface Host
	{
		/** Run a key sequence in Cmd.KeySequnece notation ("F", "^D", "M-p", "20."). */
		void sendCommand(String keySequence);

		/** Open ForkFront's own settings activity (the MENU face). */
		void openSettings();

		/**
		 * Show or hide ForkFront's soft keyboard -- its own layout, not the system
		 * IME, which is what makes Ctrl and Meta reachable at all.
		 */
		void toggleKeyboard();

		/** Where the map should centre has changed; ask {@link #mapArea()} again. */
		void mapAreaChanged();
	}

	// Gesture timings, from the handoff's "Interactions".
	private static final int HUB_HOLD_MS    = 380;
	private static final int CENTRE_HOLD_MS = 420;
	/** Hold a pinned slot this long to clear it. */
	private static final int SLOT_CLEAR_MS  = 420;

	/*
	 * Flick-to-select, on OFFENSE.  A marking menu in Kurtenbach & Buxton's
	 * sense: press the hub and slide a short way in a node's direction, and that
	 * node fires on release without the radial ever needing to be looked at.
	 * The same wedge logic serves the novice: tap the hub, see the radial, then
	 * drag toward the node you want.  The two modes share one gesture, so
	 * looking at the menu rehearses the eyes-free version.
	 *
	 * The radial is revealed only once the finger has been down FLICK_REVEAL_MS,
	 * so a quick expert flick never flashes the menu (K&B use ~330ms; this is
	 * shorter because the finger has to travel FLICK_MIN anyway).  Wedge width
	 * comes from the node bearings, which are spread at 45 degrees: Lai & Zhang
	 * (CHI 2014) found 36-degree thumb wedges the most error-prone and 45 worse
	 * than 60, so three nodes over 135 degrees is the narrowest this should go.
	 */
	/** Movement below this is a tap or a hold, not a flick. */
	private static final float FLICK_SLOP_DP   = 10f;
	/** A flick has to travel at least this far to fire; shorter is treated as a tap. */
	private static final float FLICK_MIN_DP    = 26f;
	/** The outermost wedges extend this far past their node's half-step. */
	private static final float FLICK_ARC_SLACK = 15f;
	private static final int   FLICK_REVEAL_MS = 200;

	/**
	 * Fan slot geometry.  Radius and step are per-hub, because a six-node fan
	 * cannot use a three-node fan's numbers -- but within one fan the radius is
	 * uniform, and that part is a constraint rather than a preference: an earlier
	 * revision varied it per slot and slots 3 and 4 overlapped.
	 *
	 * 44 is the floor for every slot including the tail ones.  An earlier taper ran
	 * 54/50/44/40/34/34, which put four of ATTACK's six nodes under the touch
	 * minimum.
	 */
	private static final float[] FAN_SIZE   = { 54f, 50f, 46f, 44f, 44f, 44f };
	private static final float[] FAN_RADIUS_CORNER = { -1f, -1f, 14f, 20f, -1f, -1f }; // -1 = circle
	private static final float[] FAN_ROTATE = { 0f, 0f, -11f, 9f, 0f, 0f };

	// Movement numpad, tucked fully into the bottom-left corner so the left thumb
	// rests on it without reaching.
	private static final float PAD_LEFT   = 12f;
	private static final float PAD_BOTTOM = 12f;
	/**
	 * Wider gutters than the 4dp the handoff drew.  Between-cell presses were
	 * landing on the map, and more air between targets is the whole point of the
	 * pad's geometry -- see the mold in buildNumpad().
	 */
	private static final float PAD_GAP    = 8f;
	private static final float PAD_IDLE_ALPHA = 0.74f;
	/**
	 * Cell size is a preference (RhPrefs.padCell) while it is under trial, so the
	 * pad's footprint is fixed at construction rather than compile time.  OFFENSE,
	 * DROP and the context radial all derive their positions from it -- see
	 * hubCx(), hubCy() and ctxRadialRadius() -- so changing the key size never
	 * needs a second number changed by hand.
	 */
	private float mPadCell;
	private float mPadBox;
	/** Air between the pad's edge and the nearest control that keys off it. */
	private static final float PAD_AIR = 4f;

	/**
	 * Context radial, held off the numpad's centre cell.  The step is a floor: 26
	 * degrees is what keeps adjacent nodes 59dp apart against their 46dp diameter.
	 * The radius is derived from the pad in ctxRadialRadius(), since it has to
	 * clear whatever size the pad is.
	 */
	private static final float CTX_RADIAL_STEP   = 26f;
	private static final float CTX_RADIAL_A0     = -90f;
	private static final float CTX_RADIAL_SIZE   = 46f;
	/** Air between the pad's boundary and a radial node's near edge, at the worst bearing. */
	private static final float CTX_RADIAL_AIR    = 11f;

	// Context strip, now along the freed bottom edge.  Heights 46/52/46 -- the
	// middle face is the primary and is taller for it.
	private static final float CTX_STRIP_LEFT   = 396f;
	private static final float CTX_STRIP_BOTTOM = 12f;
	private static final float CTX_STRIP_W      = 104f;
	private static final float CTX_STRIP_GAP    = 8f;
	private static final float[] CTX_STRIP_H    = { 46f, 52f, 46f };

	// Count chips, above a counted context face.
	private static final int   CHIP_HOLD_MS   = 360;
	private static final float CHIP_SIZE      = 44f;
	private static final float CHIP_GAP       = 4f;
	private static final float CHIP_GAP_ABOVE = 7f;
	/** Five 44dp chips with four 4dp gaps: the presets, then "n". */
	private static final float CHIP_ROW_W     = 5 * CHIP_SIZE + 4 * CHIP_GAP;

	/** Satellite and pinned-point faces. */
	private static final float SAT_SIZE = 44f;

	/*
	 * Terminal style geometry, design dp, from the design canvas.  The wells are
	 * RhCase's; each bank is the numpad's width plus its well, so the banks grow
	 * and shrink with the Movement key size setting and every key inside them is
	 * laid out against that width.
	 *
	 *   left bank     REST ×20 | MSGS         right bank   MENU WORLD GAME KEYS
	 *                 SACRIFICE | macro                    INVENTORY
	 *                 DROP | pinned slot 1                 Wear   Put on  Wield
	 *                 numpad                               Take off Remove Swap
	 *                                                      spare | EAT QUAFF READ
	 *   deck          OFFENSE, pinned slots 2-3,           spare |
	 *                 LOOK, context                        SEARCH | INTERACT
	 */
	private static final float T_KEY       = 58f;  // the square keys in the banks
	private static final float T_GAP       = 6f;
	private static final float T_ROW_GAP   = 11f;
	private static final float T_ROW1_H    = 40f;  // REST/MSGS and MENU..KEYS
	private static final float T_SLOT_W    = 50f;  // pinned slots 2-3 on the deck
	private static final float T_STRIP_W   = 116f; // LOOK and the context key
	private static final float T_RIGHT_COL = 88f;  // EAT/QUAFF/READ over INTERACT
	private static final float T_INTERACT_H = 105f;
	private static final float T_CONSUME_H  = 68f;
	private static final float T_SEARCH_H   = 63f;
	private static final float T_SPARE_H    = 52f;
	private static final float T_EQ_BAR     = 28f;
	private static final float T_EQ_CELL_H  = 48f;

	private final Activity mContext;
	private final Host mHost;
	private final Handler mHandler = new Handler();

	private RhFlash mFlash;
	/** Terminal style, fixed for the life of one build(). */
	private boolean mTerm;
	private RhCase mCase;
	private RhScreen mScreen;
	/** The left bank's middle row (SACRIFICE, the macro), centred once the height is known. */
	private final List<View> mTermRow2 = new ArrayList<View>();
	private RhHeader mHeader;
	private RhStatusPanel mStatusPanel;
	private RhMessagePanel mMessagePanel;
	private RhFace mRestFace;
	private ViewGroup mRestChips;
	/** Terminal style: Long rest, scrolled out of sight beside Rest (RhScrollWell). */
	private RhFace mLongFace;
	private ViewGroup mLongChips;
	private RhScrollWell mRestWell;
	private String mMessageText = "";
	private int mMessageMore;
	private RhStatus mStatus;
	private final List<RhFace> mPadCells = new ArrayList<RhFace>();
	private RhFace mPadCentre;
	private ViewGroup mPadMold;
	private ViewGroup mCtxRadial;
	/** Radial nodes, per hub id -- OFFENSE and EQUIP both have one. */
	private final java.util.Map<String, ViewGroup> mRadials =
			new java.util.HashMap<String, ViewGroup>();
	private RhFace mArmedBanner;
	private final List<RhFace> mCtxStrip = new ArrayList<RhFace>();
	private final List<ViewGroup> mChipRows = new ArrayList<ViewGroup>();
	/** Which counted face has its chip row open, by command key. */
	private String mChipsOpen;
	private final List<RhFace> mTopRight = new ArrayList<RhFace>();
	private final List<HubView> mHubs = new ArrayList<HubView>();
	private final List<RhFace> mAtkSlots = new ArrayList<RhFace>();
	private RhDrawer mDrawer;

	// State.  The interface layer needs only this; everything else comes from the
	// game core.
	private String mFanOpen;          // hub id, or null -- one fan at a time
	private String mDrawerOpen;       // group id, or null -- exclusive with mFanOpen
	private RhCommands.Item mArmed;   // pending direction, or null
	private boolean mCtxRadialOpen;
	/** Which hub's radial is open, by hub id, or null. */
	private String mRadialOpen;
	private boolean mPortrait;
	/** True while the soft keyboard owns the bottom of the window. */
	private boolean mSuppressed;
	/** True while the core is waiting for a direction of its own accord. */
	private boolean mExpectsDirection;

	/**
	 * A command picked up and waiting for a slot, and which slot groups will take
	 * it.  Only one assignment can be live at a time.
	 *
	 * A pick-up from ATTACK's fan or EQUIP's radial offers that hub's slots only.
	 * A pick-up from a drawer offers both, because a drawer row belongs to no hub
	 * and the whole point of the mechanic is re-tuning the set per role or per
	 * task -- a role that cannot two-weapon frees a star point for something else.
	 */
	private static final int ASSIGN_ATTACK = 0;
	private static final int ASSIGN_EQUIP  = 1;
	private static final int ASSIGN_BOTH   = 2;

	private RhCommands.Item mAssign;
	private int mAssignTarget;
	/** With ASSIGN_FAN: the hub whose fan is taking the command. */
	private String mAssignHub;
	/**
	 * A hub's own fan, taking a command into one of its nodes (2026-09-24).  From
	 * the drawer that hub opened; the fan opens with its nodes lit.
	 */
	private static final int ASSIGN_FAN = 3;
	/** The hub whose tap or hold opened the drawer, or null for MENU / WORLD / GAME. */
	private RhCommands.Hub mDrawerHub;
	/** Each fan hub's nodes, as keys (RhPrefs.fanSlots). */
	private final java.util.Map<String, String[]> mFanKeys = new java.util.HashMap<String, String[]>();
	/** The modal layer under anything open; see syncModal(). */
	private ModalScrim mScrim;

	private boolean assignAccepts(boolean attackGroup)
	{
		if(mAssign == null)
			return false;
		if(mAssignTarget == ASSIGN_BOTH)
			return true;
		return attackGroup ? mAssignTarget == ASSIGN_ATTACK : mAssignTarget == ASSIGN_EQUIP;
	}

	/** Pinned slot contents, as keys, mirrored from RhPrefs and persisted on change. */
	private String[] mAtkSlotKeys;
	private String[] mEquipSlotKeys;

	private RhCommands.ContextAction[] mCtxActions = new RhCommands.ContextAction[0];

	// ____________________________________________________________________________________
	public RhOverlay(Activity context, Host host)
	{
		super(context);
		mContext = context;
		mHost = host;
		mPadCell = RhPrefs.padCell();
		mPadBox  = 3 * mPadCell + 2 * PAD_GAP;
		setClipChildren(false);
		setClipToPadding(false);
		build();
	}

	/** Attach above the map, below nothing.  The map view sits at index 0 of map_frame. */
	public static RhOverlay attach(Activity context, int mapFrameId, Host host)
	{
		ViewGroup frame = (ViewGroup)context.findViewById(mapFrameId);
		if(frame == null)
			return null;

		// The activity can re-attach more than once -- NH_State's constructor ends by
		// calling setContext(), and both create the interface.  Two overlays stacked
		// here means gestures reach the top one while the one underneath goes on
		// drawing its own stale faces, so evict any previous instance first.
		for(int i = frame.getChildCount() - 1; i >= 0; i--)
			if(frame.getChildAt(i) instanceof RhOverlay)
				frame.removeViewAt(i);

		RhOverlay ovl = new RhOverlay(context, host);
		frame.addView(ovl, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return ovl;
	}

	// ____________________________________________________________________________________
	// Construction.  Children are added in ascending z order; the handoff's
	// explicit z-indexes (pad 18, hubs 19/20/21, status 22, strip 24, radial 26,
	// banner 28, message 29) are honoured by this ordering.

	private void build()
	{
		// Pinned slots persist, so they are the first thing the layout needs.
		mAtkSlotKeys   = RhPrefs.atkSlots().clone();
		mEquipSlotKeys = RhPrefs.equipSlots().clone();
		mFanKeys.clear();
		for(RhCommands.Hub h : RhCommands.HUBS)
		{
			String[] keys = RhPrefs.fanSlots(h.id);
			if(keys != null)
				mFanKeys.put(h.id, keys.clone());
		}

		mTerm = RhTheme.terminal();
		mHeader = null;
		mStatusPanel = null;
		mBadges = null;
		mMessagePanel = null;
		mCase = null;
		mScreen = null;
		mTermRow2.clear();
		mLongFace = null;
		mLongChips = null;
		mRestWell = null;

		// The terminal's header, status and badges are lines on its glass, so the
		// frame goes in first -- lowest in z -- and those three are not built.
		if(mTerm)
			buildTerminalFrame();
		else
		{
			buildHeader();
			buildStatusPanel();
		}
		buildNumpad();
		buildContextStrip();
		buildPrayColumn();
		if(!mTerm)
			buildBadgeColumn();
		buildTopRight();
		if(mTerm)
			buildRightMacros();

		// The radial containers must exist before the hubs, because building EQUIP
		// populates the equip radial as it goes.
		mCtxRadial = new FrameLayout(mContext);
		mCtxRadial.setVisibility(GONE);
		addView(mCtxRadial, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		buildHubs();
		buildArmedBanner();

		mScrim = new ModalScrim(mContext);
		mScrim.setVisibility(GONE);
		addView(mScrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		// Above the hub layer: the fans and radials sweep up through this band, and
		// the message that prompted an action must not be hidden by the control
		// answering it.
		if(!mTerm)
			buildMessagePanel();
		buildMessageHistoryButton();

		mDrawer = new RhDrawer(mContext, new RhDrawer.Listener()
		{
			@Override
			public void onItem(RhCommands.Item item, View from)
			{
				closeDrawer();
				execute(item, from);
			}

			@Override
			public void onItemPin(RhCommands.Item item)
			{
				// Where it can go depends on who opened the drawer: OFFENSE's
				// points, the equip cells, or the opening hub's own fan.
				RhCommands.Hub from = mDrawerHub;
				closeDrawer();
				if(item == RhCommands.SEARCH_MODE || item == RhCommands.CASE_TOGGLE
						|| item == RhCommands.STATUS_TOGGLE)
					return;
				pickUp(item, assignTargetFor(from), from);
			}

			@Override
			public void onDismiss()
			{
				closeDrawer();
			}
		});
		mDrawer.setVisibility(GONE);
		addView(mDrawer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		mFlash = new RhFlash(mContext);
		addView(mFlash, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		if(mTerm)
			post(new Runnable() { @Override public void run() { placeTermRow2(); } });
	}

	// ____________________________________________________________________________________
	// The terminal frame.  The case draws the wells and the hood and swallows
	// every touch off the glass; the screen draws messages and status on the
	// glass itself.  The map underneath is told to centre between the screen's
	// two bands -- see mapArea().

	private void buildTerminalFrame()
	{
		mCase = new RhCase(mContext, termBank());
		addView(mCase, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		mScreen = new RhScreen(mContext, new RhScreen.Listener()
		{
			@Override
			public void onHistory()
			{
				execute(RhCommands.PREV_MSGS, mScreen);
			}
		});
		LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		lp.leftMargin   = RhTheme.dpi(mContext, RhCase.glassSide(termBank()));
		lp.rightMargin  = lp.leftMargin;
		lp.topMargin    = RhTheme.dpi(mContext, RhCase.glassTop());
		lp.bottomMargin = RhTheme.dpi(mContext, RhCase.glassBottom());
		addView(mScreen, lp);
		if(mStatus != null)
			mScreen.setStatus(mStatus);
		mScreen.setMessage(mMessageText, mMessageMore);
		updateLamps();
	}

	/**
	 * Terminal style: where the map should centre, in this view's coordinates --
	 * the glass between the message and status bands, 2dp in from its sides.
	 * Null when the map should have the whole view area, as it always did: the
	 * colourful style, portrait, or the overlay stood down for the keyboard.
	 */
	public android.graphics.Rect mapArea()
	{
		// Caseless, the map has the whole screen again, as the colourful style gave it.
		if(!mTerm || RhTheme.caseless() || getVisibility() != VISIBLE || getWidth() == 0 || getHeight() == 0)
			return null;
		int side   = RhTheme.dpi(mContext, RhCase.glassSide(termBank()) + 2f);
		int top    = RhTheme.dpi(mContext, RhCase.glassTop() + RhScreen.MSG_BAND);
		int bottom = RhTheme.dpi(mContext, RhCase.glassBottom() + RhScreen.statusBand());
		if(getWidth() - 2 * side <= 0 || getHeight() - top - bottom <= 0)
			return null;
		return new android.graphics.Rect(side, top, getWidth() - side, getHeight() - bottom);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		mHost.mapAreaChanged();
		if(mTerm)
		{
			// The case is laid out against the screen it is on: rebuild to fit it.
			// Posted, because children cannot be re-added mid-layout.
			post(new Runnable()
			{
				@Override
				public void run()
				{
					updateFitLimit();
					rebuild();
				}
			});
		}
	}

	/**
	 * The terminal case has to fit the screen: both banks' full height, and a
	 * glass wide enough to play on.  On anything smaller than the design size
	 * (896 x 415 dp with 58dp keys) every key, the case and the screen text
	 * shrink together; on anything larger the keys stay put and the glass takes
	 * the room.  FIT_FLOOR stops the shrinking before keys fall well under the
	 * touch floor -- a screen that small overflows instead.
	 */
	private static final float FIT_FLOOR = 0.7f;
	/** The narrowest the hood may get, glass and its moulding together. */
	private static final float T_MIN_HOOD = 400f;

	private void updateFitLimit()
	{
		if(!RhTheme.terminal())
		{
			RhTheme.setFitLimit(Float.MAX_VALUE);
			return;
		}
		if(getWidth() == 0 || getHeight() == 0)
			return;
		float density = getResources().getDisplayMetrics().density;
		float wDp = getWidth() / density, hDp = getHeight() / density;
		float limit = Math.min(wDp / termNeedW(), hDp / termNeedH());
		RhTheme.setFitLimit(Math.max(FIT_FLOOR, limit));
	}

	/** The design height the tallest bank needs, at scale 1. */
	private float termNeedH()
	{
		float left = 2 * termInner() + T_ROW1_H + 2 * T_KEY + 3 * T_ROW_GAP + mPadBox;
		float right = 2 * termInner() + T_ROW1_H + 10f + T_EQ_BAR + 8f + 2 * T_EQ_CELL_H + T_GAP
				+ 8f + 2 * (T_SPARE_H + T_GAP) + T_SEARCH_H;
		return Math.max(left, right);
	}

	/** The design width both banks and the narrowest usable hood need, at scale 1. */
	private float termNeedW()
	{
		return 2 * (RhCase.MARGIN + termBank() + RhCase.MARGIN) + T_MIN_HOOD;
	}

	/**
	 * The left bank's top row hangs from the top and the pad and the row above it
	 * stand on the bottom, so a taller screen opens a gap between them -- 28dp on
	 * Lucas's phone with its status bar hidden.  The middle row takes the centre
	 * of it rather than leaving it all on one side.
	 */
	private void placeTermRow2()
	{
		if(!mTerm || getHeight() == 0)
			return;
		float hDp = getHeight() / RhTheme.dp(mContext, 1f);
		float row1Bottom = termInner() + T_ROW1_H;
		float row3Top = hDp - termRow3Bottom() - T_KEY;
		float top = Math.max(termRow2Top(), (row1Bottom + row3Top - T_KEY) / 2f);
		int px = RhTheme.dpi(mContext, top);
		for(View v : mTermRow2)
		{
			LayoutParams lp = (LayoutParams)v.getLayoutParams();
			if(lp != null && lp.topMargin != px)
			{
				lp.topMargin = px;
				v.setLayoutParams(lp);
			}
		}
	}

	/**
	 * GAME -> "Case on/off".  Posted, because the drawer row that asked is still
	 * dispatching its tap when the rebuild would remove it.
	 */
	private void toggleCase()
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putBoolean("rhCase", RhTheme.caseless()).commit();
		post(new Runnable()
		{
			@Override
			public void run()
			{
				RhTheme.loadPrefs(prefs);
				updateFitLimit();
				rebuild();
			}
		});
	}

	/**
	 * GAME -> "Status lines": full, compact, hidden, and round again.  The map
	 * re-centres in whatever glass the status leaves it.
	 */
	private void cycleStatusLines()
	{
		RhPrefs.saveStatusLines(PreferenceManager.getDefaultSharedPreferences(mContext),
				RhPrefs.statusLines().next());
		if(mScreen != null)
			mScreen.invalidate();
		mHost.mapAreaChanged();
	}

	/** The mode lamps on the hood's lip; see RhCase. */
	private void updateLamps()
	{
		if(mCase != null)
			mCase.setLamps(RhPrefs.searchMode(), mArmed != null, mMessageMore > 0);
	}

	/**
	 * Macros 2 and 3, in the two keys above Search (Lucas, 2026-09-24).  The
	 * canvas left them blank, as spare keys; macros are what he wanted there.
	 * The upper key is M2 and the lower M3, so the three read in order.
	 */
	private void buildRightMacros()
	{
		float colW = mPadBox - T_RIGHT_COL - T_GAP;
		float right = termInner() + T_RIGHT_COL + T_GAP;
		float bottom = termInner() + T_SEARCH_H + T_GAP;
		for(int i = 0; i < 2; i++)
		{
			int slot = 2 - i;
			if(slot >= RhPrefs.MACRO_SLOTS)
				continue;
			addView(buildMacroFace(slot), box(colW, T_SPARE_H, right, bottom + i * (T_SPARE_H + T_GAP), true));
		}
	}

	// Terminal geometry, derived.  Every value is design dp from the nearest edge.

	/** A well's inner edge, from the screen edge. */
	private float termInner()      { return RhCase.MARGIN + RhCase.WELL_PAD; }
	/** A bank's width: the numpad and its well. */
	private float termBank()       { return mPadBox + 2 * RhCase.WELL_PAD; }
	/** The wide keys that share a row with one square key: 126 beside a 190 pad. */
	private float termWideKey()    { return mPadBox - T_KEY - T_GAP; }
	private float termRow2Top()    { return termInner() + T_ROW1_H + T_ROW_GAP; }
	/** The bottom of the left bank's third row: the row just above the pad. */
	private float termRow3Bottom() { return padBottom() + mPadBox + T_ROW_GAP; }
	/** The deck's inner left edge; the same distance from the right is its inner right edge. */
	private float termDeckLeft()   { return RhCase.MARGIN + termBank() + RhCase.MARGIN + RhCase.WELL_PAD; }
	private float termDeckBottom() { return RhCase.MARGIN + (RhCase.DECK_H - RhCase.DECK_KEY) / 2f; }
	private float termEqTop()      { return termInner() + T_ROW1_H + 10f; }

	/** A box against the top-left corner. */
	private LayoutParams boxTL(float wDp, float hDp, float leftDp, float topDp)
	{
		LayoutParams lp = new LayoutParams(RhTheme.dpi(mContext, wDp), RhTheme.dpi(mContext, hDp));
		lp.gravity = Gravity.TOP | Gravity.LEFT;
		lp.leftMargin = RhTheme.dpi(mContext, leftDp);
		lp.topMargin = RhTheme.dpi(mContext, topDp);
		return lp;
	}

	// ____________________________________________________________________________________
	// Header and status.  Both are read-only, and both live along the top edge --
	// the one part of the screen a thumb never has to reach mid-fight.

	private void buildHeader()
	{
		mHeader = new RhHeader(mContext);
		LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
		                                   RhTheme.rawDpi(mContext, RhTheme.HEADER_HEIGHT));
		lp.gravity = Gravity.TOP;
		addView(mHeader, lp);
	}

	private void buildStatusPanel()
	{
		mStatusPanel = new RhStatusPanel(mContext);
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.TOP | Gravity.LEFT;
		lp.leftMargin = RhTheme.dpi(mContext, 10f);
		lp.topMargin  = RhTheme.dpi(mContext, 46f);
		addView(mStatusPanel, lp);
	}

	/**
	 * The message line and its history button, as one centred group.
	 *
	 * Both sit at a fixed size so the button never moves: the panel keeps its
	 * declared 420dp even with nothing to say, rather than shrinking to fit and
	 * dragging the control around with it.
	 */
	private void buildMessagePanel()
	{
		mMessagePanel = new RhMessagePanel(mContext);
		// A tap opens the message history, as MSGS does -- but only while some
		// of this turn's messages have scrolled out (RhMessagePanel.setMessage
		// makes it clickable then); otherwise taps fall through to the map.
		mMessagePanel.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				execute(RhCommands.PREV_MSGS, mMessagePanel);
			}
		});
		mMessagePanel.setClickable(false);
		LayoutParams lp = new LayoutParams(
				RhTheme.dpi(mContext, RhMessagePanel.WIDTH), LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
		lp.topMargin = RhTheme.dpi(mContext, 46f);
		addView(mMessagePanel, lp);
	}

	/**
	 * Message history, centred in the header band.
	 *
	 * It sat at the message panel's right edge until the KEYS button pushed the
	 * top-right cluster into it. The header's middle is the one piece of the top
	 * edge with nothing in it -- name and role sit left, attributes right -- and a
	 * control that shows you what already happened belongs in the identity band
	 * rather than among the commands.
	 *
	 * It wears VIOLET for the same reason: it is reference, not action.
	 *
	 * The 38dp height is under the 44dp touch floor, deliberately. The band is
	 * that tall and the face is 66dp wide to compensate; the handoff already
	 * carves out one header control at 55x23 on the same reasoning.
	 */
	private void buildMessageHistoryButton()
	{
		int h = RhTheme.rawDpi(mContext, RhTheme.HEADER_HEIGHT);

		/*
		 * Rest lives up here rather than in the context strip.  It commits twenty
		 * turns; catching it by accident mid-fight is the kind of mistake the
		 * layout is supposed to prevent, which is the same reason the handoff
		 * exiles PRAY to the far corner.  Out of thumb reach is the point.
		 */
		mRestFace = new RhFace(mContext).radius(4f).face(RhTheme.A90)
				.textColor(RhTheme.BADGE_TEXT);
		bindHold(mRestFace, CHIP_HOLD_MS,
			new Runnable() { @Override public void run() { openRestChips(RhCommands.CTX_REST); } },
			new Runnable()
			{
				@Override
				public void run()
				{
					closeChips();
					runAction(RhCommands.CTX_REST, mRestWell != null ? mRestWell : mRestFace);
				}
			});

		final RhFace prev = new RhFace(mContext)
				.face(RhTheme.VIOLET)
				.radius(4f)
				.label(labelFor(RhCommands.PREV_MSGS), 9f, 0.04f)
				.sub(subKeyFor(RhCommands.PREV_MSGS), 7.5f, RhTheme.RAW_KEY, 1f);
		bindTap(prev, new Runnable()
		{
			@Override
			public void run()
			{
				execute(RhCommands.PREV_MSGS, prev);
			}
		});

		if(mTerm)
		{
			// The terminal has no header: Rest and Msgs take the left bank's top row
			// and the macro the wide key under Msgs -- still the far end of the
			// bank from the thumb, which is the point of where they were.
			float in = termInner();
			mLongFace = new RhFace(mContext).radius(4f).face(RhTheme.A90)
					.textColor(RhTheme.BADGE_TEXT);
			bindHold(mLongFace, CHIP_HOLD_MS,
				new Runnable() { @Override public void run() { openRestChips(RhCommands.CTX_LONG_REST); } },
				new Runnable()
				{
					@Override
					public void run()
					{
						closeChips();
						runAction(RhCommands.CTX_LONG_REST, mRestWell);
						mRestWell.scrollTo(false);
					}
				});
			// Long rest waits past Rest's right edge, out of sight until the slot
			// is swiped toward the screen's edge -- gurrhack's scrolling panels,
			// which is how Lucas kept his s300 key (see RhScrollWell).
			mRestWell = new RhScrollWell(mContext, mRestFace, mLongFace,
					RhTheme.dpi(mContext, termWideKey()), RhTheme.dpi(mContext, T_GAP));
			addView(mRestWell, boxTL(termWideKey(), T_ROW1_H, in, in));
			addView(prev, boxTL(T_KEY, T_ROW1_H, in + mPadBox - T_KEY, in));
			// Macro 1 under Msgs; 2 and 3 are in the right bank (buildRightMacros).
			RhFace macro = buildMacroFace(0);
			addView(macro, boxTL(termWideKey(), T_KEY, in + T_KEY + T_GAP, termRow2Top()));
			mTermRow2.add(macro);
		}
		else
		{
			LinearLayout group = new LinearLayout(mContext);
			group.setOrientation(LinearLayout.HORIZONTAL);
			LayoutParams glp = new LayoutParams(LayoutParams.WRAP_CONTENT, h);
			glp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
			addView(group, glp);

			group.addView(mRestFace, new LinearLayout.LayoutParams(RhTheme.rawDpi(mContext, 78f), h));
			LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
					RhTheme.rawDpi(mContext, 66f), h);
			plp.leftMargin = RhTheme.rawDpi(mContext, 6f);
			group.addView(prev, plp);

			// The header has room for one macro; the terminal style carries all three.
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					RhTheme.rawDpi(mContext, 78f), h);
			lp.leftMargin = RhTheme.rawDpi(mContext, 6f);
			group.addView(buildMacroFace(0), lp);
		}

		mRestChips = buildCountRow(RhCommands.CTX_REST);
		if(mTerm)
			mLongChips = buildCountRow(RhCommands.CTX_LONG_REST);
		refreshRestFace();
	}

	// ____________________________________________________________________________________
	// Macros.  gurrhack's command panels let a button hold any key sequence, and
	// Lucas used that to walk a boulder across a level and back on one press.
	// The mobile interface dropped the panels and with them that ability, so it
	// comes back as a slot in the header: empty by default, hold to define, tap
	// to run.  The sequence goes through the same Cmd.KeySequnece the panels used,
	// which feeds the core one key at a time and waits for it between keys --
	// a bare string would race a prompt.
	//
	// It lives in the header, beside MSGS and REST, because a macro is by nature
	// something the player set up rather than something the game is offering, and
	// because it can commit many turns at once: like Rest, it belongs out of
	// thumb reach.

	/** By slot; a style that shows fewer slots leaves the rest null. */
	private final RhFace[] mMacroFaces = new RhFace[RhPrefs.MACRO_SLOTS];

	/** This turn's contextual actions, best first; the strip's middle slot shows or fans them. */
	private final List<RhCommands.ContextAction> mCtxCandidates = new ArrayList<RhCommands.ContextAction>();
	private String mCandSig = "";
	private FrameLayout mCandRadial;
	private boolean mCandOpen;
	private RhBadges mBadges;
	private WedgeView mWedges;

	/** One macro face, wired and labelled; the caller places it. */
	private RhFace buildMacroFace(final int slot)
	{
		final RhFace f = new RhFace(mContext).radius(4f).face(RhTheme.G90);
		mMacroFaces[slot] = f;
		if(mTerm)
			f.tag("M" + (slot + 1));

		bindHold(f, HUB_HOLD_MS,
			new Runnable() { @Override public void run() { editMacro(slot); } },
			new Runnable()
			{
				@Override
				public void run()
				{
					if(!RhPrefs.macroSet(slot))
					{
						// An empty slot is the discoverable way in, as with a pin point.
						editMacro(slot);
						return;
					}
					closeChips();
					flashRaw(macroLabel(slot), f);
					mHost.sendCommand(RhPrefs.macroKeys(slot));
				}
			});

		refreshMacroFace(slot);
		return f;
	}

	private String macroLabel(int slot)
	{
		String name = RhPrefs.macroName(slot);
		return name.length() > 0 ? name : RhPrefs.macroKeys(slot);
	}

	private void refreshMacroFace(int slot)
	{
		RhFace f = mMacroFaces[slot];
		if(f == null)
			return;
		if(RhPrefs.macroSet(slot))
			f.placeholder(false)
			 .textColor(RhTheme.TEXT)
			 .label(macroLabel(slot), 8.5f, 0.03f)
			 .sub("hold to edit", 7f, RhTheme.TEXT, 0.75f);
		else
			f.placeholder(true)
			 .textColor(RhTheme.TEXT)
			 .label("+", 15f, 0f)
			 .sub("macro", 7f, RhTheme.TEXT, 0.75f);
	}

	/**
	 * The editor: a name and the key sequence, as two text fields.  This is the
	 * one place the interface asks for typed text, and it uses the system
	 * keyboard rather than ForkFront's because the sequence is written in
	 * gurrhack's escape notation (^D, M-x, \e) -- plain characters, no Ctrl or
	 * Meta key needed to enter it.
	 */
	private void editMacro(final int slot)
	{
		closeChips();
		closeFan();
		closeRadial();
		closeDrawer();
		closeContextRadial();

		final android.widget.EditText name = new android.widget.EditText(mContext);
		name.setHint("Name (fits ~8 characters)");
		name.setSingleLine();
		name.setText(RhPrefs.macroName(slot));

		final android.widget.EditText keys = new android.widget.EditText(mContext);
		keys.setHint("Keys, e.g. 20s  or  ^Dh  or  M-p");
		keys.setSingleLine();
		keys.setTypeface(android.graphics.Typeface.MONOSPACE);
		keys.setText(RhPrefs.macroKeys(slot));

		android.widget.TextView help = new android.widget.TextView(mContext);
		help.setText("Sent one key at a time, as typed.  ^X = Ctrl-X,  M-x = Meta-x,  "
		           + "\\e = Escape,  \\n = Enter,  \\b = backspace.");
		help.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);

		LinearLayout body = new LinearLayout(mContext);
		body.setOrientation(LinearLayout.VERTICAL);
		int pad = RhTheme.rawDpi(mContext, 16f);
		body.setPadding(pad, pad / 2, pad, 0);
		body.addView(name);
		body.addView(keys);
		body.addView(help);

		final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(mContext)
				.setTitle(RhPrefs.macroSet(slot) ? "Edit macro" : "New macro")
				.setView(body)
				.setPositiveButton("Save", new android.content.DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(android.content.DialogInterface d, int which)
					{
						RhPrefs.saveMacro(PreferenceManager.getDefaultSharedPreferences(mContext),
						                  slot, name.getText().toString(), keys.getText().toString());
						refreshMacroFace(slot);
					}
				})
				.setNegativeButton("Cancel", null)
				.create();

		if(RhPrefs.macroSet(slot))
			dlg.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Clear",
				new android.content.DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(android.content.DialogInterface d, int which)
					{
						RhPrefs.saveMacro(PreferenceManager.getDefaultSharedPreferences(mContext),
						                  slot, "", "");
						refreshMacroFace(slot);
					}
				});

		dlg.show();
	}

	/**
	 * Rest's chips open below the header rather than above the face, since there
	 * is nothing above it.  Centred is safe here -- the warning about centring in
	 * the handoff was about the strip's row reaching back over the numpad, and
	 * there is no numpad at the top of the screen.  The terminal opens them under
	 * the Rest slot instead, and Long rest's row (100 to 400) opens in the same
	 * place.
	 */
	private ViewGroup buildCountRow(final RhCommands.ContextAction act)
	{
		LinearLayout row = new LinearLayout(mContext);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setVisibility(GONE);

		// The row opens across the message line, and the message reads through the
		// gaps between chips.  A backing in the info-panel colour makes it a
		// popover rather than a set of floating tiles over live text.
		int inset = RhTheme.dpi(mContext, 4f);
		row.setBackgroundColor(RhTheme.POPOVER_BG);
		row.setPadding(inset, inset, inset, inset);

		LayoutParams lp = new LayoutParams(RhTheme.dpi(mContext, CHIP_ROW_W) + 2 * inset,
		                                   RhTheme.dpi(mContext, CHIP_SIZE) + 2 * inset);
		if(mTerm)
		{
			// Under the Rest slot, over the bank's second row while it is open.
			lp.gravity = Gravity.TOP | Gravity.LEFT;
			lp.leftMargin = RhTheme.dpi(mContext, termInner());
			lp.topMargin = RhTheme.dpi(mContext, termInner() + T_ROW1_H + T_GAP);
		}
		else
		{
			lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
			lp.topMargin = RhTheme.rawDpi(mContext, RhTheme.HEADER_HEIGHT + 4f);
		}
		addView(row, lp);

		for(int c = 0; c < act.counts.length; c++)
		{
			final int value = act.counts[c];
			RhFace chip = new RhFace(mContext)
					.face(RhTheme.A90)
					.radius(4f)
					.textColor(RhTheme.BADGE_TEXT)
					.label("×" + value, 11f, 0f);
			LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
					RhTheme.dpi(mContext, CHIP_SIZE), RhTheme.dpi(mContext, CHIP_SIZE));
			if(c > 0)
				lp2.leftMargin = RhTheme.dpi(mContext, CHIP_GAP);
			row.addView(chip, lp2);

			bindTap(chip, new Runnable()
			{
				@Override
				public void run()
				{
					RhPrefs.saveCount(PreferenceManager.getDefaultSharedPreferences(mContext),
					                  act.countKey, value);
					closeChips();
				}
			});
		}
		addCustomChip(row, new Runnable()
		{
			@Override
			public void run()
			{
				promptCount(act);
			}
		});
		return row;
	}

	/** The largest count NetHack takes: LARGEST_INT, include/global.h. */
	private static final int MAX_COUNT = 32767;

	/**
	 * The last chip on every count row, "n": any count at all (Lucas, 2026-09-24),
	 * after NetHack's own n prefix.  Shutting yourself in a closet to rest a
	 * thousand turns is a plan the presets should not rule out.
	 */
	private void addCustomChip(LinearLayout row, Runnable onTap)
	{
		RhFace chip = new RhFace(mContext)
				.face(RhTheme.A90)
				.radius(4f)
				.textColor(RhTheme.BADGE_TEXT)
				.label("×n", 11f, 0f);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				RhTheme.dpi(mContext, CHIP_SIZE), RhTheme.dpi(mContext, CHIP_SIZE));
		lp.leftMargin = RhTheme.dpi(mContext, CHIP_GAP);
		row.addView(chip, lp);
		bindTap(chip, onTap);
	}

	/** Asks for the count on the system's number pad.  It sticks, like a preset. */
	private void promptCount(final RhCommands.ContextAction act)
	{
		final android.widget.EditText field = new android.widget.EditText(mContext);
		field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		field.setSingleLine();
		field.setHint("1 to " + MAX_COUNT);
		field.setText(Integer.toString(countFor(act)));
		field.setSelectAllOnFocus(true);

		LinearLayout body = new LinearLayout(mContext);
		int pad = RhTheme.rawDpi(mContext, 16f);
		body.setPadding(pad, pad / 2, pad, 0);
		body.addView(field, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(mContext)
				.setTitle(act.word + ": how many turns?")
				.setView(body)
				.setPositiveButton("Set", new android.content.DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(android.content.DialogInterface d, int which)
					{
						long n;
						try
						{
							n = Long.parseLong(field.getText().toString().trim());
						}
						catch(NumberFormatException e)
						{
							return;
						}
						if(n < 1)
							return;
						RhPrefs.saveCount(PreferenceManager.getDefaultSharedPreferences(mContext),
								act.countKey, (int)Math.min(n, MAX_COUNT));
						closeChips();
					}
				})
				.setNegativeButton("Cancel", null)
				.create();
		dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		dlg.show();
	}

	/** Full strength on the chip that matches; on "n" when no preset does. */
	private static void highlightCounts(ViewGroup chips, int[] counts, int n)
	{
		boolean preset = false;
		for(int c = 0; c < counts.length; c++)
			preset |= counts[c] == n;
		for(int c = 0; c < chips.getChildCount(); c++)
		{
			boolean on = c < counts.length ? counts[c] == n : !preset;
			chips.getChildAt(c).setAlpha(on ? 1f : 0.66f);
		}
	}

	private void openRestChips(RhCommands.ContextAction act)
	{
		closeFan();
		closeRadial();
		closeDrawer();
		closeContextRadial();
		mChipsOpen = act.countKey;
		refreshContextStrip();
	}

	private void refreshRestFace()
	{
		refreshCountFace(mRestFace, mRestChips, RhCommands.CTX_REST);
		refreshCountFace(mLongFace, mLongChips, RhCommands.CTX_LONG_REST);
		// Long rest stays up while its own chips are open.
		if(mRestWell != null)
			mRestWell.setPinned(RhCommands.CTX_LONG_REST.countKey.equals(mChipsOpen));
	}

	private void refreshCountFace(RhFace face, ViewGroup chips, RhCommands.ContextAction act)
	{
		if(face == null || chips == null)
			return;

		int n = countFor(act);
		boolean open = act.countKey.equals(mChipsOpen);

		face.label(RhPrefs.labelMode() == RhPrefs.LabelMode.KEYS
						? act.keyWithCount(n)
						: act.wordWithCount(n),
				8.5f, 0.03f)
		    .sub(open ? "pick a count" : "hold to set", 7f, RhTheme.BADGE_TEXT, 0.75f);

		chips.setVisibility(open ? VISIBLE : GONE);
		if(open)
			highlightCounts(chips, act.counts, n);
	}

	/** Verbatim NetHack message output, pushed whenever the core prints. */
	public void setMessage(String message)
	{
		setMessage(message, mMessageMore);
	}

	/**
	 * The same, plus how many of this turn's messages fell out of the lines
	 * shown -- ForkFront's "--N more--", which the panel now carries itself.
	 */
	public void setMessage(String message, int more)
	{
		mMessageText = message;
		mMessageMore = more;
		if(mMessagePanel != null)
			mMessagePanel.setMessage(message, more);
		if(mScreen != null)
			mScreen.setMessage(message, more);
		updateLamps();
	}

	/**
	 * Whether this overlay, not ForkFront's classic panels, is the control
	 * surface: switched on, and landscape.  Suppression for the soft keyboard is
	 * not part of it -- NH_State sets that itself, from this answer.
	 */
	public boolean ownsControls()
	{
		return RhPrefs.enabled() && !mPortrait;
	}

	/** Called when the core finishes a status pass, so the fields are consistent. */
	public void statusUpdated(RhStatus status)
	{
		mStatus = status;
		if(mHeader != null)
			mHeader.setStatus(status);
		if(mStatusPanel != null)
			mStatusPanel.setStatus(status);
		if(mBadges != null)
			mBadges.setStatus(status);
		if(mScreen != null)
			mScreen.setStatus(status);
		recomputeContext();
	}

	// ____________________________________________________________________________________
	// The context strip, rewritten every turn from what the core reports.

	/**
	 * Three fixed slots: Look, the turn's contextual action, Search.
	 *
	 * Look and Search never move -- a control that changes position with game
	 * state is what the spatial-memory work says not to build (Scarr, Cockburn &
	 * Gutwin 2013).  Search sits on the right, under the thumb that is not
	 * walking, so a step and a search alternate between thumbs (Lucas, 2026-09-23).
	 *
	 * The middle slot shows the one action the turn offers; when several apply --
	 * stairs with a chest on them, an altar by a door -- it names them and fans
	 * them out on a tap.  Attack is gone from it: fighting is walking into things,
	 * or F, f, t, z and a direction, and "Attack" on a staircase was hiding the
	 * stairs (Lucas, 2026-09-23).  Pick up stays off the strip; the pad's centre
	 * cell takes it.
	 */
	private void recomputeContext()
	{
		if(mStatus == null)
			return;

		mCtxCandidates.clear();
		if(mStatus.here(RhStatus.HERE_STAIRS_DOWN))
			mCtxCandidates.add(RhCommands.CTX_DESCEND);
		if(mStatus.here(RhStatus.HERE_STAIRS_UP))
			mCtxCandidates.add(RhCommands.CTX_ASCEND);
		if(mStatus.here(RhStatus.HERE_ALTAR))
			mCtxCandidates.add(RhCommands.CTX_SACRIFICE);
		if(mStatus.here(RhStatus.HERE_CONTAINER))
			mCtxCandidates.add(RhCommands.CTX_LOOT);
		if(mStatus.here(RhStatus.ADJ_CLOSED_DOOR))
			mCtxCandidates.add(RhCommands.CTX_OPEN);

		StringBuilder sig = new StringBuilder();
		for(RhCommands.ContextAction a : mCtxCandidates)
			sig.append(a.id).append(' ');
		if(mCandOpen && !sig.toString().equals(mCandSig))
			closeCandidates();
		mCandSig = sig.toString();

		RhCommands.ContextAction top = mCtxCandidates.isEmpty() ? null : mCtxCandidates.get(0);
		setContextActions(new RhCommands.ContextAction[] {
				RhCommands.CTX_LOOK, top, RhCommands.CTX_SEARCH });
		refreshPadCentre();
	}

	/**
	 * The pad's centre cell takes Pick up whenever something is underfoot.
	 *
	 * Pick up is high-frequency, and its only other home is the context strip at
	 * x 396 -- a full screen crossing from a thumb resting at x 12.  The centre
	 * cell is the one uncommitted target in that corner.  Search keeps the cell on
	 * every other turn, and stays on the strip and in the radial regardless.
	 */
	private void refreshPadCentre()
	{
		if(mPadCentre == null)
			return;

		if(directionPending())
		{
			// While a direction is wanted the centre cell is the ninth direction:
			// your own square.  Applying a key or a lock pick to a chest asks "In
			// what direction?" and only accepts '.' for the square you stand on,
			// so without this there is no way to open one.
			mPadCentre.label("HERE", 8f, 0.04f).sub(".", 7f, RhTheme.RAW_KEY, 1f);
			return;
		}

		boolean pickup = mStatus != null && mStatus.here(RhStatus.HERE_OBJECT);
		if(mTerm)
		{
			// A keycap says what a hold does on its front, so HOLD leaves the face.
			if(pickup)
				mPadCentre.label("PICK UP", 8f, 0.04f).sub(",", 7f, RhTheme.RAW_KEY, 1f);
			else
				mPadCentre.label("REST", 9f, 0.04f).sub("hold · context", 7f, RhTheme.TEXT, 0.75f);
			return;
		}
		if(pickup)
			mPadCentre.label("PICK\nUP", 8f, 0.04f).sub(",", 7f, RhTheme.RAW_KEY, 1f);
		else
			mPadCentre.label("REST\nHOLD", 8f, 0.04f).sub(null, 7f, RhTheme.RAW_KEY, 1f);
	}

	/** True while either our armed mode or the core itself is waiting for a direction. */
	private boolean directionPending()
	{
		return mArmed != null || mExpectsDirection;
	}

	/**
	 * The core has asked for a direction.  The numpad answers it -- including the
	 * centre cell for "here" -- so the classic directional overlay stays down.
	 */
	public void setExpectsDirection(boolean expects)
	{
		if(mExpectsDirection == expects)
			return;
		mExpectsDirection = expects;
		if(expects)
			closeContextRadial();
		refreshPadCentre();
	}

	/** What the centre cell sends on a tap, which depends on what is underfoot. */
	private RhCommands.Item padCentreCommand()
	{
		return (mStatus != null && mStatus.here(RhStatus.HERE_OBJECT))
				? RhCommands.PICKUP : RhCommands.SEARCH;
	}

	// ____________________________________________________________________________________
	// The movement numpad.  A 3x3 grid of squares tucked fully into the bottom-left
	// corner, where the left thumb already rests -- a numpad's proportions, which
	// is what NetHack's movement keys are.  Nine targets fit in 146dp where the old
	// ring needed 172dp, and these are the most-pressed keys in the game, so this
	// is the corner of the screen that matters most.
	//
	// Semi-transparent so the map reads through; snaps to full opacity when armed.

	private void buildNumpad()
	{
		float cellPitch = mPadCell + PAD_GAP;

		// The mold.  It owns the pad's whole footprint and swallows anything that
		// lands in a gutter, so a press between two cells does nothing instead of
		// falling through to the map and firing travel.
		mPadMold = new FrameLayout(mContext);
		mPadMold.setClickable(true);
		addView(mPadMold, boxLB(mPadBox, mPadBox, padLeft(), padBottom()));

		for(int row = 0; row < 3; row++)
		{
			for(int col = 0; col < 3; col++)
			{
				int idx = row * 3 + col;
				final char key = RhCommands.PAD_KEYS[idx];

				// Grid origin is the pad's top-left; anchors here are measured from
				// the bottom, so the row index counts down from the top row.
				float left = col * cellPitch;
				float bottom = (2 - row) * cellPitch;

				if(key == 0)
				{
					mPadCentre = new RhFace(mContext)
							.radius(3f)
							.face(RhTheme.G90)
							.label("REST\nHOLD", 8f, 0.04f)
							.leading(1.1f);
					if(mTerm)
						mPadCentre.cap(RhTheme.role(RhTheme.ROLE_MOVE));
					mPadMold.addView(mPadCentre, boxLB(mPadCell, mPadCell, left, bottom));
					bindHold(mPadCentre, CENTRE_HOLD_MS,
						new Runnable() { @Override public void run() { openContextRadial(); } },
						new Runnable()
					{
						@Override
						public void run()
						{
							// The ninth direction, when one is wanted.
							if(directionPending())
								pressDirection('.');
							else
								execute(padCentreCommand(), mPadCentre);
						}
					});
					continue;
				}

				RhFace cell = new RhFace(mContext)
						.radius(3f)
						.face(RhTheme.G90)
						.uppercase(false)
						.label(RhCommands.PAD_ARROW[idx], 18f, 0f);
				if(mTerm)
					cell.cap(RhTheme.role(RhTheme.ROLE_MOVE)).sub(String.valueOf(key), 7f, RhTheme.RAW_KEY, 1f);
				mPadMold.addView(cell, boxLB(mPadCell, mPadCell, left, bottom));
				bindTap(cell, new Runnable()
				{
					@Override
					public void run()
					{
						pressDirection(key);
					}
				});
				mPadCells.add(cell);
			}
		}

		repaintPad();
		setPadAlpha(padIdleAlpha());
		refreshPadCentre();
	}

	/**
	 * The pad's anchor.  The colourful pad is semi-transparent so the map reads
	 * through it; a keycap sits in its well with nothing behind it to read.
	 */
	private float padLeft()      { return mTerm ? termInner() : PAD_LEFT; }
	private float padBottom()    { return mTerm ? termInner() : PAD_BOTTOM; }
	private float padIdleAlpha() { return mTerm ? 1f : PAD_IDLE_ALPHA; }

	private void setPadAlpha(float alpha)
	{
		for(RhFace c : mPadCells)
			c.setAlpha(alpha);
		if(mPadCentre != null)
			mPadCentre.setAlpha(alpha);
	}

	/** Centre of the numpad, in design dp from the left and bottom edges. */
	private float padCentreX() { return padLeft() + mPadBox / 2f; }
	private float padCentreY() { return padBottom() + mPadBox / 2f; }

	/**
	 * A hub's centre, with the two that key off the pad derived rather than read.
	 *
	 * OFFENSE keeps the handoff's relationship -- PAD_AIR clear of the pad's right
	 * edge -- at any key size.  DROP stays where the handoff put it (cy 210,
	 * stacked under PRAY/SACRIFICE) until the pad grows enough to reach it, and
	 * then rides PAD_AIR above the pad's top edge instead; at 58dp keys that is
	 * cy 230, which leaves 5dp under SACRIFICE on the 443dp-tall moto g.  Every
	 * fan, radial and satellite position goes through these two, so a hub and
	 * everything hung off it move together.
	 */
	private float hubCx(RhCommands.Hub hub)
	{
		if(mTerm)
		{
			// OFFENSE opens the deck, beside the pad's well; DROP is the wide key
			// above the pad; INTERACT and EAT/QUAFF/READ share the right bank's
			// inner column.
			if(hub == RhCommands.HUB_ATTACK)
				return termDeckLeft() + hubW(hub) / 2f;
			if(hub == RhCommands.HUB_DROP)
				return termInner() + termWideKey() / 2f;
			if(hub == RhCommands.HUB_INTERACT || hub == RhCommands.HUB_CONSUME)
				return -(termInner() + T_RIGHT_COL / 2f);
			return hub.cx;
		}
		if(hub == RhCommands.HUB_ATTACK)
			return PAD_LEFT + mPadBox + PAD_AIR + hubW(hub) / 2f;
		return hub.cx;
	}

	/**
	 * OFFENSE is sized to a numpad cell, so it follows the Movement key size
	 * setting: the hub beside the pad should be as easy to hit as the pad.
	 */
	private float hubW(RhCommands.Hub hub)
	{
		if(mTerm)
		{
			if(hub == RhCommands.HUB_ATTACK)
				return mPadCell;
			if(hub == RhCommands.HUB_DROP)
				return termWideKey();
			if(hub == RhCommands.HUB_INTERACT || hub == RhCommands.HUB_CONSUME)
				return T_RIGHT_COL;
			return hub.w;
		}
		return hub == RhCommands.HUB_ATTACK ? mPadCell : hub.w;
	}

	private float hubH(RhCommands.Hub hub)
	{
		if(mTerm)
		{
			if(hub == RhCommands.HUB_ATTACK)
				return RhCase.DECK_KEY;
			if(hub == RhCommands.HUB_DROP)
				return T_KEY;
			if(hub == RhCommands.HUB_INTERACT)
				return T_INTERACT_H;
			if(hub == RhCommands.HUB_CONSUME)
				return T_CONSUME_H;
			return hub.h;
		}
		return hub == RhCommands.HUB_ATTACK ? mPadCell : hub.h;
	}

	private float hubCy(RhCommands.Hub hub)
	{
		if(mTerm)
		{
			if(hub == RhCommands.HUB_ATTACK)
				return termDeckBottom() + RhCase.DECK_KEY / 2f;
			if(hub == RhCommands.HUB_DROP)
				return termRow3Bottom() + T_KEY / 2f;
			if(hub == RhCommands.HUB_INTERACT)
				return termInner() + T_INTERACT_H / 2f;
			if(hub == RhCommands.HUB_CONSUME)
				return termInner() + T_INTERACT_H + T_GAP + T_CONSUME_H / 2f;
			return hub.cyFromBottom;
		}
		if(hub == RhCommands.HUB_DROP)
			return Math.max(hub.cyFromBottom, PAD_BOTTOM + mPadBox + PAD_AIR + hub.h / 2f);
		return hub.cyFromBottom;
	}

	/**
	 * The context radial's radius: enough to clear the pad at the worst of its
	 * bearings, plus a node's half-diameter, plus CTX_RADIAL_AIR.
	 *
	 * The pad is a square, so along a bearing a from its centre the boundary is
	 * half / max(|cos a|, |sin a|) -- furthest on the diagonal.  Of the five
	 * bearings -90/-64/-38/-12/+14, the -38 node is the worst at 1.27 x half:
	 * 97.7dp on a 154dp pad (the old fixed 132 gave it 11dp of air, which is
	 * where CTX_RADIAL_AIR comes from) and 120.6dp on a 190dp pad, for a radius
	 * of 155.  The +14 node overlaps OFFENSE at every size; OFFENSE is dimmed and
	 * disabled while the radial is open, so that is visual only.
	 */
	private float ctxRadialRadius()
	{
		float half = mPadBox / 2f;
		float worst = 0f;
		for(int i = 0; i < RhCommands.CTX_RADIAL.length; i++)
		{
			double a = Math.toRadians(CTX_RADIAL_A0 + i * CTX_RADIAL_STEP);
			float along = half / (float)Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
			worst = Math.max(worst, along);
		}
		return worst + CTX_RADIAL_SIZE / 2f + CTX_RADIAL_AIR;
	}

	/**
	 * A direction press either walks, or completes an armed command.  Arming means
	 * one tap plus one direction with both thumbs in place -- kicking a door open
	 * is two presses in the same corner.
	 */
	private void pressDirection(char dir)
	{
		if(mArmed != null)
		{
			RhCommands.Item cmd = mArmed;
			disarm();
			mHost.sendCommand(cmd.key + dir);
			return;
		}
		// Search mode pads each step with searches, before or after it (Lucas,
		// 2026-09-23 -- his lower-Mines habit, made a mode).  Never while the core
		// has asked for a direction: then the key is an answer, not a step.
		if(RhPrefs.searchMode() && !mExpectsDirection)
		{
			int n = RhPrefs.searchCount();
			String s = n > 1 ? n + "s" : "s";
			mHost.sendCommand(RhPrefs.searchBefore() ? s + dir : dir + s);
			return;
		}
		mHost.sendCommand(String.valueOf(dir));
	}

	/**
	 * The pad's resting colour: amber while search mode is on, as a standing
	 * reminder that every step now costs extra turns.  Armed red overrides it.
	 */
	private void repaintPad()
	{
		// Keycaps do not change colour; the terminal lights a lamp instead.
		if(mTerm)
		{
			updateLamps();
			return;
		}
		if(mArmed != null)
			return;
		int[] face = RhPrefs.searchMode() ? RhTheme.A90 : RhTheme.G90;
		for(RhFace c : mPadCells)
			c.face(face);
	}

	/**
	 * WORLD's "Search mode" entry.  Off: switches it off.  On: asks the two
	 * questions Lucas specified -- before or after the step, and how many.
	 */
	private void toggleSearchMode()
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if(RhPrefs.searchMode())
		{
			RhPrefs.saveSearchMode(prefs, false, RhPrefs.searchBefore(), RhPrefs.searchCount());
			repaintPad();
			flashRaw("search off", mPadCentre);
			return;
		}

		int pad = RhTheme.rawDpi(mContext, 18f);
		LinearLayout body = new LinearLayout(mContext);
		body.setOrientation(LinearLayout.VERTICAL);
		body.setPadding(pad, pad / 2, pad, 0);

		android.widget.TextView q1 = new android.widget.TextView(mContext);
		q1.setText("Search before or after each step?");
		body.addView(q1);
		final android.widget.RadioGroup when = new android.widget.RadioGroup(mContext);
		final android.widget.RadioButton before = new android.widget.RadioButton(mContext);
		before.setText("Before \u2014 search, then move");
		before.setId(101);
		android.widget.RadioButton after = new android.widget.RadioButton(mContext);
		after.setText("After \u2014 move, then search");
		after.setId(102);
		when.addView(before);
		when.addView(after);
		when.check(RhPrefs.searchBefore() ? 101 : 102);
		body.addView(when);

		android.widget.TextView q2 = new android.widget.TextView(mContext);
		q2.setText("How many searches with each step?");
		q2.setPadding(0, pad / 2, 0, 0);
		body.addView(q2);
		final int[] counts = { 1, 2, 3, 5 };
		final android.widget.RadioGroup many = new android.widget.RadioGroup(mContext);
		many.setOrientation(android.widget.RadioGroup.HORIZONTAL);
		for(int k = 0; k < counts.length; k++)
		{
			android.widget.RadioButton rb = new android.widget.RadioButton(mContext);
			rb.setText("\u00d7" + counts[k]);
			rb.setId(201 + k);
			many.addView(rb);
		}
		int cur = 0;
		for(int k = 0; k < counts.length; k++)
			if(counts[k] == RhPrefs.searchCount())
				cur = k;
		many.check(201 + cur);
		body.addView(many);

		android.widget.TextView note = new android.widget.TextView(mContext);
		note.setText("Each search is a turn.  The movement keys turn amber while this is on; "
		           + "switch it off from the same WORLD entry.");
		note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
		note.setPadding(0, pad / 2, 0, 0);
		body.addView(note);

		new android.app.AlertDialog.Builder(mContext)
				.setTitle("Search mode")
				.setView(body)
				.setPositiveButton("Turn on", new android.content.DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(android.content.DialogInterface d, int which)
					{
						int n = counts[Math.max(0, Math.min(counts.length - 1,
								many.getCheckedRadioButtonId() - 201))];
						boolean b = when.getCheckedRadioButtonId() == 101;
						RhPrefs.saveSearchMode(prefs, true, b, n);
						repaintPad();
						flashRaw((b ? "search " : "step then ") + (n > 1 ? n + "s" : "s"), mPadCentre);
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ____________________________________________________________________________________
	// Hubs.  Tap runs the quick command; hold 380ms fans four faces; tapping the
	// hub again with the fan open closes it and opens the whole group's drawer.
	// Three levels of depth from one 76dp target under the thumb.

	private static final class HubView
	{
		RhCommands.Hub hub;
		RhFace face;
		ViewGroup fan;
		/** Star points or triangle satellites that belong to this hub, if any. */
		ViewGroup satellites;
		/**
		 * The slot faces, created once and re-skinned in place.  Rebuilding them on
		 * every change left a stale face behind on screen, and would have left every
		 * touch handler holding the item it was built with rather than the one now
		 * in the slot.
		 */
		final List<RhFace> slotFaces = new ArrayList<RhFace>();
		/** The fan's nodes, by index; re-skinned in place like the slots. */
		final List<RhFace> fanFaces = new ArrayList<RhFace>();
	}

	private void buildHubs()
	{
		for(RhCommands.Hub hub : RhCommands.HUBS)
			addHub(hub);
	}

	private void addHub(final RhCommands.Hub hub)
	{
		final HubView hv = new HubView();
		hv.hub = hub;

		// The fan sits below its hub in draw order, matching the prototype.
		hv.fan = new FrameLayout(mContext);
		hv.fan.setVisibility(GONE);
		addView(hv.fan, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		for(int i = 0; i < hub.fan.length && i < FAN_SIZE.length; i++)
		{
			final int index = i;
			double a = Math.toRadians(hub.fanA0 + i * hub.fanStep);
			float sx = offsetX(hubCx(hub), (float)Math.cos(a) * hub.fanRadius);
			float sy = hubCy(hub) - (float)Math.sin(a) * hub.fanRadius;

			final RhFace slot = new RhFace(mContext)
					.shape(FAN_RADIUS_CORNER[i] < 0 ? RhFace.Shape.CIRCLE : RhFace.Shape.RECT)
					.radius(FAN_RADIUS_CORNER[i] < 0 ? RhTheme.FACE_RADIUS : FAN_RADIUS_CORNER[i]);
			slot.setRotation(FAN_ROTATE[i]);
			hv.fan.addView(slot, centredLB(FAN_SIZE[i], FAN_SIZE[i], sx, sy));
			hv.fanFaces.add(slot);

			// Both handlers read the node's command at press time: a fan is
			// assignable now (Lucas, 2026-09-24), so what a node holds can change.
			bindHold(slot, HUB_HOLD_MS,
				new Runnable()
				{
					@Override
					public void run()
					{
						// A hold sends the command's fuller form -- Engrave's menu of
						// things to write with, rather than starting the engraving.
						RhCommands.Item item = fanItem(hub, index);
						if(mAssign != null || item == null || !item.hasAlt())
							return;
						closeFan();
						flashRaw(item.altKey, slot);
						mHost.sendCommand(item.altKey);
					}
				},
				new Runnable()
				{
					@Override
					public void run()
					{
						if(assignAcceptsFan(hub))
						{
							placeFan(hv, index);
							return;
						}
						RhCommands.Item item = fanItem(hub, index);
						closeFan();
						if(item == null)
						{
							// An emptied node is the way into the drawer to refill it.
							openDrawer(hub.group.id, hub);
							return;
						}
						fireFromHub(hub, item, slot);
					}
				});
		}
		refreshFan(hv);

		if(hub == RhCommands.HUB_ATTACK)
		{
			// OFFENSE's radial sits on the same arc as its pinnable points, so a
			// command promoted from the radial lands where it was shown.
			buildRadial(hub, RhCommands.OFFENSE_RADIAL,
			            RhCommands.ATK_SLOT_BEARING,
			            new float[] { RhCommands.ATK_SLOT_RADIUS });
			buildAttackStarPoints(hv);
			// The flick's wedges, drawn behind the radial's nodes, so the gesture can
			// be seen as well as felt (Lucas, 2026-09-23).
			ViewGroup offRadial = mRadials.get(hub.id);
			if(offRadial != null)
			{
				float[][] w = flickWedges(RhCommands.ATK_SLOT_BEARING,
						Math.min(RhCommands.OFFENSE_RADIAL.length, RhCommands.ATK_SLOT_BEARING.length));
				mWedges = new WedgeView(mContext, w[0], w[1]);
				offRadial.addView(mWedges, 0,
						new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
			}
		}
		if(hub == RhCommands.HUB_EQUIP)
			buildEquipMatrix(hv);

		hv.face = new RhFace(mContext)
				.face(hub.face)
				.label(hub.label, hub.labelSize, hub.labelTracking)
				.sub(hubIdleSub(hub), 8f, RhTheme.TEXT, 0.75f);

		if(mTerm)
		{
			// Every hub is a keycap: a keyboard tells its keys apart by size and
			// place, so the silhouettes stand down.  INTERACT is the right thumb's
			// home key; it and EAT/QUAFF/READ take their colours from the skin.
			hv.face.radius(4f);
			if(hub == RhCommands.HUB_INTERACT)
				hv.face.cap(RhTheme.role(RhTheme.ROLE_INTERACT));
			else if(hub == RhCommands.HUB_CONSUME)
				hv.face.cap(RhTheme.role(RhTheme.ROLE_CONSUME));
			else if(hub == RhCommands.HUB_EQUIP)
				hv.face.defaultCap(RhTheme.role(RhTheme.ROLE_INVENTORY));
		}
		else
		{
			if(hub.poly != null)
				hv.face.poly(hub.poly, hub.insL, hub.insT, hub.insR, hub.insB);
			else if(hub.radius < 0)
				hv.face.shape(RhFace.Shape.CIRCLE);
			else
				hv.face.radius(hub.radius);

			if(hub.labelPadBottom > 0f)
				hv.face.labelPadBottom(hub.labelPadBottom);
			if(hub.labelPadRight > 0f)
				hv.face.labelPadRight(hub.labelPadRight);
		}

		if(hub == RhCommands.HUB_EQUIP)
		{
			// The inventory bar: the matrix's full width, half a cell tall, on top.
			hv.face.sub(hubIdleSub(hub), hubSubSize(hub), RhTheme.TEXT, 0.75f);
			addView(hv.face, mTerm ? boxTR(mPadBox, T_EQ_BAR, termInner(), termEqTop())
			                       : boxTR(EQ_W, EQ_BAR, EQ_RIGHT, EQ_TOP));
		}
		else
			addView(hv.face, centredLB(hubW(hub), hubH(hub), hubCx(hub), hubCy(hub)));

		if(hub == RhCommands.HUB_ATTACK)
		{
			// Inverted: OFFENSE has no quick command worth a bare tap, so a tap
			// opens the radial and a hold opens the drawer.  A flick toward one of
			// the radial's nodes fires it directly -- see bindFlick().
			bindFlick(hv.face, hub, RhCommands.OFFENSE_RADIAL, RhCommands.ATK_SLOT_BEARING,
				HUB_HOLD_MS,
				new Runnable() { @Override public void run() { openDrawer(hub.group.id, hub); } },
				new Runnable() { @Override public void run() { toggleRadial(hub); } });
		}
		else if(hub == RhCommands.HUB_EQUIP)
		{
			// The inventory bar over the equipment matrix: tap `i`, hold for the
			// whole gear drawer.  The six cells below it do the rest.
			bindHold(hv.face, HUB_HOLD_MS,
				new Runnable() { @Override public void run() { openDrawer(hub.group.id, hub); } },
				new Runnable() { @Override public void run() { execute(hub.quick, hv.face); } });
		}
		else
		{
			bindHold(hv.face, HUB_HOLD_MS,
				new Runnable() { @Override public void run() { openFan(hv); } },
				new Runnable() { @Override public void run() { hubTapped(hv); } });
		}

		mHubs.add(hv);
	}

	/**
	 * One dispatcher, two entry points.  A pinned point and a fan node fire the
	 * same command, so both route through here -- which owns the key flash, the
	 * instant set, and the direction-arming fall-through.  While these were two
	 * handlers, `z` prompted correctly from the fan and silently armed a direction
	 * from a pinned point.
	 */
	private void fireFromHub(RhCommands.Hub hub, RhCommands.Item item, View from)
	{
		if(hub == RhCommands.HUB_ATTACK && needsDirection(item.key))
		{
			arm(item, from);
			return;
		}
		execute(item, from);
	}

	/** Fight, Kick, Fire and Throw take a direction; Zap, Cast, Swap and Two-weapon do not. */
	private static boolean needsDirection(String key)
	{
		return "F".equals(key) || "^D".equals(key) || "f".equals(key) || "t".equals(key);
	}

	// ____________________________________________________________________________________
	// Pinning.
	//
	// The same grammar on both hubs: hold a fan node or a radial node to pick the
	// command up, the slots light amber and read HERE, tap one to place it.  Hold a
	// pinned slot to clear it.  Pinning a command that is already pinned elsewhere
	// moves it rather than duplicating it.
	//
	// This is the answer to "I want these five always under my thumb" without
	// spending five permanent faces on it: the player pins the two or three they
	// actually use for this role or this task, and the rest stay one tap away.

	private void buildAttackStarPoints(final HubView hv)
	{
		buildSlotGroup(hv, true);
	}

	/*
	 * The equipment matrix (Lucas, 2026-09-23), in place of EQUIP's triangle, its
	 * radial and its satellites.  Three pairs, each put-on over take-off:
	 *
	 *     [          INVENTORY i  .  hold: all gear          ]
	 *     [  Wear W   ] [  Put on P ] [  Wield w ]
	 *     [ Take off T ] [ Remove R  ] [  Swap x  ]
	 *
	 * Lucas asked for two columns of three; this is three columns of two.  His
	 * phone shows the system status bar, so the overlay is about 415dp tall, not
	 * the 443 the layout was drawn for, and three rows of 48dp cells plus the bar
	 * do not fit between the top-right cluster and CONSUME at the 44dp touch
	 * floor.  Three columns of two do, at 48dp, and still keep each pair together.
	 *
	 * Every cell is a pin slot with the usual grammar: long-press a drawer row to
	 * pick a command up, tap a cell to place it, hold a cell to clear it, tap an
	 * empty cell for the drawer.
	 */
	private static final float EQ_CELL  = 48f;
	private static final float EQ_GAP   = 5f;
	private static final float EQ_BAR   = 24f;
	private static final float EQ_TOP   = 96f;   // the top-right cluster ends at 90
	private static final float EQ_RIGHT = 10f;
	private static final float EQ_W     = 3 * EQ_CELL + 2 * EQ_GAP;

	private void buildEquipMatrix(final HubView hv)
	{
		hv.satellites = new FrameLayout(mContext);
		addView(hv.satellites, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		for(int i = 0; i < mEquipSlotKeys.length && i < 6; i++)
		{
			final int index = i;
			int col = i % 3, row = i / 3;
			final RhFace slot = new RhFace(mContext).radius(3f);
			if(mTerm)
			{
				// The bank's full width in three, under a 28dp bar.  The put-on verbs
				// take the skin's inventory colour; the off verbs keep their slate.
				slot.defaultCap(RhTheme.role(RhTheme.ROLE_INVENTORY));
				float cellW = (mPadBox - 2 * T_GAP) / 3f;
				float right = termInner() + (2 - col) * (cellW + T_GAP);
				float top = termEqTop() + T_EQ_BAR + 8f + row * (T_EQ_CELL_H + T_GAP);
				hv.satellites.addView(slot, boxTR(cellW, T_EQ_CELL_H, right, top));
			}
			else
			{
				float right = EQ_RIGHT + (2 - col) * (EQ_CELL + EQ_GAP);
				float top = EQ_TOP + EQ_BAR + EQ_GAP + row * (EQ_CELL + EQ_GAP);
				hv.satellites.addView(slot, boxTR(EQ_CELL, EQ_CELL, right, top));
			}
			hv.slotFaces.add(slot);

			bindHold(slot, SLOT_CLEAR_MS,
				new Runnable()
				{
					@Override
					public void run()
					{
						if(mAssign != null || slotItem(false, index) == null)
							return;
						clearSlot(hv, false, index);
					}
				},
				new Runnable()
				{
					@Override
					public void run()
					{
						if(assignAccepts(false))
						{
							placeAssignment(hv, false, index);
							return;
						}
						RhCommands.Item item = slotItem(false, index);
						if(item == null)
						{
							openDrawer(hv.hub.group.id, hv.hub);
							return;
						}
						execute(item, slot);
					}
				});
		}
		refreshSlots(hv, false);
	}

	/** Create one hub's slot faces and their gestures.  Called once per hub. */
	private void buildSlotGroup(final HubView hv, final boolean attack)
	{
		hv.satellites = new FrameLayout(mContext);
		addView(hv.satellites, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		RhCommands.Hub hub = hv.hub;
		float[] bearings = attack ? RhCommands.ATK_SLOT_BEARING : RhCommands.EQUIP_SAT_BEARING;

		for(int i = 0; i < bearings.length; i++)
		{
			final int index = i;
			float radius = attack ? RhCommands.ATK_SLOT_RADIUS : RhCommands.EQUIP_SAT_RADIUS[i];
			double a = Math.toRadians(bearings[i]);
			float sx = offsetX(hubCx(hub), (float)Math.cos(a) * radius);
			float sy = hubCy(hub) - (float)Math.sin(a) * radius;

			final RhFace slot = new RhFace(mContext).shape(RhFace.Shape.CIRCLE);
			if(mTerm && attack)
				hv.satellites.addView(slot, termAttackSlot(i));
			else
				hv.satellites.addView(slot, centredLB(SAT_SIZE, SAT_SIZE, sx, sy));
			hv.slotFaces.add(slot);

			// Both handlers read the slot's contents at press time, never at build
			// time, so re-pinning cannot leave a face firing its old command.
			bindHold(slot, SLOT_CLEAR_MS,
				new Runnable()
				{
					@Override
					public void run()
					{
						// Hold clears, but never while an assignment is in hand --
						// that gesture is placing, not clearing.
						if(mAssign != null || slotItem(attack, index) == null)
							return;
						clearSlot(hv, attack, index);
					}
				},
				new Runnable()
				{
					@Override
					public void run()
					{
						if(assignAccepts(attack))
						{
							placeAssignment(hv, attack, index);
							return;
						}
						RhCommands.Item item = slotItem(attack, index);
						if(item == null)
						{
							// An empty slot is the discoverable way into its source.
							openRadial(hv.hub);
							return;
						}
						if(attack)
							fireFromHub(RhCommands.HUB_ATTACK, item, slot);
						else
							execute(item, slot);
					}
				});
		}

		refreshSlots(hv, attack);
	}

	/**
	 * Terminal style: OFFENSE's three pinned points as keys.  The first sits in
	 * the left bank beside DROP, directly above the pad's right column; the other
	 * two follow OFFENSE along the deck.  Up, up-right and right of the thumb --
	 * the same order the arc ran in.
	 */
	private LayoutParams termAttackSlot(int i)
	{
		if(i == 0)
			return boxLB(T_KEY, T_KEY, termInner() + mPadBox - T_KEY, termRow3Bottom());
		float left = termDeckLeft() + mPadCell + T_GAP + (i - 1) * (T_SLOT_W + T_GAP);
		return boxLB(T_SLOT_W, RhCase.DECK_KEY, left, termDeckBottom());
	}

	private boolean slotsHiddenBy(HubView hv, boolean attack)
	{
		return hv.hub.id.equals(mFanOpen) || hv.hub.id.equals(mRadialOpen);
	}

	private RhCommands.Item slotItem(boolean attack, int index)
	{
		String[] keys = attack ? mAtkSlotKeys : mEquipSlotKeys;
		return RhCommands.pinnable(keys[index]);
	}

	/** Re-skin one hub's slot faces from its current key array, in place. */
	private void refreshSlots(final HubView hv, final boolean attack)
	{
		boolean taking = assignAccepts(attack);
		// The fan and the radial each sweep through their own slots' space, so the
		// slots stand down while one is open -- unless a command is in hand, when the
		// slots are the whole point of what is on screen.
		boolean hidden = !taking && slotsHiddenBy(hv, attack);

		for(int i = 0; i < hv.slotFaces.size(); i++)
		{
			RhFace slot = hv.slotFaces.get(i);
			RhCommands.Item item = slotItem(attack, i);
			slot.setVisibility(hidden ? GONE : VISIBLE);

			if(taking)
			{
				// Every slot becomes a destination while a command is in hand.
				slot.placeholder(false)
				    .face(RhTheme.A90)
				    .textColor(RhTheme.BADGE_TEXT)
				    .label("HERE", 8f, 0.04f)
				    .sub(null, 7f, RhTheme.RAW_KEY, 1f);
			}
			else if(item == null)
			{
				slot.placeholder(true)
				    .textColor(RhTheme.TEXT)
				    .label("+", 15f, 0f)
				    .sub(null, 7f, RhTheme.RAW_KEY, 1f);
			}
			else
			{
				slot.placeholder(false)
				    .face(item.face != null ? item.face : RhTheme.G90)
				    .textColor(RhTheme.TEXT)
				    .label(labelFor(item), attack ? 7.5f : 9f, 0.02f)
				    .sub(item.key, 7f, RhTheme.RAW_KEY, 1f);
			}
		}
	}

	/** Hold a fan or radial node to pick its command up. */
	private void pickUp(RhCommands.Item item, int target)
	{
		pickUp(item, target, null);
	}

	private void pickUp(RhCommands.Item item, int target, RhCommands.Hub hub)
	{
		closeChips();
		closeFan();
		closeRadial();
		closeDrawer();
		mAssign = item;
		mAssignTarget = target;
		mAssignHub = hub != null ? hub.id : null;
		if(target == ASSIGN_FAN)
		{
			// The fan opens with every node lit; tapping one places the command.
			HubView hv = hubView(mAssignHub);
			if(hv != null)
			{
				mFanOpen = hv.hub.id;
				hv.fan.setVisibility(VISIBLE);
			}
		}
		refreshAllSlots();
		updateHubSubLines();
		applyDimming();
	}

	/** Where a drawer's command can be pinned, by the hub that opened the drawer. */
	private int assignTargetFor(RhCommands.Hub hub)
	{
		if(hub == RhCommands.HUB_ATTACK)
			return ASSIGN_ATTACK;
		if(hub == RhCommands.HUB_EQUIP)
			return ASSIGN_EQUIP;
		if(hub != null && mFanKeys.containsKey(hub.id))
			return ASSIGN_FAN;
		return ASSIGN_BOTH;
	}

	private HubView hubView(String id)
	{
		if(id == null)
			return null;
		for(HubView hv : mHubs)
			if(hv.hub.id.equals(id))
				return hv;
		return null;
	}

	private boolean assignAcceptsFan(RhCommands.Hub hub)
	{
		return mAssign != null && mAssignTarget == ASSIGN_FAN && hub.id.equals(mAssignHub);
	}

	/** A fan node's command: the pinned key, or the fan as shipped. */
	private RhCommands.Item fanItem(RhCommands.Hub hub, int index)
	{
		String[] keys = mFanKeys.get(hub.id);
		if(keys == null || index >= keys.length)
			return index < hub.fan.length ? hub.fan[index] : null;
		return RhCommands.pinnable(keys[index]);
	}

	/** Re-skin one hub's fan nodes from its key array, in place. */
	private void refreshFan(HubView hv)
	{
		boolean taking = assignAcceptsFan(hv.hub);
		for(int i = 0; i < hv.fanFaces.size(); i++)
		{
			RhFace node = hv.fanFaces.get(i);
			if(taking)
			{
				node.placeholder(false)
				    .face(RhTheme.A90)
				    .textColor(RhTheme.BADGE_TEXT)
				    .label("HERE", 8f, 0.04f)
				    .sub(null, 7f, RhTheme.RAW_KEY, 1f);
				continue;
			}
			RhCommands.Item item = fanItem(hv.hub, i);
			if(item == null)
				node.placeholder(true)
				    .textColor(RhTheme.TEXT)
				    .label("+", 15f, 0f)
				    .sub(null, 7f, RhTheme.RAW_KEY, 1f);
			else
				node.placeholder(false)
				    .face(item.face != null ? item.face : RhTheme.G90)
				    .textColor(RhTheme.TEXT)
				    .label(labelFor(item), 8.5f, 0.02f)
				    .sub(subKeyFor(item), 8f, RhTheme.RAW_KEY, 1f);
		}
	}

	private void placeFan(HubView hv, int index)
	{
		String[] keys = mFanKeys.get(hv.hub.id);
		if(keys == null || mAssign == null)
			return;
		// Moving, not duplicating: the command leaves any other node of this fan.
		for(int i = 0; i < keys.length; i++)
			if(mAssign.key.equals(keys[i]))
				keys[i] = null;
		keys[index] = mAssign.key;
		RhPrefs.saveFanSlots(PreferenceManager.getDefaultSharedPreferences(mContext), hv.hub.id, keys);
		mAssign = null;
		mAssignHub = null;
		closeFan();
		refreshAllSlots();
		updateHubSubLines();
		applyDimming();
	}

	private void cancelAssignment()
	{
		if(mAssign == null)
			return;
		boolean fan = mAssignTarget == ASSIGN_FAN;
		mAssign = null;
		mAssignHub = null;
		if(fan)
			closeFan();
		refreshAllSlots();
		updateHubSubLines();
		applyDimming();
	}

	private void placeAssignment(HubView hv, boolean attack, int index)
	{
		String[] keys = attack ? mAtkSlotKeys : mEquipSlotKeys;

		// Moving, not duplicating: clear any slot already holding this command.
		for(int i = 0; i < keys.length; i++)
			if(mAssign.key.equals(keys[i]))
				keys[i] = null;

		keys[index] = mAssign.key;
		saveSlots(attack);
		mAssign = null;
		refreshAllSlots();
		updateHubSubLines();
		applyDimming();
	}

	private void clearSlot(HubView hv, boolean attack, int index)
	{
		String[] keys = attack ? mAtkSlotKeys : mEquipSlotKeys;
		keys[index] = null;
		saveSlots(attack);
		refreshSlots(hv, attack);
	}

	private void saveSlots(boolean attack)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if(attack)
			RhPrefs.saveAtkSlots(prefs, mAtkSlotKeys);
		else
			RhPrefs.saveEquipSlots(prefs, mEquipSlotKeys);
	}

	private void refreshAllSlots()
	{
		for(HubView hv : mHubs)
		{
			refreshFan(hv);
			if(hv.hub == RhCommands.HUB_ATTACK)
				refreshSlots(hv, true);
			else if(hv.hub == RhCommands.HUB_EQUIP)
				refreshSlots(hv, false);
		}
	}

	/** While a command is in hand the owning hub says where it wants to go. */
	private void updateHubSubLines()
	{
		for(HubView hv : mHubs)
		{
			String sub;
			if(hv.hub == RhCommands.HUB_ATTACK && assignAccepts(true))
				sub = "pick a point";
			else if(hv.hub == RhCommands.HUB_EQUIP && assignAccepts(false))
				sub = "pick a cell";
			else if(assignAcceptsFan(hv.hub))
				sub = "pick a node";
			else if(hv.hub.id.equals(mFanOpen))
				sub = "tap = all";
			else
				sub = hubIdleSub(hv.hub);
			hv.face.sub(sub, hv.hub == RhCommands.HUB_EQUIP ? 6.5f : 8f, RhTheme.TEXT, 0.75f);
		}
	}

	/**
	 * A hub's sub-line at rest: what its hold does.  OFFENSE advertises the flick
	 * as well (Lucas, 2026-09-24): flicking up-right to kick a door is the fastest
	 * way through one at low level, and nothing on the face said it was there.
	 */
	private String hubIdleSub(RhCommands.Hub hub)
	{
		if(hub == RhCommands.HUB_EQUIP)
			return "hold · all gear";
		if(hub == RhCommands.HUB_ATTACK)
			return "hold + flick";
		return hub.leftSide ? "hold ▸" : "◂ hold";
	}

	private float hubSubSize(RhCommands.Hub hub)
	{
		return hub == RhCommands.HUB_EQUIP ? 6.5f : 8f;
	}

	private void hubTapped(HubView hv)
	{
		// The hub under a fan that is waiting for a command: a tap is a cancel.
		if(assignAcceptsFan(hv.hub))
		{
			cancelAssignment();
			return;
		}
		if(hv.hub.id.equals(mFanOpen) || hv.hub.id.equals(mRadialOpen))
		{
			// Second tap with the fan (or EQUIP's radial) open: it closes and the
			// group drawer opens.  This is the third level of depth.
			closeFan();
			closeRadial();
			openDrawer(hv.hub.group.id, hv.hub);
			return;
		}
		execute(hv.hub.quick, hv.face);
	}

	private void openFan(HubView hv)
	{
		closeCandidates();
		closeChips();
		closeDrawer();
		closeFan();
		closeRadial();
		mFanOpen = hv.hub.id;
		hv.fan.setVisibility(VISIBLE);
		if(hv.hub == RhCommands.HUB_ATTACK)
			refreshSlots(hv, true);
		hv.face.sub("tap = all", 8f, RhTheme.TEXT, 0.75f);
		applyDimming();
	}

	private void closeFan()
	{
		if(mFanOpen == null)
			return;
		HubView closedFan = null;
		for(HubView hv : mHubs)
		{
			if(!hv.hub.id.equals(mFanOpen))
				continue;
			hv.fan.setVisibility(GONE);
			refreshFan(hv);
			if(hv.hub == RhCommands.HUB_ATTACK)
				closedFan = hv;
			hv.face.sub(hubIdleSub(hv.hub), hubSubSize(hv.hub), RhTheme.TEXT, 0.75f);
		}
		mFanOpen = null;
		if(closedFan != null)
			refreshSlots(closedFan, true);
		applyDimming();
	}

	// ____________________________________________________________________________________
	// The equip radial: five commands, one tap from the thumb, on two staggered
	// arcs.  Five 44dp circles do not fit on a single arc in the space between the
	// top-right cluster and CONSUME.

	/**
	 * A hub's radial: its commands one tap from the thumb, laid out where their
	 * pinnable slots live so promoting one lands it exactly where it was shown.
	 */
	private void buildRadial(final RhCommands.Hub hub, RhCommands.Item[] items,
	                         float[] bearings, float[] radii)
	{
		ViewGroup radial = new FrameLayout(mContext);
		radial.setVisibility(GONE);
		addView(radial, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mRadials.put(hub.id, radial);

		final boolean toAttack = hub == RhCommands.HUB_ATTACK;

		for(int i = 0; i < items.length; i++)
		{
			final RhCommands.Item item = items[i];
			double a = Math.toRadians(bearings[i]);
			float r = radii.length == 1 ? radii[0] : radii[i];
			float sx = offsetX(hubCx(hub), (float)Math.cos(a) * r);
			float sy = hubCy(hub) - (float)Math.sin(a) * r;

			final RhFace node = new RhFace(mContext)
					.shape(RhFace.Shape.CIRCLE)
					.face(item.face != null ? item.face : RhTheme.G90)
					.label(labelFor(item), 7.5f, 0.02f)
					.sub(item.key, 7f, RhTheme.RAW_KEY, 1f);
			radial.addView(node, centredLB(SAT_SIZE, SAT_SIZE, sx, sy));

			bindHold(node, HUB_HOLD_MS,
				new Runnable()
				{
					@Override
					public void run()
					{
						pickUp(item, toAttack ? ASSIGN_ATTACK : ASSIGN_EQUIP);
					}
				},
				new Runnable()
				{
					@Override
					public void run()
					{
						closeRadial();
						// OFFENSE's commands all take a direction, so they arm the
						// interface rather than letting the core prompt.
						if(toAttack)
							fireFromHub(hub, item, node);
						else
							execute(item, node);
					}
				});
		}
	}

	private void toggleRadial(RhCommands.Hub hub)
	{
		if(hub.id.equals(mRadialOpen))
			closeRadial();
		else
			openRadial(hub);
	}

	private void openRadial(RhCommands.Hub hub)
	{
		closeCandidates();
		ViewGroup radial = mRadials.get(hub.id);
		if(radial == null)
			return;
		closeChips();
		closeFan();
		closeDrawer();
		closeContextRadial();
		closeRadial();
		mRadialOpen = hub.id;
		radial.setVisibility(VISIBLE);
		refreshSlotsFor(hub);
		// EQUIP's radial follows the fan grammar, so its hub carries the same hint.
		if(hub == RhCommands.HUB_EQUIP)
			for(HubView hv : mHubs)
				if(hv.hub == hub)
					hv.face.sub("tap = all", 8f, RhTheme.TEXT, 0.75f);
		applyDimming();
	}

	private void closeRadial()
	{
		if(mRadialOpen == null)
			return;
		ViewGroup radial = mRadials.get(mRadialOpen);
		if(radial != null)
			radial.setVisibility(GONE);
		String was = mRadialOpen;
		mRadialOpen = null;
		if(mWedges != null)
			mWedges.setActive(-1);
		for(HubView hv : mHubs)
			if(hv.hub.id.equals(was))
			{
				refreshSlotsFor(hv.hub);
				if(hv.hub == RhCommands.HUB_EQUIP)
					hv.face.sub(hubIdleSub(hv.hub), hubSubSize(hv.hub), RhTheme.TEXT, 0.75f);
			}
		applyDimming();
	}

	private void refreshSlotsFor(RhCommands.Hub hub)
	{
		for(HubView hv : mHubs)
			if(hv.hub == hub && hv.satellites != null)
				refreshSlots(hv, hub == RhCommands.HUB_ATTACK);
	}

	// ____________________________________________________________________________________
	/**
	 * Modality.  While a fan, radial, chip row or pin assignment is open, one
	 * scrim covers everything else: it greys every other control, blocks it, and
	 * a tap on it closes what is open without the tap reaching the map.  The
	 * open popup and the face that opened it are lifted above the scrim, so the
	 * opener keeps its own grammar -- tap OFFENSE again to close its radial, tap
	 * a fan hub again for its drawer.
	 *
	 * This replaced per-hub fading (Lucas, 2026-09-24).  That faded same-side
	 * hubs but left them live, left the numpad live under an open radial, and
	 * never reordered anything, so a fan opened underneath whatever had been
	 * added after it -- DROP's nodes under the macro key, the context radial
	 * under DROP.  Lifting on every open puts the popup on top whatever the
	 * build order was.
	 */
	private void applyDimming()
	{
		for(HubView hv : mHubs)
		{
			hv.face.setAlpha(1f);
			hv.face.setEnabled(true);
			if(hv.satellites != null)
			{
				hv.satellites.setAlpha(1f);
				setGroupEnabled(hv.satellites, true);
			}
		}
		setPadAlpha(mArmed != null ? 1f : padIdleAlpha());
		syncModal();
	}

	/** Show or hide the scrim for what is open now, and lift the open things above it. */
	private void syncModal()
	{
		if(mScrim == null)
			return;

		List<View> lift = new ArrayList<View>();
		if(mFanOpen != null)
		{
			HubView hv = hubView(mFanOpen);
			if(hv != null)
			{
				lift.add(hv.face);
				lift.add(hv.fan);
			}
		}
		if(mRadialOpen != null)
		{
			HubView hv = hubView(mRadialOpen);
			if(hv != null)
				lift.add(hv.face);
			lift.add(mRadials.get(mRadialOpen));
		}
		if(mCtxRadialOpen)
			lift.add(mCtxRadial);
		if(mCandOpen && mCtxStrip.size() > 1)
		{
			lift.add(mCtxStrip.get(1));
			lift.add(mCandRadial);
		}
		if(mChipsOpen != null)
		{
			if(mChipsOpen.equals(RhCommands.CTX_REST.countKey))
			{
				lift.add(mRestWell != null ? mRestWell : mRestFace);
				lift.add(mRestChips);
			}
			else if(mChipsOpen.equals(RhCommands.CTX_LONG_REST.countKey))
			{
				lift.add(mRestWell);
				lift.add(mLongChips);
			}
			else
			{
				for(int i = 0; i < mCtxStrip.size() && i < mChipRows.size(); i++)
				{
					RhCommands.ContextAction act = contextAction(i);
					if(act != null && act.isCounted() && act.countKey.equals(mChipsOpen))
					{
						lift.add(mCtxStrip.get(i));
						lift.add(mChipRows.get(i));
					}
				}
			}
		}
		if(mAssign != null)
		{
			for(HubView hv : mHubs)
			{
				if(hv.satellites != null
						&& ((hv.hub == RhCommands.HUB_ATTACK && assignAccepts(true))
						 || (hv.hub == RhCommands.HUB_EQUIP && assignAccepts(false))))
					lift.add(hv.satellites);
				if(assignAcceptsFan(hv.hub))
				{
					lift.add(hv.face);
					lift.add(hv.fan);
				}
			}
		}

		if(lift.isEmpty())
		{
			mScrim.setVisibility(GONE);
			return;
		}
		mScrim.setHint(mAssign != null
				? "TAP A LIT KEY TO PLACE " + mAssign.word.toUpperCase() + "  ·  TAP ELSEWHERE OR BACK TO CANCEL"
				: "TAP ANYWHERE OR BACK TO CLOSE");
		mScrim.setVisibility(VISIBLE);
		mScrim.bringToFront();
		for(View v : lift)
			liftAboveScrim(v);
		if(mDrawer != null)
			mDrawer.bringToFront();
		if(mFlash != null)
			mFlash.bringToFront();
	}

	/** Bring a view -- or the direct child of this overlay that holds it -- to the top. */
	private void liftAboveScrim(View v)
	{
		while(v != null && v.getParent() != this)
			v = v.getParent() instanceof View ? (View)v.getParent() : null;
		if(v != null)
			v.bringToFront();
	}

	/** Close every fan, radial, chip row and pin in hand.  True if anything was open. */
	private boolean dismissPopups()
	{
		boolean any = mChipsOpen != null || mCandOpen || mAssign != null
				|| mRadialOpen != null || mCtxRadialOpen || mFanOpen != null;
		closeChips();
		closeCandidates();
		cancelAssignment();
		closeRadial();
		closeContextRadial();
		closeFan();
		syncModal();
		return any;
	}

	/**
	 * The scrim itself: a grey wash over everything below it, a line of text
	 * saying how to get out, and a tap anywhere is the way out.
	 */
	private final class ModalScrim extends View
	{
		private final Paint mHint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
		private final Paint mPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.RectF mPill = new android.graphics.RectF();
		private String mText = "";

		ModalScrim(android.content.Context c)
		{
			super(c);
			mHint.setTypeface(mTerm ? RhTheme.capFont(c) : RhTheme.monoBold(c));
			mHint.setTextAlign(Paint.Align.CENTER);
			mHint.setTextSize(RhTheme.dp(c, 9f));
			mHint.setColor(0xd9ffffff);
			if(android.os.Build.VERSION.SDK_INT >= 21)
				mHint.setLetterSpacing(0.1f);
		}

		void setHint(String text)
		{
			if(text.equals(mText))
				return;
			mText = text;
			invalidate();
		}

		@Override
		protected void onDraw(Canvas canvas)
		{
			canvas.drawColor(0x9e05070d);
			float y = mTerm ? RhTheme.dp(getContext(), RhCase.glassTop() + 14f)
			                : RhTheme.rawDp(getContext(), RhTheme.HEADER_HEIGHT + 16f);
			// On a pill of its own: the hint sits over the message lines.
			Paint.FontMetrics fm = mHint.getFontMetrics();
			float half = mHint.measureText(mText) / 2f + RhTheme.dp(getContext(), 10f);
			float pad = RhTheme.dp(getContext(), 4f);
			mPill.set(getWidth() / 2f - half, y + fm.ascent - pad, getWidth() / 2f + half, y + fm.descent + pad);
			float r = mPill.height() / 2f;
			mPillPaint.setColor(0xf205070d);
			canvas.drawRoundRect(mPill, r, r, mPillPaint);
			canvas.drawText(mText, getWidth() / 2f, y, mHint);
		}

		@Override
		public boolean onTouchEvent(MotionEvent e)
		{
			if(e.getActionMasked() == MotionEvent.ACTION_UP)
				dismissPopups();
			return true;
		}
	}

	private static void setGroupEnabled(ViewGroup g, boolean enabled)
	{
		for(int i = 0; i < g.getChildCount(); i++)
			g.getChildAt(i).setEnabled(enabled);
	}

	/**
	 * Offset a design-dp x anchor by a screen-space delta.
	 *
	 * This is plain addition in both conventions.  A right-anchored value is stored
	 * negative, and distance from the right edge grows as screen x falls, so a
	 * leftward (negative) delta correctly makes it more negative.  Negating the
	 * delta for right-anchored controls -- the obvious-looking guess -- sent every
	 * right-hand fan and radial sweeping the wrong way.
	 */
	private static float offsetX(float anchorX, float delta)
	{
		return anchorX + delta;
	}

	// ____________________________________________________________________________________
	// Armed direction mode.

	private void arm(RhCommands.Item cmd, View from)
	{
		closeCandidates();
		closeFan();
		closeDrawer();
		mArmed = cmd;
		flashKey(cmd, from);
		if(!mTerm)
			for(RhFace c : mPadCells)
				c.face(RhTheme.R90);
		updateLamps();
		setPadAlpha(1f);
		refreshPadCentre();
		mArmedBanner.label(cmd.word.toUpperCase() + " — PICK A DIRECTION", 10f, 0.04f);
		mArmedBanner.setVisibility(VISIBLE);
	}

	private void disarm()
	{
		if(mArmed == null)
			return;
		mArmed = null;
		repaintPad();
		updateLamps();
		setPadAlpha(padIdleAlpha());
		refreshPadCentre();
		mArmedBanner.setVisibility(GONE);
	}

	/**
	 * The banner sits above the numpad, never over it.  An earlier position covered
	 * the east key at exactly the moment a direction press was required.
	 */
	private void buildArmedBanner()
	{
		mArmedBanner = new RhFace(mContext)
				.face(RhTheme.R90)
				.label("", 10f, 0.04f);
		mArmedBanner.setVisibility(GONE);
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, RhTheme.dpi(mContext, 30f));
		if(mTerm)
		{
			// Across the top of the map, under the message lines.
			lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
			lp.topMargin = RhTheme.dpi(mContext, RhCase.glassTop() + RhScreen.MSG_BAND + 6f);
		}
		else
		{
			lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			lp.bottomMargin = RhTheme.dpi(mContext, 284f);
		}
		addView(mArmedBanner, lp);

		// Tapping the banner is the ESC cancel.
		bindTap(mArmedBanner, new Runnable()
		{
			@Override
			public void run()
			{
				disarm();
				mHost.sendCommand("\\e");
			}
		});
	}

	// ____________________________________________________________________________________
	// Context radial: the strip's list extended with far look and chat.

	private void openContextRadial()
	{
		closeChips();
		closeFan();
		closeDrawer();
		closeRadial();
		mCtxRadial.removeAllViews();

		float cx = padCentreX();
		float cy = padCentreY();
		float radius = ctxRadialRadius();

		for(int i = 0; i < RhCommands.CTX_RADIAL.length; i++)
		{
			final RhCommands.ContextAction act = RhCommands.CTX_RADIAL[i];
			double a = Math.toRadians(CTX_RADIAL_A0 + i * CTX_RADIAL_STEP);
			float sx = cx + (float)Math.cos(a) * radius;
			float sy = cy - (float)Math.sin(a) * radius;

			final RhFace f = new RhFace(mContext)
					.shape(RhFace.Shape.CIRCLE)
					.face(RhTheme.G90)
					.label(labelForAction(act), 9.5f, 0.03f)
					.sub(subKeyForAction(act), 8.5f, RhTheme.RAW_KEY, 1f);
			mCtxRadial.addView(f, centredLB(CTX_RADIAL_SIZE, CTX_RADIAL_SIZE, sx, sy));
			bindTap(f, new Runnable()
			{
				@Override
				public void run()
				{
					closeContextRadial();
					runAction(act, f);
				}
			});
		}
		mCtxRadialOpen = true;
		mCtxRadial.setVisibility(VISIBLE);
		applyDimming();
	}

	private void closeContextRadial()
	{
		if(!mCtxRadialOpen)
			return;
		mCtxRadialOpen = false;
		mCtxRadial.setVisibility(GONE);
		mCtxRadial.removeAllViews();
		applyDimming();
	}

	// ____________________________________________________________________________________
	// The context strip: Look | <this turn's action> | Search.  Only the middle
	// slot changes; see recomputeContext() for why the other two are nailed down.

	private void buildContextStrip()
	{
		float x = CTX_STRIP_LEFT;
		for(int i = 0; i < 3; i++)
		{
			final int index = i;

			// The face colour is decided per turn in applyStripShape(), since the
			// primary follows the leading contextual action rather than a slot.
			RhFace f = new RhFace(mContext)
					.face(RhTheme.G90)
					.label("", 9.5f, 0.03f);
			if(mTerm)
			{
				addView(f, termStripBox(i));
				buildChipRow(index, termChipBox(i));
			}
			else
			{
				addView(f, boxLB(CTX_STRIP_W, CTX_STRIP_H[i], x, CTX_STRIP_BOTTOM));
				// Search's chips open from slot 2 now, so they anchor to its right edge and
				// reach back over the strip rather than out under the right-hand hubs.
				buildChipRow(index, boxLB(CHIP_ROW_W, CHIP_SIZE,
				                          index == 2 ? x + CTX_STRIP_W - CHIP_ROW_W : x,
				                          CTX_STRIP_BOTTOM + CTX_STRIP_H[i] + CHIP_GAP_ABOVE));
			}
			mCtxStrip.add(f);

			// Bound once; both handlers read the slot's current action at press
			// time, because the strip rewrites itself every turn.
			bindHold(f, CHIP_HOLD_MS,
				new Runnable()
				{
					@Override
					public void run()
					{
						RhCommands.ContextAction act = contextAction(index);
						if(act == null)
							return;
						if(act.isCounted())
						{
							openChips(index);
							return;
						}
						// Far look's hold is Look here -- the same question asked of
						// your own square instead of a distant one.
						if(act.hasAlt())
						{
							closeChips();
							RhFace from = mCtxStrip.get(index);
							flashRaw(act.altKey, from);
							mHost.sendCommand(act.altKey);
						}
					}
				},
				new Runnable()
				{
					@Override
					public void run()
					{
						RhCommands.ContextAction act = contextAction(index);
						if(act == null)
							return;
						if(act.isCounted() && act.countKey.equals(mChipsOpen))
						{
							// Tapping a face whose chips are open just closes them.
							closeChips();
							return;
						}
						closeChips();
						if(index == 1 && mCtxCandidates.size() > 1)
						{
							toggleCandidates();
							return;
						}
						runAction(act, mCtxStrip.get(index));
					}
				});

			x += CTX_STRIP_W + CTX_STRIP_GAP;
		}
		mCandRadial = new FrameLayout(mContext);
		mCandRadial.setVisibility(GONE);
		addView(mCandRadial, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mCandOpen = false;

		setContextActions(new RhCommands.ContextAction[] {
				RhCommands.CTX_LOOK, null, RhCommands.CTX_SEARCH });
	}

	private RhCommands.ContextAction contextAction(int index)
	{
		return index < mCtxActions.length ? mCtxActions[index] : null;
	}

	/*
	 * Terminal style: Look and the context key close the deck, measured from its
	 * right end so they sit toward the right thumb as the strip did; Search moves
	 * to the foot of the right bank, beside INTERACT.  Each chip row opens above
	 * its own key.
	 */
	/**
	 * Look and the context key share whatever the deck has left after OFFENSE and
	 * the two pinned slots, so the deck is full on any screen width -- 117dp each
	 * on the design screen.  Before the first layout the width is unknown and the
	 * design value stands in; the rebuild on the first size change corrects it.
	 */
	private float termStripW()
	{
		if(getWidth() == 0)
			return T_STRIP_W;
		float deckInner = getWidth() / RhTheme.dp(mContext, 1f) - 2 * termDeckLeft();
		float left = mPadCell + 2 * (T_GAP + T_SLOT_W);
		float w = (deckInner - left - 8f - T_GAP - T_GAP) / 2f;
		return RhTheme.clamp(w, 70f, 170f);
	}

	private float termStripRight(int i)
	{
		return i == 0 ? termDeckLeft() + termStripW() + T_GAP : termDeckLeft();
	}

	private LayoutParams termStripBox(int i)
	{
		if(i == 2)
			return box(mPadBox - T_RIGHT_COL - T_GAP, T_SEARCH_H,
			           termInner() + T_RIGHT_COL + T_GAP, termInner(), true);
		return box(termStripW(), RhCase.DECK_KEY, termStripRight(i), termDeckBottom(), true);
	}

	private LayoutParams termChipBox(int i)
	{
		if(i == 2)
			return box(CHIP_ROW_W, CHIP_SIZE, termInner(), termInner() + T_SEARCH_H + CHIP_GAP_ABOVE, true);
		return box(CHIP_ROW_W, CHIP_SIZE, termStripRight(i) + termStripW() - CHIP_ROW_W,
		           termDeckBottom() + RhCase.DECK_KEY + CHIP_GAP_ABOVE, true);
	}

	private static final float CAND_RADIUS = 100f;
	private static final float CAND_SIZE   = 54f;

	/**
	 * The middle slot's fan: every contextual action this turn, on an arc above
	 * the slot, the first in amber; a second tap on the slot closes it.  Three is
	 * the most the current set produces at once (stairs, a container underfoot, a
	 * door beside you -- an altar is never on stairs), so an arc always fits, and
	 * the list form Lucas sketched for four or more is not needed yet.
	 */
	private void toggleCandidates()
	{
		if(mCandOpen)
		{
			closeCandidates();
			return;
		}
		closeFan();
		closeRadial();
		closeDrawer();
		closeContextRadial();
		mCandRadial.removeAllViews();

		int n = mCtxCandidates.size();
		float cx = mTerm ? -(termStripRight(1) + termStripW() / 2f)
		                 : CTX_STRIP_LEFT + CTX_STRIP_W + CTX_STRIP_GAP + CTX_STRIP_W / 2f;
		float cy = mTerm ? termDeckBottom() + RhCase.DECK_KEY / 2f
		                 : CTX_STRIP_BOTTOM + CTX_STRIP_H[1] / 2f;
		float spread = n <= 2 ? 60f : n == 3 ? 45f : 36f;
		float a0 = 270f - spread * (n - 1) / 2f;
		for(int k = 0; k < n; k++)
		{
			final RhCommands.ContextAction act = mCtxCandidates.get(k);
			double a = Math.toRadians(a0 + k * spread);
			float sx = cx + (float)Math.cos(a) * CAND_RADIUS;
			float sy = cy - (float)Math.sin(a) * CAND_RADIUS;
			final RhFace f = new RhFace(mContext)
					.shape(RhFace.Shape.CIRCLE)
					.face(k == 0 ? RhTheme.A90 : RhTheme.G90)
					.label(act.word, 8.5f, 0.02f)
					.sub(act.key, 7f, RhTheme.RAW_KEY, 1f);
			mCandRadial.addView(f, centredLB(CAND_SIZE, CAND_SIZE, sx, sy));
			bindTap(f, new Runnable()
			{
				@Override
				public void run()
				{
					closeCandidates();
					runAction(act, f);
				}
			});
		}
		mCandRadial.setVisibility(VISIBLE);
		mCandOpen = true;
		refreshContextStrip();
	}

	private void closeCandidates()
	{
		if(!mCandOpen || mCandRadial == null)
			return;
		mCandOpen = false;
		mCandRadial.setVisibility(GONE);
		mCandRadial.removeAllViews();
		refreshContextStrip();
	}

	/**
	 * Replace the strip's contents, slot by slot.  A null is an empty slot, drawn
	 * as a placeholder so the position is kept.
	 */
	public void setContextActions(RhCommands.ContextAction[] actions)
	{
		mCtxActions = actions != null ? actions : new RhCommands.ContextAction[0];
		closeChips();
		refreshContextStrip();
	}

	private void refreshContextStrip()
	{
		for(int i = 0; i < mCtxStrip.size(); i++)
		{
			RhFace f = mCtxStrip.get(i);
			RhCommands.ContextAction act = contextAction(i);

			f.setVisibility(VISIBLE);
			applyStripShape(f, i, act);

			if(act == null)
			{
				// The reserved slot with nothing in it: a dashed outline, the same
				// treatment as an empty pin point, so it reads as a place the game
				// will put something rather than a control.  Its handlers bail on
				// the null, so it is inert.
				f.placeholder(true).label("", 9.5f, 0.03f).sub(null, 8f, RhTheme.TEXT, 0f);
				mChipRows.get(i).setVisibility(GONE);
				continue;
			}
			f.placeholder(false);

			if(act.isCounted())
			{
				// The face always shows what it will send, so the count rides in the
				// label and the sub-line carries the hint rather than the raw key.
				int n = countFor(act);
				f.label(RhPrefs.labelMode() == RhPrefs.LabelMode.KEYS
								? act.keyWithCount(n) : act.wordWithCount(n),
						9.5f, 0.03f)
				 .sub(act.countKey.equals(mChipsOpen) ? "pick a count" : "hold to set",
				      8f, RhTheme.TEXT, 0.75f);
			}
			else if(i == 1 && mCtxCandidates.size() > 1)
			{
				// Several actions apply: name them, and let the tap fan them out.
				String lbl = mCtxCandidates.size() == 2
						? mCtxCandidates.get(0).word + "\n" + mCtxCandidates.get(1).word
						: mCtxCandidates.size() + " actions";
				f.label(lbl, 9f, 0.03f)
				 .sub(mCandOpen ? "pick one" : "tap to pick", 7f, RhTheme.TEXT, 0.75f);
			}
			else if(act.holdHint != null)
			{
				f.label(labelForAction(act), 9.5f, 0.03f)
				 .sub(act.holdHint, 7f, RhTheme.TEXT, 0.75f);
			}
			else
			{
				f.label(labelForAction(act), 9.5f, 0.03f)
				 .sub(subKeyForAction(act), 8f, RhTheme.RAW_KEY, 1f);
			}
		}
		refreshChipRows();
		refreshRestFace();
		syncModal();
	}

	/**
	 * Far look wears an eye.  It keys off the command rather than the slot, which
	 * no longer matters now that Far look always sits in the third slot, but it
	 * costs nothing and keeps the shape with the command if the order ever moves.
	 *
	 * It runs 52dp tall, because the lens gives its height back at the ends; the
	 * target still clears 44dp through the middle where the label sits.
	 */
	private void applyStripShape(RhFace f, int index, RhCommands.ContextAction act)
	{
		if(mTerm)
		{
			// Keys keep their size and colour.  The context key says it has
			// something to offer by lighting: its lamp and a backlit legend.
			f.face(RhTheme.G90);
			f.shape(RhFace.Shape.RECT);
			if(index == 1)
				f.lamp(act != null ? RhFace.LAMP_ON : RhFace.LAMP_OFF).lit(act != null);
			if(index == 2)
				f.defaultCap(RhTheme.role(RhTheme.ROLE_SEARCH));
			return;
		}

		// By identity: Look shares its key with Look here in the context radial.
		boolean eye = act == RhCommands.CTX_LOOK;

		/*
		 * The middle slot is the primary -- the handoff's 46/52/46 -- and it is
		 * amber only while it holds something, so the amber still carries
		 * information: when the game has nothing to suggest, nothing is amber.
		 */
		boolean primary = index == 1 && act != null;
		f.face(primary ? RhTheme.A90 : RhTheme.G90);

		float heightDp = (eye || index == 1) ? 52f : 46f;
		int height = RhTheme.dpi(mContext, heightDp);
		LayoutParams lp = (LayoutParams)f.getLayoutParams();
		if(lp != null && lp.height != height)
		{
			lp.height = height;
			f.setLayoutParams(lp);
		}

		f.shape(eye ? RhFace.Shape.LENS : RhFace.Shape.RECT);
	}

	private int countFor(RhCommands.ContextAction act)
	{
		return RhPrefs.count(act.countKey, act.defaultCount);
	}

	// ____________________________________________________________________________________
	// Counted actions.
	//
	// Search and Rest carry a repeat count, because the right number changes
	// constantly -- s20 down a dead end, s5 while suspicious, one careful s after a
	// magic trap has blinded you with monsters closing in.

	private void buildChipRow(final int index, LayoutParams where)
	{
		LinearLayout row = new LinearLayout(mContext);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setVisibility(GONE);

		// Anchored to its button, not centred: centred, the row's 236dp reaches
		// back over the numpad's right column and takes taps from a live movement key.
		addView(row, where);
		mChipRows.add(row);

		for(int c = 0; c < RhCommands.COUNT_CHOICES.length; c++)
		{
			final int value = RhCommands.COUNT_CHOICES[c];
			final RhFace chip = new RhFace(mContext)
					.face(RhTheme.A90)
					.radius(4f)
					.textColor(RhTheme.BADGE_TEXT)
					.label("×" + value, 11f, 0f);

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					RhTheme.dpi(mContext, CHIP_SIZE), RhTheme.dpi(mContext, CHIP_SIZE));
			if(c > 0)
				lp.leftMargin = RhTheme.dpi(mContext, CHIP_GAP);
			row.addView(chip, lp);

			bindTap(chip, new Runnable()
			{
				@Override
				public void run()
				{
					RhCommands.ContextAction act = contextAction(index);
					if(act == null)
						return;
					RhPrefs.saveCount(PreferenceManager.getDefaultSharedPreferences(mContext),
					                  act.countKey, value);
					closeChips();
				}
			});
		}
		addCustomChip(row, new Runnable()
		{
			@Override
			public void run()
			{
				RhCommands.ContextAction act = contextAction(index);
				if(act != null)
					promptCount(act);
			}
		});
	}

	private void openChips(int index)
	{
		RhCommands.ContextAction act = contextAction(index);
		if(act == null || !act.isCounted())
			return;
		closeFan();
		closeRadial();
		closeContextRadial();
		mChipsOpen = act.countKey;
		refreshContextStrip();
	}

	private void closeChips()
	{
		if(mChipsOpen == null)
			return;
		mChipsOpen = null;
		refreshContextStrip();
	}

	private void refreshChipRows()
	{
		for(int i = 0; i < mChipRows.size(); i++)
		{
			RhCommands.ContextAction act = contextAction(i);
			ViewGroup row = mChipRows.get(i);
			boolean open = act != null && act.isCounted() && act.countKey.equals(mChipsOpen);
			row.setVisibility(open ? VISIBLE : GONE);
			if(!open)
				continue;

			highlightCounts(row, RhCommands.COUNT_CHOICES, countFor(act));
		}
	}

	// ____________________________________________________________________________________
	// Fixed faces.

	/**
	 * PRAY and SACRIFICE, deliberately awkward: top-left, the furthest point from
	 * either thumb.  Both are emergency or altar commands the player must never be
	 * able to fire by accident, and they are the only two on amber outside
	 * contextual state.
	 *
	 * Built as one column rather than two absolutely-positioned faces.  The handoff
	 * records that while both carried their own top value, every height change
	 * needed the other's offset re-derived by hand -- which is how a 2dp overlap
	 * with DROP got in twice.  Let layout own the second position.
	 */
	private void buildPrayColumn()
	{
		// One face since 2026-09-23: tap to sacrifice, hold to pray (Lucas).  Prayer
		// keeps its own "Are you sure you want to pray?" confirmation, so a hold that
		// lands by accident while sacrifice-farming costs a keypress, not a prayer
		// timeout.  Merging also took SACRIFICE out from under the DROP chevron,
		// which a 58dp pad lifts into it on a phone that shows its status bar.
		final RhFace f = new RhFace(mContext)
				.face(RhTheme.A90)
				.label("SACRIFICE", 7.5f, 0.02f)
				.sub("hold · pray", 7f, RhTheme.TEXT, 0.75f);

		if(mTerm)
		{
			// The left bank's second row, still the far end of it from the thumb.
			addView(f, boxTL(T_KEY, T_KEY, termInner(), termRow2Top()));
			mTermRow2.add(f);
		}
		else
		{
			LinearLayout column = new LinearLayout(mContext);
			column.setOrientation(LinearLayout.VERTICAL);
			LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			lp.gravity = Gravity.TOP | Gravity.LEFT;
			lp.leftMargin = RhTheme.dpi(mContext, 10f);
			lp.topMargin  = RhTheme.dpi(mContext, 92f);
			addView(column, lp);
			column.addView(f, new LinearLayout.LayoutParams(
					RhTheme.dpi(mContext, 58f), RhTheme.dpi(mContext, 44f)));
		}
		bindHold(f, HUB_HOLD_MS,
			new Runnable() { @Override public void run() { execute(RhCommands.PRAY, f); } },
			new Runnable() { @Override public void run() { execute(RhCommands.SACRIFICE, f); } });
	}

	/**
	 * Status badges hang below the status panel, beside the SACRIFICE face and
	 * clear of the centred message line that used to cover them.  See RhBadges.
	 */
	private void buildBadgeColumn()
	{
		mBadges = new RhBadges(mContext);
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.TOP | Gravity.LEFT;
		lp.leftMargin = RhTheme.dpi(mContext, 74f);
		lp.topMargin  = RhTheme.dpi(mContext, 92f);
		addView(mBadges, lp);
		if(mStatus != null)
			mBadges.setStatus(mStatus);
	}

	private void addPrayFace(LinearLayout column, final RhCommands.Item item, String label,
	                         float labelSize, float topGap)
	{
		final RhFace f = new RhFace(mContext)
				.face(RhTheme.A90)
				.label(label, labelSize, 0.02f)
				.sub(item.key, 7.5f, RhTheme.TEXT, 0.7f);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				RhTheme.dpi(mContext, 58f), RhTheme.dpi(mContext, 44f));
		lp.topMargin = RhTheme.dpi(mContext, topGap);
		column.addView(f, lp);
		bindTap(f, new Runnable()
		{
			@Override
			public void run()
			{
				execute(item, f);
			}
		});
	}

	/** Everything the player does not touch mid-fight, out of thumb range on purpose. */
	private void buildTopRight()
	{
		// Terminal style: four equal keys across the right bank's top row.
		int n = RhCommands.TOP_RIGHT.length;
		float termW = (mPadBox - (n - 1) * T_GAP) / n;
		float right = mTerm ? termInner() : 10f;
		for(int i = RhCommands.TOP_RIGHT.length - 1; i >= 0; i--)
		{
			final RhCommands.RowFace spec = RhCommands.TOP_RIGHT[i];
			final RhFace f = new RhFace(mContext)
					.face(RhTheme.G90)
					.label(spec.label, 9f, 0.06f);
			float w = mTerm ? termW : spec.w;
			LayoutParams lp = new LayoutParams(RhTheme.dpi(mContext, w),
			                                   RhTheme.dpi(mContext, mTerm ? T_ROW1_H : spec.h));
			lp.gravity = Gravity.TOP | Gravity.RIGHT;
			lp.rightMargin = RhTheme.dpi(mContext, right);
			lp.topMargin   = RhTheme.dpi(mContext, mTerm ? termInner() : 46f);
			addView(f, lp);
			bindTap(f, new Runnable()
			{
				@Override
				public void run()
				{
					if("menu".equals(spec.groupId))
						mHost.openSettings();
					else if("keyboard".equals(spec.groupId))
						mHost.toggleKeyboard();
					else
						toggleDrawer(spec.groupId, f);
				}
			});
			mTopRight.add(f);
			right += w + (mTerm ? T_GAP : 6f);
		}
	}

	// The staggered bottom row (INVENT / USE / DROP / LOOK) is gone.  Each of the
	// four found a better home: INVENT is a hold on EQUIP, DROP became its own hub,
	// INTERACT and CONSUME between them cover USE, and Far look moved into the
	// context strip.  Clearing the bottom edge is what let movement take the corner.

	// ____________________________________________________________________________________
	// Drawers.  An open drawer's button switches to amber.

	private void toggleDrawer(String groupId, RhFace button)
	{
		if(groupId.equals(mDrawerOpen))
			closeDrawer();
		else
			openDrawer(groupId);
	}

	private void openDrawer(String groupId)
	{
		openDrawer(groupId, null);
	}

	private void openDrawer(String groupId, RhCommands.Hub from)
	{
		closeCandidates();
		RhCommands.Group g = RhCommands.group(groupId);
		if(g == null)
			return;
		closeFan();
		closeContextRadial();
		disarm();
		mDrawerOpen = groupId;
		mDrawerHub = from;
		mDrawer.show(g, drawerItems(g));
		updateDrawerButtonFaces();
	}

	/**
	 * A group's commands, plus its wizard-mode additions when the core reports
	 * debug mode.  They are appended rather than mixed in, so the ordinary
	 * commands keep their positions and the debug ones collect at the end.
	 */
	private RhCommands.Item[] drawerItems(RhCommands.Group g)
	{
		RhCommands.Item[] extras = RhCommands.wizardExtras(g.id);
		if(extras == null || mStatus == null || !mStatus.isWizard())
			return g.items;

		RhCommands.Item[] all = new RhCommands.Item[g.items.length + extras.length];
		System.arraycopy(g.items, 0, all, 0, g.items.length);
		System.arraycopy(extras, 0, all, g.items.length, extras.length);
		return all;
	}

	private void closeDrawer()
	{
		if(mDrawerOpen == null)
			return;
		mDrawerOpen = null;
		mDrawerHub = null;
		mDrawer.hide();
		updateDrawerButtonFaces();
	}

	private void updateDrawerButtonFaces()
	{
		// mTopRight was built right to left, so walk the spec array the same way.
		for(int i = 0; i < mTopRight.size(); i++)
		{
			RhCommands.RowFace spec = RhCommands.TOP_RIGHT[RhCommands.TOP_RIGHT.length - 1 - i];
			mTopRight.get(i).face(spec.groupId.equals(mDrawerOpen) ? RhTheme.A90 : RhTheme.G90);
		}
	}

	// ____________________________________________________________________________________
	// Command emission.

	private void execute(RhCommands.Item item, View from)
	{
		if(item == RhCommands.SEARCH_MODE)
		{
			closeFan();
			toggleSearchMode();
			return;
		}
		if(item == RhCommands.CASE_TOGGLE)
		{
			closeFan();
			toggleCase();
			return;
		}
		if(item == RhCommands.STATUS_TOGGLE)
		{
			closeFan();
			cycleStatusLines();
			return;
		}
		flashKey(item, from);
		closeFan();
		mHost.sendCommand(item.key);
	}

	private void runAction(RhCommands.ContextAction act, View from)
	{
		// A counted action sends its count with the key, and flashes what it sent.
		String keys = act.isCounted() ? act.keyWithCount(countFor(act)) : act.key;
		flashRaw(keys, from);
		mHost.sendCommand(keys);
	}

	private void flashKey(RhCommands.Item item, View from)
	{
		flashRaw(item.rawKey(), from);
	}

	/** The flash is anchored to the pressed element's top-centre, just above the finger. */
	private void flashRaw(String key, View from)
	{
		if(!RhPrefs.keyFlash() || key == null || key.length() == 0)
			return;

		float w = mFlash.pillWidth(key);
		float h = mFlash.pillHeight();
		LayoutParams lp = new LayoutParams(Math.round(w), Math.round(h));
		lp.gravity = Gravity.TOP | Gravity.LEFT;

		if(from != null)
		{
			float cx = from.getLeft() + from.getWidth() / 2f;
			float top = from.getTop();
			lp.leftMargin = Math.round(cx - w / 2f);
			lp.topMargin  = Math.round(top - h);
		}
		else
		{
			lp.gravity = Gravity.CENTER;
		}
		mFlash.setLayoutParams(lp);
		mFlash.flash(key);
	}

	// ____________________________________________________________________________________
	// Label modes.  In "keys only" the interface reverts to bare NetHack keys with
	// identical layout and positions, so muscle memory survives the switch.

	private String labelFor(RhCommands.Item item)
	{
		switch(RhPrefs.labelMode())
		{
			case KEYS: return item.key;
			default:   return item.word;
		}
	}

	private String subKeyFor(RhCommands.Item item)
	{
		switch(RhPrefs.labelMode())
		{
			case BOTH: return item.key;
			default:   return null;
		}
	}

	private String labelForAction(RhCommands.ContextAction act)
	{
		return RhPrefs.labelMode() == RhPrefs.LabelMode.KEYS ? act.key : act.word;
	}

	private String subKeyForAction(RhCommands.ContextAction act)
	{
		return RhPrefs.labelMode() == RhPrefs.LabelMode.BOTH ? act.key : null;
	}

	// ____________________________________________________________________________________
	// Gesture plumbing.

	private void bindTap(final RhFace face, final Runnable action)
	{
		face.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent e)
			{
				switch(e.getActionMasked())
				{
					case MotionEvent.ACTION_DOWN:
						face.setFacePressed(true);
						return true;
					case MotionEvent.ACTION_UP:
						if(face.isFacePressed())
						{
							face.setFacePressed(false);
							action.run();
						}
						return true;
					case MotionEvent.ACTION_CANCEL:
						face.setFacePressed(false);
						return true;
				}
				return false;
			}
		});
	}

	/**
	 * Tap / hold / tap-again on one face.
	 *
	 * The release of the gesture that *opened* the hold state must be a no-op, or
	 * it is indistinguishable from a fresh second tap and the fan closes the
	 * instant the finger lifts.  The opening gesture is tagged and its own pointer
	 * up swallowed -- and the "no timer pending" bail-out has to come after that
	 * check, because that is exactly the case the second tap needs.
	 */
	private void bindHold(final RhFace face, final int holdMs, final Runnable onHold,
	                      final Runnable onTap)
	{
		face.setOnTouchListener(new OnTouchListener()
		{
			private Runnable pending;
			private boolean justOpened;

			@Override
			public boolean onTouch(View v, MotionEvent e)
			{
				switch(e.getActionMasked())
				{
					case MotionEvent.ACTION_DOWN:
						face.setFacePressed(true);
						justOpened = false;
						pending = new Runnable()
						{
							@Override
							public void run()
							{
								pending = null;
								justOpened = true;
								onHold.run();
							}
						};
						mHandler.postDelayed(pending, holdMs);
						return true;

					case MotionEvent.ACTION_UP:
						face.setFacePressed(false);
						if(justOpened)
						{
							justOpened = false;
							return true;
						}
						if(pending == null)
							return true;
						mHandler.removeCallbacks(pending);
						pending = null;
						onTap.run();
						return true;

					case MotionEvent.ACTION_CANCEL:
						face.setFacePressed(false);
						if(pending != null)
						{
							mHandler.removeCallbacks(pending);
							pending = null;
						}
						justOpened = false;
						return true;
				}
				return false;
			}
		});
	}

	/** The flick's wedge edges for a bearing set; the rule is in bindFlick()'s notes. */
	private static float[][] flickWedges(float[] bearings, int n)
	{
		float[] lower = new float[n], upper = new float[n];
		for(int i = 0; i < n; i++)
		{
			lower[i] = i > 0 ? (bearings[i - 1] + bearings[i]) / 2f
			                 : bearings[i] - (bearings[i + 1] - bearings[i]) / 2f - FLICK_ARC_SLACK;
			upper[i] = i < n - 1 ? (bearings[i] + bearings[i + 1]) / 2f
			                     : bearings[i] + (bearings[i] - bearings[i - 1]) / 2f + FLICK_ARC_SLACK;
		}
		return new float[][] { lower, upper };
	}

	/**
	 * OFFENSE's flick wedges, drawn behind its radial: faint while the radial is
	 * up, lit under the finger during a flick.  Bearings are CSS-convention
	 * degrees, which is also Canvas.drawArc's convention, so they draw as stored.
	 */
	private final class WedgeView extends View
	{
		private final float[] mLo, mHi;
		private int mActive = -1;
		private final Paint mFillP = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mEdgeP = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Path mPath = new android.graphics.Path();
		private final android.graphics.RectF mOuter = new android.graphics.RectF();
		private final android.graphics.RectF mInner = new android.graphics.RectF();

		WedgeView(android.content.Context c, float[] lo, float[] hi)
		{
			super(c);
			mLo = lo;
			mHi = hi;
			mEdgeP.setStyle(Paint.Style.STROKE);
			mEdgeP.setStrokeWidth(RhTheme.dp(c, 1f));
		}

		void setActive(int i)
		{
			if(i == mActive)
				return;
			mActive = i;
			invalidate();
		}

		@Override
		protected void onDraw(Canvas canvas)
		{
			float cx = RhTheme.dp(getContext(), hubCx(RhCommands.HUB_ATTACK));
			float cy = getHeight() - RhTheme.dp(getContext(), hubCy(RhCommands.HUB_ATTACK));
			float rIn  = RhTheme.dp(getContext(), hubW(RhCommands.HUB_ATTACK) / 2f + 5f);
			float rOut = RhTheme.dp(getContext(), RhCommands.ATK_SLOT_RADIUS + SAT_SIZE / 2f + 12f);
			mOuter.set(cx - rOut, cy - rOut, cx + rOut, cy + rOut);
			mInner.set(cx - rIn, cy - rIn, cx + rIn, cy + rIn);
			for(int i = 0; i < mLo.length; i++)
			{
				float sweep = mHi[i] - mLo[i];
				mPath.reset();
				mPath.arcTo(mOuter, mLo[i], sweep, true);
				mPath.arcTo(mInner, mHi[i], -sweep, false);
				mPath.close();
				boolean on = i == mActive;
				mFillP.setColor(on ? 0x5cffffff : 0x14ffffff);
				canvas.drawPath(mPath, mFillP);
				mEdgeP.setColor(on ? 0xccffffff : 0x40ffffff);
				canvas.drawPath(mPath, mEdgeP);
			}
		}
	}

	/**
	 * Tap / hold / flick on one hub face.  The model is in the FLICK_* notes.
	 *
	 * Bearings are CSS-convention degrees (0 = east, y down, clockwise), which is
	 * also what atan2 on raw touch deltas yields, so a flick's angle compares to
	 * a node's bearing with no conversion.  Wedge i is the span of bearings
	 * nearer to bearings[i] than to its neighbours; the two outer wedges run a
	 * half-step plus FLICK_ARC_SLACK past their node.  Outside that arc a flick
	 * is a cancel, not a nearest-node guess -- a flick straight at the numpad
	 * should not fire Fight.
	 *
	 * The tap and hold halves are bindHold()'s, with the same swallowed-release
	 * rule for the gesture that opened the hold state.
	 */
	private void bindFlick(final RhFace face, final RhCommands.Hub hub,
	                       final RhCommands.Item[] items, final float[] bearings,
	                       final int holdMs, final Runnable onHold, final Runnable onTap)
	{
		final float slopPx = RhTheme.dp(mContext, FLICK_SLOP_DP);
		final float minPx  = RhTheme.dp(mContext, FLICK_MIN_DP);
		final int n = Math.min(items.length, bearings.length);

		// Wedge boundaries, once.  The arc is centred so that a bearing can be
		// normalised into (centre - 180, centre + 180] before comparing, which keeps
		// a wedge straddling +/-180 from being split in two.
		final float[][] edges = flickWedges(bearings, n);
		final float[] lower = edges[0], upper = edges[1];
		final float arcCentre = (lower[0] + upper[n - 1]) / 2f;

		face.setOnTouchListener(new OnTouchListener()
		{
			private Runnable pendingHold, pendingReveal;
			private boolean justOpened, dragging, revealed;
			private long downAt;
			private float x0, y0;
			private int wedge = -1;

			private int wedgeAt(float dx, float dy)
			{
				float ang = (float)Math.toDegrees(Math.atan2(dy, dx));
				while(ang <= arcCentre - 180f) ang += 360f;
				while(ang >  arcCentre + 180f) ang -= 360f;
				for(int i = 0; i < n; i++)
					if(ang >= lower[i] && ang < upper[i])
						return i;
				return -1;
			}

			// The i-th node among the radial's faces; the wedge view sits at child 0.
			private RhFace node(int i)
			{
				ViewGroup radial = mRadials.get(hub.id);
				if(radial == null || i < 0)
					return null;
				for(int c = 0, k = 0; c < radial.getChildCount(); c++)
				{
					View v = radial.getChildAt(c);
					if(v instanceof RhFace && k++ == i)
						return (RhFace)v;
				}
				return null;
			}

			private void highlight(int i)
			{
				if(i == wedge)
					return;
				RhFace old = node(wedge);
				if(old != null)
					old.setFacePressed(false);
				wedge = i;
				RhFace now = node(wedge);
				if(now != null && hub.id.equals(mRadialOpen))
					now.setFacePressed(true);
				if(mWedges != null)
					mWedges.setActive(hub.id.equals(mRadialOpen) ? wedge : -1);
			}

			private void reveal()
			{
				if(!dragging || revealed)
					return;
				if(!hub.id.equals(mRadialOpen))
				{
					openRadial(hub);
					revealed = true;
				}
				// Re-apply so the node lights whether the radial was just opened
				// or was already up from an earlier tap.
				int w = wedge;
				wedge = -1;
				highlight(w);
			}

			private void clearTimers()
			{
				if(pendingHold != null)
				{
					mHandler.removeCallbacks(pendingHold);
					pendingHold = null;
				}
				if(pendingReveal != null)
				{
					mHandler.removeCallbacks(pendingReveal);
					pendingReveal = null;
				}
			}

			private void reset()
			{
				clearTimers();
				highlight(-1);
				dragging = false;
				revealed = false;
				justOpened = false;
			}

			@Override
			public boolean onTouch(View v, MotionEvent e)
			{
				switch(e.getActionMasked())
				{
					case MotionEvent.ACTION_DOWN:
						face.setFacePressed(true);
						reset();
						x0 = e.getX();
						y0 = e.getY();
						downAt = e.getEventTime();
						pendingHold = new Runnable()
						{
							@Override
							public void run()
							{
								pendingHold = null;
								justOpened = true;
								onHold.run();
							}
						};
						mHandler.postDelayed(pendingHold, holdMs);
						pendingReveal = new Runnable()
						{
							@Override
							public void run()
							{
								pendingReveal = null;
								reveal();
							}
						};
						mHandler.postDelayed(pendingReveal, FLICK_REVEAL_MS);
						return true;

					case MotionEvent.ACTION_MOVE:
					{
						if(justOpened)
							return true;
						float dx = e.getX() - x0, dy = e.getY() - y0;
						float dist = (float)Math.hypot(dx, dy);
						if(!dragging)
						{
							if(dist < slopPx)
								return true;
							// Moving turns the press into a flick: the hold can no
							// longer happen, and the reveal is due now if its time
							// has already passed.
							dragging = true;
							if(pendingHold != null)
							{
								mHandler.removeCallbacks(pendingHold);
								pendingHold = null;
							}
							if(e.getEventTime() - downAt >= FLICK_REVEAL_MS)
							{
								if(pendingReveal != null)
								{
									mHandler.removeCallbacks(pendingReveal);
									pendingReveal = null;
								}
								highlight(wedgeAt(dx, dy));
								reveal();
								return true;
							}
						}
						highlight(wedgeAt(dx, dy));
						return true;
					}

					case MotionEvent.ACTION_UP:
					{
						face.setFacePressed(false);
						clearTimers();
						if(justOpened)
						{
							justOpened = false;
							return true;
						}
						if(!dragging)
						{
							onTap.run();
							return true;
						}
						float dx = e.getX() - x0, dy = e.getY() - y0;
						float dist = (float)Math.hypot(dx, dy);
						int w = dist >= minPx ? wedgeAt(dx, dy) : -1;
						highlight(-1);
						if(w >= 0)
						{
							closeRadial();
							fireFromHub(hub, items[w], face);
						}
						else if(dist < minPx && !revealed)
						{
							// A wobble, not a flick: the tap it was meant to be.
							onTap.run();
						}
						else if(revealed && w < 0 && dist >= minPx)
						{
							// Flicked clear of the arc with the menu up: a cancel.
							closeRadial();
						}
						// Otherwise the radial this gesture revealed stays up for a
						// second look -- the player was reaching, not flicking.
						dragging = false;
						revealed = false;
						return true;
					}

					case MotionEvent.ACTION_CANCEL:
						face.setFacePressed(false);
						if(revealed)
							closeRadial();
						reset();
						return true;
				}
				return false;
			}
		});
	}

	// ____________________________________________________________________________________
	// Layout helpers.  Every anchor is design dp; a negative x is measured from the
	// right edge so a control keeps its distance from the thumb on any width.

	/**
	 * Place a box against one horizontal edge.
	 *
	 * The side is an explicit argument rather than the sign of the offset.  While
	 * the sign carried both the value and the side, converting a centre to a left
	 * edge could flip it: a node centred 16dp from the right edge became +6 once
	 * half its width was added, which read as "6dp from the *left*" and threw the
	 * control clean across the screen.  Margins may be negative; a side may not be
	 * inferred.
	 */
	private LayoutParams box(float wDp, float hDp, float edgeDp, float bottomDp, boolean fromRight)
	{
		LayoutParams lp = new LayoutParams(RhTheme.dpi(mContext, wDp), RhTheme.dpi(mContext, hDp));
		lp.gravity = Gravity.BOTTOM | (fromRight ? Gravity.RIGHT : Gravity.LEFT);
		if(fromRight)
			lp.rightMargin = RhTheme.dpi(mContext, edgeDp);
		else
			lp.leftMargin = RhTheme.dpi(mContext, edgeDp);
		lp.bottomMargin = RhTheme.dpi(mContext, bottomDp);
		return lp;
	}

	/** A box against the top-right corner, for controls that must clear the top cluster. */
	private LayoutParams boxTR(float wDp, float hDp, float rightDp, float topDp)
	{
		LayoutParams lp = new LayoutParams(RhTheme.dpi(mContext, wDp), RhTheme.dpi(mContext, hDp));
		lp.gravity = Gravity.TOP | Gravity.RIGHT;
		lp.rightMargin = RhTheme.dpi(mContext, rightDp);
		lp.topMargin = RhTheme.dpi(mContext, topDp);
		return lp;
	}

	private LayoutParams boxLB(float wDp, float hDp, float leftDp, float bottomDp)
	{
		return box(wDp, hDp, leftDp, bottomDp, false);
	}

	/**
	 * Centre a box on a point.  A negative cx is a distance from the right edge,
	 * and stays measured from the right however far left it goes.
	 */
	private LayoutParams centredLB(float wDp, float hDp, float cxDp, float cyFromBottomDp)
	{
		boolean fromRight = cxDp < 0;
		float magnitude = Math.abs(cxDp);
		return box(wDp, hDp, magnitude - wDp / 2f, cyFromBottomDp - hDp / 2f, fromRight);
	}

	// ____________________________________________________________________________________
	// Lifecycle.

	public void preferencesUpdated(SharedPreferences prefs)
	{
		RhTheme.loadPrefs(prefs);
		RhPrefs.load(prefs);
		// Movement key size is a preference, and every pad-derived position reads
		// these; assigning them only in the constructor meant a new size waited
		// for an app restart.
		mPadCell = RhPrefs.padCell();
		mPadBox  = 3 * mPadCell + 2 * PAD_GAP;
		updateFitLimit();
		rebuild();
	}

	/** Labels and scale both change every face, so rebuild rather than patch. */
	private void rebuild()
	{
		removeAllViews();
		mPadCells.clear();
		// Every face list must be emptied here.  mMacroFaces was missed: the rebuilt
		// macro face went in at index 1 while refreshMacroFace(0) kept labelling the
		// detached one, so after any visit to Settings the header's macro slot drew
		// blank -- and still ran, because a tap reads the prefs, not the face.
		java.util.Arrays.fill(mMacroFaces, null);
		mCtxCandidates.clear();
		mCandOpen = false;
		mWedges = null;
		mCtxStrip.clear();
		mChipRows.clear();
		mChipsOpen = null;
		mTopRight.clear();
		mHubs.clear();
		mAtkSlots.clear();
		mFanOpen = null;
		mDrawerOpen = null;
		mArmed = null;
		mCtxRadialOpen = false;
		mRadialOpen = null;
		mRadials.clear();
		mAssign = null;
		mAssignHub = null;
		mDrawerHub = null;
		build();
		statusUpdated(mStatus);
		setMessage(mMessageText);
		applyVisibility();
	}

	public void onConfigurationChanged(Configuration cfg)
	{
		mPortrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT;
		applyVisibility();
	}

	/**
	 * The overlay shows only when it is switched on and the phone is landscape.
	 * Portrait still runs the classic ForkFront panels: the handoff's portrait
	 * layout predates the five-hub model and has not been brought in line with it.
	 */
	private void applyVisibility()
	{
		setVisibility(RhPrefs.enabled() && !mPortrait && !mSuppressed ? VISIBLE : GONE);
		// The terminal frames the map only while it is showing.
		mHost.mapAreaChanged();
	}

	/**
	 * Stand down while the soft keyboard is up.  The keyboard takes the bottom of
	 * the window, so the map area shrinks and every bottom-anchored control would
	 * ride up into it -- and with the keyboard showing, the classic status and
	 * message views come back to fill the gap.
	 */
	public void setSuppressed(boolean suppressed)
	{
		if(mSuppressed == suppressed)
			return;
		mSuppressed = suppressed;
		applyVisibility();
	}

	/**
	 * Back closes whatever the interface has open -- a drawer, then any fan,
	 * radial, chip row or pin in hand -- before it reaches the game.  It never
	 * did: nothing called this, so Back went to the core and on to the system,
	 * which closed the app with a radial up (Lucas, 2026-09-24).
	 */
	public boolean onBackPressed()
	{
		if(mDrawerOpen != null)  { closeDrawer(); return true; }
		if(dismissPopups())      return true;
		if(mRestWell != null && mRestWell.isRevealed()) { mRestWell.scrollTo(false); return true; }
		if(mArmed != null)       { disarm(); mHost.sendCommand("\\e"); return true; }
		return false;
	}
}
