package main.java.buildbot.math;

public class PositionMayRanged {
	public final IntegerMayRanged x;
	public final IntegerMayRanged y;
	public final IntegerMayRanged z;
	private int rangeLevel = -1;

	public PositionMayRanged(IntegerMayRanged x, IntegerMayRanged y, IntegerMayRanged z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public int getRangeLevel() {
		if (rangeLevel == -1) {
			rangeLevel++;
			if (x.ranged) rangeLevel++;
			if (y.ranged) rangeLevel++;
			if (z.ranged) rangeLevel++;
		}
		return rangeLevel;
	}
}