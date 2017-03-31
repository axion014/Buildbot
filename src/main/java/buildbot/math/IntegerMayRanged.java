package main.java.buildbot.math;

public class IntegerMayRanged {
	public final int value;
	public final int min;
	public final int max;
	public final boolean ranged;

	public IntegerMayRanged(int i) {
		this.value = i;
		this.min = i;
		this.max = i;
		this.ranged = false;
	}

	public IntegerMayRanged(int a, int b) {
		this.value = 0; // do not use
		if (a > b) {
			this.min = b;
			this.max = a;
		} else {
			this.min = a;
			this.max = b;
		}
		this.ranged = true;
	}
}
