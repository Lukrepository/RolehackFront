package com.tbd.forkfront;

public interface NH_Handler
{
	void lockMouse();
	void setCursorPos(int wid, int x, int y);
	void putString(int wid, int attr, String msg, int append, int color);
	void setHealthColor(int color);
	void redrawStatus();

	/**
	 * Rolehack: one status field, apart from the formatted line.  idx is a BL_*
	 * constant; for BL_CONDITION the text is null and colorOrMask is the active
	 * condition bitmask rather than a colour.
	 */
	void statusField(int idx, byte[] text, int colorOrMask);

	/** Rolehack: name, role and race, which are not status fields. */
	void setPlayerInfo(byte[] name, byte[] role, byte[] race, int flags);

	/**
	 * Rolehack: what is underfoot and adjacent, which the interface cannot see for
	 * itself because the hero's glyph covers its own square.
	 */
	void hereContext(int flags, byte[] monsterName);
	void rawPrint(int attr, String msg);
	void printTile(int wid, int x, int y, int tile, int ch, int col, int special);
	void ynFunction(String question, byte[] choices, int def);
	void getLine(String msg, int nMaxChars, boolean b);
	void createWindow(int wid, int type);
	void displayWindow(int wid, int bBlocking);
	void clearWindow(int wid, int isRogueLevel);
	void destroyWindow(int wid);
	void startMenu(int wid);
	void addMenu(int wid, int tile, long id, int acc, int groupAcc, int attr, String msg, int bSelected, int color);
	void endMenu(int wid, String msg);
	void selectMenu(int wid, int how);
	void cliparound(int x, int y, int playerX, int playerY);
	void showDPad();
	void hideDPad();
	void showLog(int bBlocking);
	void editOpts();
	void setLastUsername(String s);
	void setNumPadOption(boolean b);
	void askName(int nMaxChars, String[] saves);
	void loadSound(String filename);
	void playSound(String filename, int volume);
}
