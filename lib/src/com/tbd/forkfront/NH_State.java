package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.*;
import com.tbd.forkfront.*;
import com.tbd.forkfront.Hearse.Hearse;
import com.tbd.forkfront.rolehack.RhOverlay;
import com.tbd.forkfront.rolehack.RhStatus;
import com.tbd.forkfront.rolehack.RhPrefs;
import com.tbd.forkfront.rolehack.RhTheme;

public class NH_State
{
	private enum CmdMode
	{
		Panel,
		Keyboard,
	}

	private Activity mContext;
	private NetHackIO mIO;
	private NHW_Message mMessage;
	private NHW_Status mStatus;
	private NHW_Map mMap;
	private NH_GetLine mGetLine;
	private NH_Question mQuestion;
	private ArrayList<NH_Window> mWindows;
	private Tileset mTileset;
	private CmdPanelLayout mCmdPanelLayout;
	private DPadOverlay mDPad;
	private RhOverlay mRolehackUI;
	private final RhStatus mRolehackStatus = new RhStatus();
	private ByteDecoder mDecoder;
	private boolean mIsDPadActive;
	private boolean mStickyKeyboard;
	private boolean mHideQuickKeyboard;
	private CmdMode mMode;
	private SoftKeyboard mKeyboard;
	private boolean mControlsVisible;
	private boolean mNumPad;
	private boolean mIsMouseLocked;
	private Hearse mHearse;
	private SoftKeyboard.KEYBOARD mRegularKeyboard;
	private SoundPlayer mSoundPlayer;

	// ____________________________________________________________________________________
	public NH_State(Activity context, ByteDecoder decoder)
	{
		mDecoder = decoder;
		mIO = new NetHackIO(context, NhHandler, decoder);
		mTileset = new Tileset(context);
		mWindows = new ArrayList<>();
		mGetLine = new NH_GetLine(mIO, this);
		mQuestion = new NH_Question(mIO, this);
		mMessage = new NHW_Message(context, mIO);
		mStatus = new NHW_Status(context, mIO);
		mMap = new NHW_Map(context, mTileset, mStatus, this, decoder);
		mCmdPanelLayout = (CmdPanelLayout)context.findViewById(R.id.cmdPanelLayout1);
		mDPad = new DPadOverlay(this);
		createRolehackUI(context);
		mKeyboard = new SoftKeyboard(context, this);
		mSoundPlayer = new SoundPlayer();
		mMode = CmdMode.Panel;

		setContext(context);
	}

	// ____________________________________________________________________________________
	public void setContext(Activity context)
	{
		mContext = context;
		for(NH_Window w : mWindows)
			w.setContext(context);
		mGetLine.setContext(context);
		mQuestion.setContext(context);
		mMessage.setContext(context);
		mStatus.setContext(context);
		mCmdPanelLayout.setContext(context, this);
		mDPad.setContext(context);
		createRolehackUI(context);
		mMap.setContext(context);
		mTileset.setContext(context);
	}

	// ____________________________________________________________________________________
	public void startNetHack(String path)
	{
		mIO.start(path);

		preferencesUpdated();
		updateVisibleState();

		mMap.loadZoomLevel();

		// I have preferences already, might as well pass them in...
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		mHearse = new Hearse(mContext, prefs, path);
	}

