package main.java.buildbot.math;

import javax.annotation.concurrent.Immutable;

@Immutable
public class IntegerMayRanged {
	public final int value;
	public final int min;
	public final int max;
	public final boolean ranged;
	public final boolean reverse;

	public IntegerMayRanged(int i) {
		value = i;
		min = i;
		max = i;
		ranged = false;
		reverse = false;
	}

	public IntegerMayRanged(int a, int b) {
		this.value = a;
		if (a > b) {
			min = b;
			max = a;
			reverse = true;
		} else {
			min = a;
			max = b;
			reverse = false;
		}
		ranged = a != b;
	}
	
	public IntegerMayRanged add(int i) {
		return ranged ? new IntegerMayRanged(min + i, max + i) : new IntegerMayRanged(value + i);
	}
	
	public static IntegerMayRanged add(IntegerMayRanged ranged, int not) {
		return ranged.add(not);
	}
	
	public static IntegerMayRanged add(int not, IntegerMayRanged ranged) {
		return ranged.add(not);
	}
	
	@Override
	public String toString() {
		return ranged ? min + " ~ " + max : String.valueOf(value);
	}
}
