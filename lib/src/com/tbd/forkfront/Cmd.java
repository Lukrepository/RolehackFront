package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.EnumSet;

import android.os.Handler;
import com.tbd.forkfront.Input.Modifier;

public interface Cmd
{
	interface ExecuteFinishedHandler {
		void onExecuteFinished();
	}
	void execute(ExecuteFinishedHandler handler);

	boolean hasLabel();

	String getCommand();

	String getLabel();

	// ____________________________________________________________________________________
	class ToggleKeyboard implements Cmd
	{
		private NH_State mState;
		private String mLabel = "";

		// ____________________________________________________________________________________
		public ToggleKeyboard(NH_State state, String label)
		{
			mState = state;
			mLabel = label;
		}

		// ____________________________________________________________________________________
		@Override
		public void execute(ExecuteFinishedHandler handler)
		{
			mState.showKeyboard();
			handler.onExecuteFinished();
		}

		// ____________________________________________________________________________________
		@Override
		public String toString()
		{
			if(mLabel.length() > 0)
				return mLabel;
			return "...";
		}

		// ____________________________________________________________________________________
		@Override
		public boolean hasLabel()
		{
			return mLabel.length() > 0;
		}

		// ____________________________________________________________________________________
		@Override
		public String getCommand()
		{
			return "...";
		}

		// ____________________________________________________________________________________
		@Override
		public String getLabel()
		{
			return mLabel;
		}
	}

	// ____________________________________________________________________________________
	class OpenMenu implements Cmd
	{
		private NH_State mState;
		private String mLabel = "";

		// ____________________________________________________________________________________
		public OpenMenu(NH_State state, String label)
		{
			mState = state;
			mLabel = label;
		}

		// ____________________________________________________________________________________
		@Override
		public void execute(ExecuteFinishedHandler handler)
		{
			mState.startPreferences();
			handler.onExecuteFinished();
		}

		// ____________________________________________________________________________________
		@Override
		public String toString()
		{
			if(mLabel.length() > 0)
				return mLabel;
			return "menu";
		}

		// ____________________________________________________________________________________
		@Override
		public boolean hasLabel()
		{
			return mLabel.length() > 0;
		}

		// ____________________________________________________________________________________
		@Override
		public String getCommand()
		{
			return "menu";
		}

		// ____________________________________________________________________________________
		@Override
		public String getLabel()
		{
			return mLabel;
		}
	}

	// ____________________________________________________________________________________
	class KeySequnece implements Cmd
	{
		private class KeyCmd
		{
			public KeyCmd(char ch, EnumSet<Modifier> mod)
			{
				this.ch = ch;
				this.mod = mod;
			}

			public EnumSet<Input.Modifier> mod;
			public char ch;
			public String ext;	// Rolehack: a command by name, sent whole
		}

		private NH_State mState;
		private String mLabel = "";
		private ArrayList<KeyCmd> mSeq = new ArrayList<KeyCmd>();
		private String mCommand;

		// ____________________________________________________________________________________
		public KeySequnece(NH_State state, String command, String label)
		{
			mState = state;
			mLabel = label;
			mCommand = command;
		}

		// ____________________________________________________________________________________
		public void setCommand(String command)
		{
			if(!mCommand.equals(command))
			{
				mCommand = command;
				mSeq.clear();
			}
		}

		// ____________________________________________________________________________________
		public void setLabel(String label)
		{
			mLabel = label;
		}

		// ____________________________________________________________________________________
		public String toString()
		{
			if(mLabel.length() > 0)
				return mLabel;
			return mCommand;
		}

		// ____________________________________________________________________________________
		@Override
		public void execute(final ExecuteFinishedHandler executeHandler)
		{
			if(mSeq.isEmpty())
				rebuildSequence();
			if(mSeq.isEmpty()) {
				executeHandler.onExecuteFinished();
				return;
			}

			// Handle response from NetHack for each key, before posting the next
			final Handler handler = mState.getHandler();
			@SuppressWarnings("unchecked")
			final ArrayList<KeyCmd> seq = (ArrayList<KeyCmd>)mSeq.clone();
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					char ch = seq.get(0).ch;
					EnumSet<Modifier> mod = seq.get(0).mod;
					String ext = seq.get(0).ext;
					if(ext != null)
					{
						Log.print("cmdpanel: #" + ext);
						mState.sendExtCmd(ext);
					}
					else
					{
						Log.print("cmdpanel: " + Character.toString(ch));
						mState.handleKeyDown(ch, Input.nhKeyFromMod(ch, mod), Input.toKeyCode(ch), mod, 0, true);
					}
					seq.remove(0);
					if(seq.size() > 0)
					{
						mState.waitReady();
						handler.post(this);
					}
					else
						executeHandler.onExecuteFinished();
				}
			});
		}

		// ____________________________________________________________________________________
		private void rebuildSequence()
		{
			mSeq.clear();
			for(int i = 0; i < mCommand.length(); i++)
			{
				EnumSet<Input.Modifier> mod = Input.modifiers();
				char ch = mCommand.charAt(i);
				// Rolehack: "#name" and a newline is a command by name.  Typed
				// into the '#' menu, its letters picked other commands; it goes
				// whole (NetHackIO.sendExtCmd) (Lucas, 2026-09-28).
				if(ch == '#')
				{
					int j = i + 1;
					while(j < mCommand.length() && Character.isLetterOrDigit(mCommand.charAt(j)))
						j++;
					int end = -1;
					if(j > i + 1 && j < mCommand.length() && mCommand.charAt(j) == '\n')
						end = j + 1;
					else if(j > i + 1 && mCommand.startsWith("\\n", j))
						end = j + 2;
					if(end > 0)
					{
						KeyCmd k = new KeyCmd(ch, mod);
						k.ext = mCommand.substring(i + 1, j);
						mSeq.add(k);
						i = end - 1;
						continue;
					}
				}
				if(ch == '^' && mCommand.length() - i >= 2)
				{
					char n = mCommand.charAt(i + 1);
					if(n != ' ')
					{
						mod.add(Input.Modifier.Control);
						ch = n;
						i++;
					}
				}
				else if(ch == 'M' && mCommand.length() - i >= 3)
				{
					char n = mCommand.charAt(i + 2);
					if(n != ' ' && mCommand.charAt(i + 1) == '-')
					{
						mod.add(Input.Modifier.Meta);
						ch = n;
						i += 2;
					}
				}
				else if(ch == '\\' && mCommand.length() - i >= 2)
				{
					char n = mCommand.charAt(i + 1);
					if(n == 'e')
					{
						ch = '\033';
						i++;
					}
					else if(n == 'n')
					{
						ch = '\n';
						i++;
					}
					else if(n == 'b')
					{
						ch = (char)0x7f;
						i++;
					}
				}
				mSeq.add(new KeyCmd(ch, mod));
			}
		}

		// ____________________________________________________________________________________
		@Override
		public boolean hasLabel()
		{
			return mLabel.length() > 0;
		}

		// ____________________________________________________________________________________
		@Override
		public String getCommand()
		{
			return mCommand;
		}

		// ____________________________________________________________________________________
		@Override
		public String getLabel()
		{
			return mLabel;
		}

	}
}