	// ____________________________________________________________________________________
	private String getLastUsername()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		return prefs.getString("lastUsername", "");
	}

	// ____________________________________________________________________________________
	public void onConfigurationChanged(Configuration newConfig)
	{
		if(mMode == CmdMode.Keyboard)
		{
			// Since the keyboard refuses to change its layout when the orientation changes
			// we recreate a new keyboard every time
			hideKeyboard();
			showKeyboard();
		}

		mCmdPanelLayout.setOrientation(newConfig.orientation);
		mDPad.setOrientation(newConfig.orientation);
		if(mRolehackUI != null)
		{
			mRolehackUI.onConfigurationChanged(newConfig);
			// setOrientation() above re-shows the classic panels whenever the
			// orientation flips, so let the one method that knows who owns the
			// controls decide.  It re-applies the top band too, whose clearance
			// depends on the overlay's visibility.
			updateVisibleState();
		}
	}

	// ____________________________________________________________________________________
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void preferencesUpdated()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

		mCmdPanelLayout.preferencesUpdated(prefs);
		mDPad.preferencesUpdated(prefs);
		RhTheme.loadPrefs(prefs);
		RhPrefs.load(prefs);
		if(mRolehackUI != null)
			mRolehackUI.preferencesUpdated(prefs);
		mMap.preferencesUpdated(prefs);
		mStatus.preferencesUpdated(prefs);
		mMessage.preferencesUpdated(prefs);
		for(NH_Window w : mWindows)
		{
			if(w != mMap && w != mStatus && w != mMessage)
				w.preferencesUpdated(prefs);
		}

		// Rolehack: through updateVisibleState(), which knows whether the mobile
		// interface owns the controls.  Showing the classic panels directly put
		// them back under the overlay after every visit to Settings, until the
		// next key press ran updateVisibleState() and hid them again.
		updateVisibleState();

		mTileset.updateTileset(prefs, mContext.getResources());
		mMap.updateZoomLimits();
		updateSystemUiVisibilityFlags(prefs);
	}

	// ____________________________________________________________________________________
	public void onCreateContextMenu(ContextMenu menu, View v)
	{
		mCmdPanelLayout.onCreateContextMenu(menu, v);
	}

	// ____________________________________________________________________________________
	public void onContextMenuClosed() {
		mCmdPanelLayout.onContextMenuClosed();
		updateSystemUiVisibilityFlags(PreferenceManager.getDefaultSharedPreferences(mContext));
	}

	// ____________________________________________________________________________________
	private void updateSystemUiVisibilityFlags(SharedPreferences prefs)
	{
		boolean isFullscreen = prefs.getBoolean("fullscreen", false);
		int fullscreenFlag = isFullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0;
		mContext.getWindow().setFlags(fullscreenFlag, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			boolean isImmersive = prefs.getBoolean("immersive", false);
			int uiVisibilityFlags = isImmersive ?
					(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
					: 0;
			mContext.getWindow().getDecorView().setSystemUiVisibility(uiVisibilityFlags);
		}
	}

	// ____________________________________________________________________________________
	public void onContextItemSelected(android.view.MenuItem item)
	{
		mCmdPanelLayout.onContextItemSelected(item);
	}

	// ____________________________________________________________________________________
	public boolean handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput)
	{
		if(keyCode == KeyEvent.KEYCODE_BACK && isKeyboardMode())
		{
			hideKeyboard();
			restoreRegularKeyboard();
			return true;
		}

		if(repeatCount > 0) switch(keyCode) {
			case KeyAction.Keyboard:
				if(mMode == CmdMode.Keyboard)
					mStickyKeyboard = false;
			case KeyAction.Control:
			case KeyAction.Meta:
			case KeyEvent.KEYCODE_ESCAPE:
				// Ignore repeat on these actions
				return true;
		}

		KeyEventResult ret = mGetLine.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		if(ret == KeyEventResult.IGNORED)
			ret = mQuestion.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		for(int i = mWindows.size() - 1; ret == KeyEventResult.IGNORED && i >= 0; i--)
		{
			NH_Window w = mWindows.get(i);
			ret = w.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);
		}

		if(ret == KeyEventResult.HANDLED)
			return true;
		if(ret == KeyEventResult.RETURN_TO_SYSTEM)
			return false;

		// Rolehack: an open fan, radial, drawer or chip row closes on Back before the
		// key goes anywhere else.  Nothing called rolehackBackPressed(), so Back went
		// on to the core and the system and closed the app (Lucas, 2026-09-24).
		if(keyCode == KeyEvent.KEYCODE_BACK && rolehackBackPressed())
			return true;

		if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)
		{
			if(mMode == CmdMode.Keyboard)
			{
				hideKeyboard();
				return true;
			}

			if(mIsDPadActive)
				return sendKeyCmd('\033');
		}
		else if(keyCode == KeyAction.Keyboard)
		{
			mStickyKeyboard = true;
			toggleKeyboard();
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(!Util.hasPhysicalKeyboard(mContext))
			{
				saveRegularKeyboard();
				if(mMode != CmdMode.Keyboard)
					mHideQuickKeyboard = true;
				showKeyboard();
				if(keyCode == KeyAction.Control)
					setCtrlKeyboard();
				else
					setMetaKeyboard();
			}
			return true;
		}
		if(keyCode == KeyEvent.KEYCODE_BACK) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
			int action = Util.parseInt(prefs.getString("backAction", ""), KeyAction.SystemDefault);
			switch (action) {
				case KeyAction.SystemDefault:
					break;
				case KeyAction.RecenterCharacter:
					if (mMap.isViewPanned()) {
						mMap.centerViewAroundPlayer();
						return true;
					}
				default:
					Log.print("warning: illegal backAction setting!");
			}
		}
		if(DEBUG.runTrace() && keyCode == KeyEvent.KEYCODE_BACK)
			Debug.stopMethodTracing();
		return sendKeyCmd(nhKey);
	}

	// ____________________________________________________________________________________
	public NH_Window getWindow(int wid)
	{
		int i = getWindowI(wid);
		return i >= 0 ? mWindows.get(i) : null;
	}

	// ____________________________________________________________________________________
	public int getWindowI(int wid)
	{
		for(int i = 0; i < mWindows.size(); i++)
			if(mWindows.get(i).id() == wid)
				return i;
		return -1;
	}

	// ____________________________________________________________________________________
	public NH_Window toFront(int wid)
	{
		int i = getWindowI(wid);
		NH_Window w = null;
		if(i >= 0)
		{
			w = mWindows.get(i);
			if(i < mWindows.size() - 1)
			{
				mWindows.remove(i);
				mWindows.add(w);
			}
		}
		return w;
	}

	// ____________________________________________________________________________________
	public boolean handleKeyUp(int keyCode)
	{
		if(mMap.handleKeyUp(keyCode))
			return true;

		if(keyCode == KeyAction.Keyboard)
		{
			if(!mStickyKeyboard && mMode == CmdMode.Keyboard)
				hideKeyboard();
			mStickyKeyboard = false;
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(mMode == CmdMode.Keyboard)
			{
				if(mHideQuickKeyboard)
					hideKeyboard();
				restoreRegularKeyboard();
			}

			mHideQuickKeyboard = false;
			return true;
		}
		return false;
	}

	// ____________________________________________________________________________________
	public boolean isMouseLocked()
	{
		return mIsMouseLocked;
	}

	// ____________________________________________________________________________________
	public void saveAndQuit()
	{
		mIO.saveAndQuit();
	}

	// ____________________________________________________________________________________
	public void saveState()
	{
		mIO.saveState();
	}

	// ____________________________________________________________________________________
	public Handler getHandler()
	{
		return mIO.getHandler();
	}

	// ____________________________________________________________________________________
	public void waitReady()
	{
		mIO.waitReady();
	}

	// ____________________________________________________________________________________
	public boolean sendKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		mIO.sendKeyCmd((char)key);
		return true;
	}

	// ____________________________________________________________________________________
	public boolean sendDirKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		if(key == 0x80 || key == '\033')
			mIsMouseLocked = false;
		if(mIsDPadActive)
			mIO.sendKeyCmd((char)key);
		else
			mIO.sendDirKeyCmd((char)key);
		return true;
	}

	// ____________________________________________________________________________________
	public void sendPosCmd(int x, int y)
	{
		mIsMouseLocked = false;
		mIO.sendPosCmd(x, y);
	}

	// ____________________________________________________________________________________
	public void clickCursorPos()
	{
		mMap.onCursorPosClicked();
	}

	// ____________________________________________________________________________________
	public boolean expectsDirection()
	{
		return mIsDPadActive;
	}

	// ____________________________________________________________________________________
	public boolean isDPadVisible()
	{
		return mDPad.isVisible();
	}

	// ____________________________________________________________________________________
	public void showControls()
	{
		mControlsVisible = true;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideControls()
	{
		mControlsVisible = false;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void showKeyboard()
	{
		mMode = CmdMode.Keyboard;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideKeyboard()
	{
		mMode = CmdMode.Panel;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void toggleKeyboard()
	{
		if(mMode == CmdMode.Panel)
			showKeyboard();
		else
			hideKeyboard();
	}

	// ____________________________________________________________________________________
	public void setMetaKeyboard()
	{
		mKeyboard.setMetaKeyboard();
	}

	// ____________________________________________________________________________________
	private void saveRegularKeyboard()
	{
		mRegularKeyboard = mKeyboard.getKeyboard();
	}

	// ____________________________________________________________________________________
	private void restoreRegularKeyboard()
	{
		if(mRegularKeyboard != null)
			mKeyboard.setKeyboard(mRegularKeyboard);
		mRegularKeyboard = null;
	}

	// ____________________________________________________________________________________
	public void setCtrlKeyboard()
	{
		mKeyboard.setCtrlKeyboard();
	}

	// ____________________________________________________________________________________
	private boolean isKeyboardMode()
	{
		return mMode == CmdMode.Keyboard && mControlsVisible;
	}

	// ____________________________________________________________________________________
	public void updateVisibleState()
	{
		if(mControlsVisible)
		{
			if(mMode == CmdMode.Panel)
			{
				mKeyboard.hide();
				if(isRolehackUIActive())
				{
					// The numpad answers direction prompts itself, including '.' on the
					// centre cell for the square you are standing on, so the classic
					// directional overlay never comes up over it.
					mDPad.forceHide();
					mCmdPanelLayout.hide();
					mRolehackUI.setSuppressed(false);
					mRolehackUI.setExpectsDirection(mIsDPadActive);
				}
				else if(mIsDPadActive)
				{
					mDPad.showDirectional(true);
					mCmdPanelLayout.hide();
				}
				else
				{
					mDPad.showDirectional(false);
					mCmdPanelLayout.show();
				}
			}
			else
			{
				mKeyboard.show();
				mCmdPanelLayout.hide();
				mDPad.forceHide();
				// The keyboard owns the bottom of the window; the overlay's controls
				// would ride up into it.
				if(mRolehackUI != null)
					mRolehackUI.setSuppressed(true);
			}
		}
		else
		{
			mCmdPanelLayout.hide();
			mKeyboard.hide();
			mDPad.forceHide();
			if(mRolehackUI != null)
				mRolehackUI.setSuppressed(true);
		}

		applyRolehackTopBand();
	}

	// ____________________________________________________________________________________
	// Rolehack mobile interface.  Lives above the map in map_frame, alongside the
	// classic command panels rather than in place of them, so the old layout is one
	// preference away at all times.
	private void createRolehackUI(Activity context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		RhTheme.loadPrefs(prefs);
		RhPrefs.load(prefs);

		try
		{
		mRolehackUI = RhOverlay.attach(context, R.id.map_frame, new RhOverlay.Host()
		{
			@Override
			public void sendCommand(String keySequence)
			{
				new Cmd.KeySequnece(NH_State.this, keySequence, "").execute(new Cmd.ExecuteFinishedHandler()
				{
					@Override
					public void onExecuteFinished()
					{
					}
				});
			}

			@Override
			public void openSettings()
			{
				startPreferences();
			}

			@Override
			public void toggleKeyboard()
			{
				NH_State.this.toggleKeyboard();
			}

			@Override
			public void mapAreaChanged()
			{
				applyMapArea();
			}
		});

		if(mRolehackUI != null)
		{
			mRolehackUI.statusUpdated(mRolehackStatus);
			mRolehackUI.onConfigurationChanged(context.getResources().getConfiguration());
		}
		}
		catch(Throwable t)
		{
			// Never let the new interface cost the player their controls.
			android.util.Log.e("Rolehack", "mobile interface failed to attach", t);
			mRolehackUI = null;
		}
	}

	// ____________________________________________________________________________________
	/**
	 * Whether the mobile interface, not the classic panels, is the control
	 * surface.  It stands down where it has no layout (RhOverlay.applyVisibility),
	 * so this must too -- asking only "is it switched on" once hid the classic
	 * panels in portrait on the next key press and left no controls at all.
	 */
	public boolean isRolehackUIActive()
	{
		return mRolehackUI != null && mRolehackUI.ownsControls();
	}

	// ____________________________________________________________________________________
	public boolean rolehackBackPressed()
	{
		return isRolehackUIActive() && mRolehackUI.onBackPressed();
	}

	// ____________________________________________________________________________________
	/**
	 * Rolehack: the mobile interface owns the top band.  The classic status rows
	 * stand down, and the message line is nudged clear of the new header and
	 * status panel until it gets its own designed panel.
	 */
	/** Rolehack: keep the interface's message line in step with ForkFront's log. */
	private void pushRolehackMessage()
	{
		if(mRolehackUI != null)
			mRolehackUI.setMessage(mMessage.getDisplayText(), mMessage.getOverflowCount());
	}

	private void applyRolehackTopBand()
	{
		boolean active = isRolehackUIActive() && mRolehackUI != null
				&& mRolehackUI.getVisibility() == android.view.View.VISIBLE;

		mStatus.setSuppressed(active);
		mMessage.setSuppressed(active);
		pushRolehackMessage();
		// ForkFront's "--N more--" used to stay up here, padded clear of the
		// header -- where it sat behind the interface's message panel.  It is
		// suppressed with the message view now, and the count rides in that panel.
	}

	// ____________________________________________________________________________________
	public void viewAreaChanged(Rect viewRect)
	{
		mViewRect.set(viewRect);
		applyMapArea();
	}

	/**
	 * Rolehack: the terminal style frames the map as a screen, so the map centres
	 * in the glass rather than in the whole view area.  The map view still covers
	 * the window; the interface's case hides everything outside the glass and
	 * takes every touch there.
	 */
	private final Rect mViewRect = new Rect();

	private void applyMapArea()
	{
		if(mMap == null || mViewRect.isEmpty())
			return;
		Rect glass = mRolehackUI != null ? mRolehackUI.mapArea() : null;
		mMap.viewAreaChanged(glass != null ? glass : mViewRect);
	}

	// ____________________________________________________________________________________
	public boolean isNumPadOn()
	{
		return mNumPad;
	}

	// ____________________________________________________________________________________
	public void startPreferences()
	{
		Intent prefsActivity = new Intent(mContext.getBaseContext(), Settings.class);
		mContext.startActivityForResult(prefsActivity, 42);
	}

	// ____________________________________________________________________________________
	private NH_Handler NhHandler = new NH_Handler() {
		@Override
		public void setLastUsername(String username) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
			prefs.edit().putString("lastUsername", username).commit();
		}

		// ____________________________________________________________________________________
		@Override
		public void setCursorPos(int wid, int x, int y) {
			NH_Window wnd = getWindow(wid);
			if(wnd != null)
				wnd.setCursorPos(x, y);
		}

		// ____________________________________________________________________________________
		@Override
		public void putString(int wid, int attr, String msg, int append, int color) {
			NH_Window wnd = getWindow(wid);
			if(wnd == null) {
				Log.print("[no wnd] " + msg);
				mMessage.printString(attr, msg, append, color);
			} else
				wnd.printString(attr, msg, append, color);
			pushRolehackMessage();
		}

		// ____________________________________________________________________________________
		@Override
		public void setHealthColor(int color) {
			if(mMap != null)
				mMap.setHealthColor(color);
		}

		// ____________________________________________________________________________________
		@Override
		public void rawPrint(int attr, String msg) {
			mMessage.printString(attr, msg, 0, -1);
		}

		// ____________________________________________________________________________________
		@Override
		public void printTile(int wid, int x, int y, int tile, int ch, int col, int special) {
			mMap.printTile(x, y, tile, ch, col, special);
		}

		// ____________________________________________________________________________________
		@Override
		public void ynFunction(String question, byte[] choices, int def) {
			mQuestion.show(mContext, question, choices, def);
		}

		// ____________________________________________________________________________________
		@Override
		public void getLine(String title, int nMaxChars, boolean showLog) {
			if(showLog)
				mGetLine.show(mContext, mMessage.getLogLine(2) + title, nMaxChars);
			else
				mGetLine.show(mContext, title, nMaxChars);
		}

		// ____________________________________________________________________________________
		@Override
		public void askName(int nMaxChars, String[] saves) {
			String last = getLastUsername();
			List<String> list = new ArrayList<>();
			for(String s : saves) {
				if(last.equals(s))
					list.add(0, s);
				else
					list.add(s);
			}
			mGetLine.showWhoAreYou(mContext, nMaxChars, list);
		}

		// ____________________________________________________________________________________
		@Override
		public void loadSound(String filename)
		{
			mSoundPlayer.load(filename);
		}

		@Override
		public void playSound(String filename, int volume)
		{
			mSoundPlayer.play(filename, volume);
		}

		// ____________________________________________________________________________________
		@Override
		public void createWindow(int wid, int type)
		{
			switch(type)
			{
			case 1: // #define NHW_MESSAGE 1
				mMessage.setId(wid);
				mWindows.add(mMessage);
			break;

			case 2: // #define NHW_STATUS 2
				mStatus.setId(wid);
				mWindows.add(mStatus);
			break;

			case 3: // #define NHW_MAP 3
				mMap.setId(wid);
				mWindows.add(mMap);
			break;

			case 4: // #define NHW_MENU 4
				mWindows.add(new NHW_Menu(wid, mContext, mIO, mTileset));
			break;

			case 5: // #define NHW_TEXT 5
				mWindows.add(new NHW_Text(wid, mContext, mIO));
			break;
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void displayWindow(final int wid, final int bBlocking)
		{
			NH_Window win = toFront(wid);
			if(win != null)
				win.show(bBlocking != 0);
		}

		// ____________________________________________________________________________________
		@Override
		public void clearWindow(final int wid, final int isRogueLevel)
		{
			NH_Window wnd = getWindow(wid);
			if(wnd != null)
			{
				wnd.clear();
				if(wnd == mMap)
					mMap.setRogueLevel(isRogueLevel != 0);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void destroyWindow(final int wid)
		{
			int i = getWindowI(wid);
			mWindows.get(i).destroy();
			mWindows.remove(i);
		}

		// ____________________________________________________________________________________
		@Override
		public void startMenu(final int wid)
		{
			((NHW_Menu)getWindow(wid)).startMenu();
		}

		// ____________________________________________________________________________________
		@Override
		public void addMenu(int wid, int tile, long id, int acc, int groupAcc, int attr, String text, int bSelected, int color)
		{
			((NHW_Menu)getWindow(wid)).addMenu(tile, id, acc, groupAcc, attr, text, bSelected, color);
		}

		// ____________________________________________________________________________________
		@Override
		public void endMenu(int wid, String prompt)
		{
			((NHW_Menu)getWindow(wid)).endMenu(prompt);
		}

		// ____________________________________________________________________________________
		@Override
		public void selectMenu(int wid, int how)
		{
			((NHW_Menu)toFront(wid)).selectMenu(MenuSelectMode.fromInt(how));
		}

		// ____________________________________________________________________________________
		@Override
		public void cliparound(int x, int y, int playerX, int playerY)
		{
			mMap.cliparound(x, y, playerX, playerY);
		}

		// ____________________________________________________________________________________
		@Override
		public void showLog(final int bBlocking)
		{
			mMessage.showLog(bBlocking != 0);
		}

		// ____________________________________________________________________________________
		@Override
		public void editOpts()
		{
		}

		// ____________________________________________________________________________________
		@Override
		public void lockMouse()
		{
			mIsMouseLocked = true;
		}

		// ____________________________________________________________________________________
		@Override
		public void showDPad()
		{
			mIsDPadActive = true;
			updateVisibleState();
		}

		// ____________________________________________________________________________________
		@Override
		public void hideDPad()
		{
			mIsDPadActive = false;
			updateVisibleState();
		}

		// ____________________________________________________________________________________
		@Override
		public void setNumPadOption(boolean numPadOn) {
			mNumPad = numPadOn;
			mDPad.updateNumPadState();
		}

		// ____________________________________________________________________________________
		@Override
		public void statusField(int idx, byte[] text, int colorOrMask)
		{
			mRolehackStatus.setField(idx, text, colorOrMask, mDecoder);
		}

		@Override
		public void hereContext(int flags, byte[] monsterName)
		{
			mRolehackStatus.setHere(flags, monsterName, mDecoder);
		}

		@Override
		public void heroLook(int[] look)
		{
			mMap.setHeroLook(look);
		}

		@Override
		public void setPlayerInfo(byte[] name, byte[] role, byte[] race, int flags)
		{
			mRolehackStatus.setPlayerInfo(name, role, race, flags, mDecoder);
		}

		@Override
		public void redrawStatus()
		{
			// The core has finished a status pass, so the fields are consistent now.
			if(mRolehackUI != null)
				mRolehackUI.statusUpdated(mRolehackStatus);
			mStatus.redraw();
		}
	};
}
