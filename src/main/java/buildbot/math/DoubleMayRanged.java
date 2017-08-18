package main.java.buildbot.math;

import javax.annotation.concurrent.Immutable;

@Immutable
public class DoubleMayRanged {
	public final double value;
	public final double min;
	public final double max;
	public final boolean ranged;
	public final boolean reverse;

	public DoubleMayRanged(double i) {
		value = i;
		min = i;
		max = i;
		ranged = false;
		reverse = false;
	}

	public DoubleMayRanged(double a, double b) {
		value = a;
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
	
	public DoubleMayRanged add(double i) {
		return ranged ? new DoubleMayRanged(min + i, max + i) : new DoubleMayRanged(value + i);
	}
	
	public double getByRatio(double rate) {
		return ranged ? (reverse ? max * (1 - rate) + min * rate : min * (1 - rate) + max * rate) : value;
	}
	
	@Override
	public String toString() {
		return ranged ? min + " ~ " + max : String.valueOf(value);
	}
}
