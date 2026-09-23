package com.tbd.forkfront.rolehack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * The header band: identity and display state only, no commands.
 *
 * Left to right -- character name, role line, a flexible gap, the six
 * attributes, and the tiles/ASCII toggle at the right edge (which the overlay
 * owns as a real control; this view leaves room for it).
 *
 * Nothing here is interactive, which is the point: the top edge is the one part
 * of the screen a thumb never has to reach mid-fight.
 */
public class RhHeader extends View
{
	private static final float PAD_H = 12f;
	private static final float GAP = 14f;
	/** Room kept clear at the right for the tiles/ASCII toggle. */
	private static final float TOGGLE_RESERVE = 66f;

	private static final String[] ATTR_LABEL = { "St", "Dx", "Co", "In", "Wi", "Ch" };
	private static final int[] ATTR_FIELD = {
		RhStatus.BL_STR, RhStatus.BL_DX, RhStatus.BL_CO,
		RhStatus.BL_IN, RhStatus.BL_WI, RhStatus.BL_CH,
	};

	private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mRule = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

	private RhStatus mStatus;

	public RhHeader(Context context)
	{
		super(context);
		mRule.setColor(RhTheme.HEADER_RULE);
		mRule.setStrokeWidth(RhTheme.rawDp(context, RhTheme.HEADER_RULE_W));
		mText.setTextAlign(Paint.Align.LEFT);
	}

	public void setStatus(RhStatus status)
	{
		mStatus = status;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		int w = getWidth(), h = getHeight();

		mFill.setShader(new LinearGradient(0, h, 0, 0,
				RhTheme.HEADER_BAND[0], RhTheme.HEADER_BAND[1], Shader.TileMode.CLAMP));
		canvas.drawRect(0, 0, w, h, mFill);
		float rw = mRule.getStrokeWidth();
		canvas.drawLine(0, h - rw / 2f, w, h - rw / 2f, mRule);

		if(mStatus == null)
			return;

		float pad = RhTheme.rawDp(getContext(), PAD_H);
		float gap = RhTheme.rawDp(getContext(), GAP);
		float cy = h / 2f;
		float x = pad;

		// Name.
		mText.setTypeface(RhTheme.monoBold(getContext()));
		mText.setTextSize(RhTheme.rawDp(getContext(), 13f));
		mText.setColor(RhTheme.TEXT);
		String name = mStatus.name();
		if(name.length() > 0)
		{
			canvas.drawText(name, x, baseline(cy), mText);
			x += mText.measureText(name) + gap;
		}

		// Role line.
		mText.setTypeface(RhTheme.monoRegular(getContext()));
		mText.setTextSize(RhTheme.rawDp(getContext(), 10f));
		mText.setColor(RhTheme.LABEL_MUTED);
		String role = mStatus.roleLine();
		if(role.length() > 0)
			canvas.drawText(role, x, baseline(cy), mText);

		// Attributes, right-aligned before the toggle's reserved space.
		mText.setTypeface(RhTheme.monoRegular(getContext()));
		mText.setTextSize(RhTheme.rawDp(getContext(), 11f));
		float attrGap = RhTheme.rawDp(getContext(), 10f);

		float total = 0f;
		String[] labels = new String[ATTR_FIELD.length];
		String[] values = new String[ATTR_FIELD.length];
		int shown = 0;
		for(int i = 0; i < ATTR_FIELD.length; i++)
		{
			// The core sends these already labelled ("St:14"), so take the bare
			// value and pair it with our own label rather than doubling it up.
			String v = mStatus.bare(ATTR_FIELD[i]);
			if(v == null || v.length() == 0)
				continue;
			labels[shown] = ATTR_LABEL[i];
			values[shown] = v;
			total += mText.measureText(labels[shown]) + mText.measureText(values[shown]);
			shown++;
		}
		if(shown == 0)
			return;
		total += attrGap * (shown - 1);

		float ax = w - pad - RhTheme.rawDp(getContext(), TOGGLE_RESERVE) - total;
		for(int i = 0; i < shown; i++)
		{
			mText.setColor(RhTheme.VALUE_SECONDARY);
			canvas.drawText(labels[i], ax, baseline(cy), mText);
			ax += mText.measureText(labels[i]);
			mText.setColor(RhTheme.TEXT);
			canvas.drawText(values[i], ax, baseline(cy), mText);
			ax += mText.measureText(values[i]) + attrGap;
		}
	}

	private float baseline(float cy)
	{
		Paint.FontMetrics fm = mText.getFontMetrics();
		return cy - (fm.ascent + fm.descent) / 2f;
	}
}
