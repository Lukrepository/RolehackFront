package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class NHW_Message implements NH_Window
{
	protected static final int SHOW_MAX_LINES = 3;
	/**
	 * Rolehack: an attribute bit from the core (winandroid.c RH_LOG_ONLY) for a
	 * message that goes to the log but not the band -- the rest of a turn skipped
	 * with Esc at --More--, or history restored with a saved game.
	 */
	public static final int ATTR_LOG_ONLY = 1 << 30;
	private static final int PAGE_MAX = 50;

	private NetHackIO mIO;
	private Activity mContext;
	private final int MaxLog = 256;
	private String[] mLog = new String[MaxLog];
	private int mCurrentIdx;
	private int mLogCount;
	private int mDispCount;
	private UI mUI;
	private NHW_Text mLogView;
	private boolean mIsVisible;
	/** Rolehack: the mobile interface draws its own message line. */
	private boolean mSuppressed;
	private int mWid;
	private int mOpacity;
	/**
	 * Rolehack: the message band's page -- the messages since the core last
	 * cleared the window, which it does at the start of each page -- and the
	 * page before it, which the band shows dimmed until a new one starts.
	 */
	private ArrayList<String> mPage = new ArrayList<>();
	private ArrayList<String> mOldPage = new ArrayList<>();
	private int mScroll;
	private boolean mMorePrompt;

	// ____________________________________________________________________________________
	public NHW_Message(Activity context, NetHackIO io)
	{
		mIO = io;
		setContext(context);
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return "NHW_Message";
	}

	// ____________________________________________________________________________________
	@Override
	public void setContext(Activity context)
	{
		if(mContext == context)
			return;
		mContext = context;
		mUI = new UI();
		if(mIsVisible)
			mUI.showInternal();
		else
			mUI.hideInternal();
		if(mLogView != null)
			mLogView.setContext(context);
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput)
	{
		KeyEventResult ret;
		if(isLogShowing() && (ret = mLogView.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput)) != KeyEventResult.IGNORED)
			return ret;
		return mUI.handleKeyDown(ch) ? KeyEventResult.HANDLED : KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		mDispCount = 0;
		if(!mPage.isEmpty())
		{
			mOldPage = mPage;
			mPage = new ArrayList<>();
		}
		mScroll = 0;
		mUI.clear();
	}

	// ____________________________________________________________________________________
	private int getIndex(int i)
	{
		if(mLogCount == 0)
			return 0;
		return i & (MaxLog - 1);
	}

	// ____________________________________________________________________________________
	@Override
	public void printString(int attr, String str, int append, int color)
	{
		mCurrentIdx = getIndex(mLogCount - 1);
		boolean logOnly = (attr & ATTR_LOG_ONLY) != 0;
		if(!logOnly)
		{
			// An answer is appended to its question after the key that gave it,
			// which has already dimmed the page: it goes where the question is.
			ArrayList<String> onto = !mPage.isEmpty() ? mPage : mOldPage;
			if(append < 0 && !onto.isEmpty())
			{
				String l = onto.get(onto.size() - 1);
				int cut = Math.max(0, l.length() + append + 1);
				onto.set(onto.size() - 1, l.substring(0, Math.min(cut, l.length())) + str);
			}
			else if(append > 0 && !onto.isEmpty())
				onto.set(onto.size() - 1, onto.get(onto.size() - 1) + str);
			else if(append == 0)
			{
				mPage.add(str);
				if(mPage.size() > PAGE_MAX)
					mPage.remove(0);
			}
		}
		else if(append == 0)
		{
			// into the log, but neither the band nor the classic line
			mCurrentIdx = getIndex(mCurrentIdx + 1);
			mLog[mCurrentIdx] = str;
			mLogCount++;
			return;
		}

		if( append < 0 && mLogCount > 0 ) {
			append++;
			String l = mLog[mCurrentIdx];
			if( append < -l.length() )
				append = -l.length();
			l = l.substring(0, l.length() + append);
			mLog[mCurrentIdx] = l + str;
		} else if( append > 0 && mLogCount > 0 ) {
			if( str.length() > 0 )
				mLog[mCurrentIdx] = mLog[mCurrentIdx] + str;
		} else {
			addMessage(str);
		}
		mUI.update();
	}

	// ____________________________________________________________________________________
	/**
	 * Rolehack: the lines the message view is currently showing, for an interface
	 * that draws its own.  Same selection updateText() makes, so the two can never
	 * disagree about what the player was told.
	 */
	public String getDisplayText()
	{
		if(mDispCount <= 0)
			return "";
		StringBuilder sb = new StringBuilder();
		int lineCount = Math.min(SHOW_MAX_LINES, mDispCount);
		int iStart = mCurrentIdx - lineCount + 1;
		for(int i = 0; i < lineCount; i++)
		{
			if(i > 0)
				sb.append('\n');
			sb.append(mLog[getIndex(iStart + i)]);
		}
		return sb.toString();
	}

	// ____________________________________________________________________________________
	/** Rolehack: the band's page, the page before it, its scroll, --More--. */
	public List<String> bandPage()  { return new ArrayList<>(mPage); }
	public List<String> bandOld()   { return new ArrayList<>(mOldPage); }
	public int bandScroll()         { return mScroll; }
	public boolean bandMore()       { return mMorePrompt; }

	public void setMorePrompt(boolean on) { mMorePrompt = on; }
	public void setScroll(int row)        { mScroll = Math.max(0, row); }

	// ____________________________________________________________________________________
	public void setSuppressed(boolean suppressed)
	{
		if(mSuppressed == suppressed)
			return;
		mSuppressed = suppressed;
		mUI.applySuppressed();
	}

	// ____________________________________________________________________________________
	/** Rolehack: how many of this turn's messages fell out of the lines shown. */
	public int getOverflowCount()
	{
		return Math.max(0, mDispCount - SHOW_MAX_LINES);
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos( int x, int y )
	{
	}

	// ____________________________________________________________________________________
	private void addMessage(String newMsg)
	{
		mCurrentIdx = getIndex(mCurrentIdx + 1);
		mLog[mCurrentIdx] = newMsg;
		mDispCount++;
		mLogCount++;
	}

	// ____________________________________________________________________________________
	public String getLogLine( int maxLineCount ) {
		if(mDispCount <= 0)
			return "";

		int nLines = Math.min(mDispCount, maxLineCount);

		StringBuilder line = new StringBuilder();
		for( int i = nLines - 1; i >= 0; i-- ) {
			int idx = getIndex(mCurrentIdx - i);
			line.append(mLog[idx]);
			line.append(' ');
		}
		line.append('\n');
		return line.toString();
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		mUI.showInternal();
		if(bBlocking)
		{
			// unblock immediately
			mIO.sendKeyCmd(' ');
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		mIsVisible = false;
		mUI.hideInternal();
	}

	// ____________________________________________________________________________________
	public void showLog(boolean bBlocking)
	{
		if(mLogView == null)
			mLogView = new NHW_Text(0, mContext, mIO);

		// Rolehack: the band's page -- or the one it shows dimmed -- in bold at the
		// log's end, so the reader sees where now is
		int highlight = !mPage.isEmpty() ? mPage.size() : mOldPage.size();

		int nLogs = 0;
		for( int n = 0; n < MaxLog; n++ ) {
			if( mLog[n] != null )
				nLogs++;
		}

		int attr = 0;
		mLogView.clear();
		int i = mCurrentIdx + 1;
		for(int n = 0; n < MaxLog; n++, i++)
		{
			String s = mLog[getIndex(i)];
			if(s != null)
			{
				nLogs--;
				if( nLogs < highlight )
					attr = TextAttr.ATTR_BOLD;
				mLogView.printString(attr, s, 0, 0xffffffff);
			}
		}
		mLogView.show(bBlocking);
		mLogView.scrollToEnd();
		clear();
		mUI.update();
	}

	// ____________________________________________________________________________________
	private boolean isLogShowing()
	{
		return mLogView != null && mLogView.isVisible();
	}

	// ____________________________________________________________________________________
	public void setId(int wid)
	{
		mWid = wid;
	}

	// ____________________________________________________________________________________
	@Override
	public int id()
	{
		return mWid;
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs)
	{
		mOpacity = prefs.getInt("statusOpacity", 0);
		mUI.updateOpacity();
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	private class UI
	{
		private TextView m_view;
		private TextView m_more;

		// ____________________________________________________________________________________
		public UI()
		{
			m_view = (TextView)mContext.findViewById(R.id.nh_message);
			m_more = (TextView)mContext.findViewById(R.id.more);
			m_more.setVisibility(View.GONE);
			m_more.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					showLog(false);
				}
			});
			updateOpacity();
		}

		// ____________________________________________________________________________________
		public boolean isMoreVisible()
		{
			return m_more.getVisibility() == View.VISIBLE;
		}

		// ____________________________________________________________________________________
		public void showInternal()
		{
			update();
			// Rolehack: the core shows this window with every message, so the
			// suppression has to hold here, not only when it is first set --
			// otherwise the classic line comes back under the interface's own.
			m_view.setVisibility(mSuppressed ? View.GONE : View.VISIBLE);
		}

		// ____________________________________________________________________________________
		public void hideInternal()
		{
			//	m_view.setVisibility(View.INVISIBLE);
			//	m_more.setVisibility(View.GONE);
		}

		// ____________________________________________________________________________________
		public void clear()
		{
			m_more.setVisibility(View.GONE);
			m_view.setText("");
		}

		// ____________________________________________________________________________________
		public void applySuppressed()
		{
			m_view.setVisibility(mSuppressed ? View.GONE : View.VISIBLE);
			update(); // shows or hides "--N more--" to match
		}

		public void update()
		{
			updateText();
			// Rolehack: while suppressed the count is shown in the interface's
			// message panel instead (getOverflowCount()).
			if( mDispCount > SHOW_MAX_LINES && !mSuppressed ) {
				m_more.setText("--" + Integer.toString(mDispCount - SHOW_MAX_LINES) + " more--");
				m_more.setVisibility(View.VISIBLE);
			} else
				m_more.setVisibility(View.GONE);
		}

		// ____________________________________________________________________________________
		public boolean handleKeyDown(char ch)
		{
			if(isMoreVisible() && ch == ' ' && !isLogShowing()) {
				showLog(false);
				return true;
			}
			return false;
		}

		// ____________________________________________________________________________________
		private void updateText()
		{
			m_view.setText("");
			if( mDispCount > 0 ) {
				int lineCount = Math.min(SHOW_MAX_LINES, mDispCount);
				int iStart = mCurrentIdx - lineCount + 1;
				for( int i = 0; i < lineCount; i++ ) {
					String msg = mLog[getIndex(iStart + i)];
					if( i > 0 )
						m_view.append("\n");
					m_view.append(msg);
				}
			}
		}

		// ____________________________________________________________________________________
		public void updateOpacity()
		{
			m_view.setBackgroundColor(mOpacity << 24);
		}
	}
}
