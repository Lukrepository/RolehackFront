package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Random;

/**
 * Key feedback (Lucas, 2026-09-25): a vibration, a mechanical keyboard's
 * click, both or neither, whenever one of the interface's keys goes down.
 *
 * The vibrations are the system's own keyboard effects, through
 * performHapticFeedback -- no permission, and they follow the phone's
 * touch-feedback setting.  They carry information as well as texture: a
 * firmer bump when a hold has done its work (the fan or drawer is up, no need
 * to look), and a detent as a flick crosses into one of OFFENSE's wedges.
 *
 * The click is a recorded key, CC0, from Freesound (assets/sounds/CREDITS.txt):
 * StavSounds' "Keyboard_Tactile_8", Lucas's pick of three he tried on the
 * phone.  It plays through a SoundPool at the game's media volume, with a small
 * pitch wobble so a run of presses does not sound machine-made.
 */
public final class RhFeedback
{
	private RhFeedback() {}

	public static final String KEY_MODE   = "rhFeedback";
	public static final String KEY_VOLUME = "rhClickVolume";

	private static boolean sVibrate = true;
	private static boolean sClick;
	private static final String SAMPLE = "sounds/key-tactile.ogg";
	private static float sVolume = 0.6f;

	private static SoundPool sPool;
	private static String sLoaded = "";
	private static int sSoundId;
	private static final Random sRandom = new Random();

	/** Read the settings; loads the chosen click, or lets it go when clicks are off. */
	public static void load(Context c, SharedPreferences prefs)
	{
		String mode = prefs.getString(KEY_MODE, "vibrate");
		sVibrate = "vibrate".equals(mode) || "both".equals(mode);
		sClick   = "click".equals(mode) || "both".equals(mode);
		try
		{
			sVolume = Math.max(0f, Math.min(1f, Integer.parseInt(prefs.getString(KEY_VOLUME, "60")) / 100f));
		}
		catch(NumberFormatException e)
		{
			sVolume = 0.6f;
		}
		if(sClick)
			loadClick(c.getApplicationContext());
		else
			releaseClick();
	}

	@SuppressWarnings("deprecation")
	private static void loadClick(Context c)
	{
		if(sPool == null)
		{
			if(Build.VERSION.SDK_INT >= 21)
				sPool = new SoundPool.Builder()
						.setMaxStreams(4)
						.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_GAME)
								.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
								.build())
						.build();
			else
				sPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
			sLoaded = "";
			sSoundId = 0;
		}
		if(SAMPLE.equals(sLoaded))
			return;
		if(sSoundId != 0)
			sPool.unload(sSoundId);
		sSoundId = 0;
		try
		{
			AssetFileDescriptor fd = c.getAssets().openFd(SAMPLE);
			sSoundId = sPool.load(fd, 1);
			fd.close();
			sLoaded = SAMPLE;
		}
		catch(java.io.IOException e)
		{
			// A missing sample leaves the keys silent, never broken.
			sLoaded = "";
		}
	}

	private static void releaseClick()
	{
		if(sPool != null)
			sPool.release();
		sPool = null;
		sLoaded = "";
		sSoundId = 0;
	}

	/** A key going down: the press under the thumb, and the click. */
	public static void press(View v)
	{
		if(sVibrate)
			v.performHapticFeedback(Build.VERSION.SDK_INT >= 27
					? HapticFeedbackConstants.KEYBOARD_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
		if(sClick && sPool != null && sSoundId != 0)
			sPool.play(sSoundId, sVolume, sVolume, 1, 0, 0.96f + sRandom.nextFloat() * 0.08f);
	}

	/** A key coming back up: the lighter half of the stroke, where the phone has one. */
	public static void up(View v)
	{
		if(sVibrate && Build.VERSION.SDK_INT >= 27)
			v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE);
	}

	/** A hold that has done its work: the fan, the drawer or the count row is up. */
	public static void held(View v)
	{
		if(sVibrate)
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
	}

	/** A flick crossing into a wedge: a detent under the thumb. */
	public static void detent(View v)
	{
		if(sVibrate)
			v.performHapticFeedback(Build.VERSION.SDK_INT >= 21
					? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.KEYBOARD_TAP);
	}
}
