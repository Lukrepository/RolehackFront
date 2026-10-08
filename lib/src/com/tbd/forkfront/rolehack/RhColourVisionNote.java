package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tbd.forkfront.R;

/**
 * The Colour vision setting's long text, on a screen of its own (2026-10-08).
 * Settings shows at most four lines of a summary (the framework's preference
 * layout, maxLines 4, no ellipsis), which cut off what each mode changes and the
 * glyph: steps with their warning.  A plain text view at 16sp: the Credits'
 * text window is 11sp monospace, under the size the message band was raised
 * from for legibility.
 */
public class RhColourVisionNote extends Preference
{
	public RhColourVisionNote(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setPersistent(false);
		setSelectable(false);
	}

	@Override
	protected View onCreateView(ViewGroup parent)
	{
		TextView text = new TextView(getContext());
		int pad = Math.round(16 * getContext().getResources().getDisplayMetrics().density);
		text.setPadding(pad, pad, pad, pad);
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		text.setLineSpacing(0, 1.2f);
		// getText, not getString: it keeps the string's bold headings
		text.setText(getContext().getText(R.string.rhColourVisionNote));
		return text;
	}
}
