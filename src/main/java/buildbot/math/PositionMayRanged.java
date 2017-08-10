package main.java.buildbot.math;

import javax.annotation.concurrent.Immutable;

import net.minecraft.util.math.Vec3i;

@Immutable
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
	
	public PositionMayRanged add(Vec3i pos) {
		return add(pos.getX(), pos.getY(), pos.getZ());
	}

	public PositionMayRanged add(int x, int y, int z) {
		return new PositionMayRanged(this.x.add(x), this.y.add(y), this.z.add(z));
	}
}