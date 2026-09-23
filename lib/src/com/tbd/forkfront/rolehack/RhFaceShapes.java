package com.tbd.forkfront.rolehack;

/**
 * The hub silhouettes, as unit coordinates (x,y pairs in 0..1) so one set of
 * numbers serves every size.
 *
 * Shape is the identity: once colour alone stops distinguishing five hubs, each
 * gets its own outline. These are the handoff's clip-path polygons transcribed.
 */
public final class RhFaceShapes
{
	private RhFaceShapes() {}

	/** polygon(0% 0%, 72% 0%, 100% 50%, 72% 100%, 0% 100%) */
	public static final float[] CHEVRON = {
		0f, 0f,  0.72f, 0f,  1f, 0.5f,  0.72f, 1f,  0f, 1f
	};

	/** polygon(50% 0%, 100% 100%, 0% 100%) */
	public static final float[] TRIANGLE = {
		0.5f, 0f,  1f, 1f,  0f, 1f
	};

	/**
	 * A star of `points` points, radii alternating 1.0 and innerRatio, first
	 * vertex at -90 degrees.
	 */
	public static float[] star(int points, float innerRatio)
	{
		float[] out = new float[points * 4];
		for(int i = 0; i < points * 2; i++)
		{
			double a = Math.toRadians(-90 + i * 180.0 / points);
			float r = (i % 2 == 1 ? innerRatio : 1f) * 0.5f;
			out[i * 2]     = 0.5f + (float)Math.cos(a) * r;
			out[i * 2 + 1] = 0.5f + (float)Math.sin(a) * r;
		}
		return out;
	}

	/** ATTACK: 12 points, radii alternating 1.0 / 0.8 — 24 vertices. */
	public static final float[] STAR12 = star(12, 0.8f);
}
