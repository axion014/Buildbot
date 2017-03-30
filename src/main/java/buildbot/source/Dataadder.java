package main.java.buildbot.source;

import java.util.*;

import main.java.buildbot.PlaceData;
import main.java.buildbot.math.PositionMayRanged;
import main.java.buildbot.math.Vec2i;
import main.java.buildbot.source.Forms;
import main.java.buildbot.source.StructDataUnit;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

class Dataadder {
	static List<PlaceData> bringData(StructDataUnit data, PositionMayRanged pos) {
		List<PlaceData> set = new LinkedList<>();
		if (pos.getRangeLevel() != data.form.dimension) throw new IllegalStateException("Wrong ranged axis count");
		if (data.form.type == Forms.Type.CUBIC) {
			if (data.form.dimension == 0) return Collections
					.singletonList(new PlaceData(new BlockPos(pos.x.value, pos.y.value, pos.z.value), data.block));
			int h = data.getOption("hollow", new Integer[0]) + 3 - data.form.dimension;

			for (int x = pos.x.min; x <= pos.x.max; x++) {
				boolean xedged = x == pos.x.min || x == pos.x.max;
				for (int y = pos.y.min; y <= pos.y.max; y++) {
					boolean yedged = y == pos.y.min || y == pos.y.max;
					for (int z = pos.z.min; z <= pos.z.max; z++) {
						int edgeLevel = xedged ? 1 : 0;
						if (yedged) edgeLevel++;
						if (z == pos.z.min || z == pos.z.max) edgeLevel++;
						if (edgeLevel >= h) set.add(new PlaceData(x, y, z, data.block));
					}
				}
			}
		} else if (data.form.type == Forms.Type.CIRCULER) {
			int h = data.getOption("hollow", new Integer[0]);
			Axis axis = data.getOption("axis");
			double radius = data.getOption("radius");
			switch (axis) {
				case X:
					if (pos.y.ranged || pos.z.ranged) throw new IllegalStateException();
					for (int x = pos.x.min; x <= pos.x.max; x++)
						if (data.form.dimension >= h || x == pos.x.min || x == pos.x.max) {
							final Vec2i center = new Vec2i(pos.z.value, pos.y.value);
							if (!(boolean) data.getOption("bold")) {
								List<Integer> ylist = michenerCircleFragment8(radius);
								for (int z = 0; z < ylist.size(); z++) {
									int y = ylist.get(x);
									set.add(new PlaceData(x, y + center.y, z + center.x, data.block));
									set.add(new PlaceData(x, -z + center.y, -y + center.x, data.block));
									set.add(new PlaceData(x, z + center.y, -y + center.x, data.block));
									set.add(new PlaceData(x, -y + center.y, z + center.x, data.block));
									set.add(new PlaceData(x, y + center.y, -z + center.x, data.block));
									set.add(new PlaceData(x, -z + center.y, y + center.x, data.block));
									set.add(new PlaceData(x, z + center.y, y + center.x, data.block));
									set.add(new PlaceData(x, -y + center.y, -z + center.x, data.block));
								}
							} else {
								List<Integer> ylisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> ylisto = michenerCircleFragment4(radius + 0.5);
								for (int z = 0; z < ylisto.size(); z++) {
									int y = ylisto.get(z);
									if (z < ylisti.size()) {
										int yi = ylisti.get(z);
										for (int yl = y; yl >= yi; y--) {
											set.add(new PlaceData(x, yl + center.y, z + center.x, data.block));
											set.add(new PlaceData(x, -yl + center.y, z + center.x, data.block));
										}
									} else for (int yl = y; yl >= -y; y--)
										set.add(new PlaceData(x, yl + center.y, z + center.x, data.block));
								}
							}
						}
					break;
				case Y:
					if (pos.x.ranged || pos.z.ranged) throw new IllegalStateException();
					for (int y = pos.y.min; y <= pos.y.max; y++)
						if (data.form.dimension >= h || y == pos.y.min || y == pos.y.max) {
							final Vec2i center = new Vec2i(pos.x.value, pos.z.value);
							if (!(boolean) data.getOption("bold")) {
								List<Integer> zlist = michenerCircleFragment8(radius);
								for (int x = 0; x < zlist.size(); x++) {
									int z = zlist.get(x);
									set.add(new PlaceData(x + center.x, y, z + center.y, data.block));
									set.add(new PlaceData(-z + center.x, y, -x + center.y, data.block));
									set.add(new PlaceData(-z + center.x, y, x + center.y, data.block));
									set.add(new PlaceData(x + center.x, y, -z + center.y, data.block));
									set.add(new PlaceData(-x + center.x, y, z + center.y, data.block));
									set.add(new PlaceData(z + center.x, y, -x + center.y, data.block));
									set.add(new PlaceData(z + center.x, y, x + center.y, data.block));
									set.add(new PlaceData(-x + center.x, y, -z + center.y, data.block));
								}
							} else {
								List<Integer> zlisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> zlisto = michenerCircleFragment4(radius + 0.5);
								for (int x = 0; x < zlisto.size(); x++) {
									int z = zlisto.get(x);
									if (x < zlisti.size()) {
										int zi = zlisti.get(x);
										for (int zl = z; zl >= zi; z--) {
											set.add(new PlaceData(x + center.x, y, zl + center.y, data.block));
											set.add(new PlaceData(x + center.x, y, -zl + center.y, data.block));
										}
									} else for (int zl = z; zl >= -z; z--)
										set.add(new PlaceData(x + center.x, y, zl + center.y, data.block));
								}
							}
						}
					break;
				case Z:
					if (pos.x.ranged || pos.y.ranged) throw new IllegalStateException();
					for (int z = pos.z.min; z <= pos.z.max; z++)
						if (data.form.dimension >= h || z == pos.z.min || z == pos.z.max) {
							final Vec2i center = new Vec2i(pos.x.value, pos.y.value);
							if (!(boolean) data.getOption("bold")) {
								List<Integer> ylist = michenerCircleFragment8(radius);
								for (int x = 0; x < ylist.size(); x++) {
									int y = ylist.get(x);
									set.add(new PlaceData(x + center.x, y + center.y, z, data.block));
									set.add(new PlaceData(-y + center.x, -x + center.y, z, data.block));
									set.add(new PlaceData(-y + center.x, x + center.y, z, data.block));
									set.add(new PlaceData(x + center.x, -y + center.y, z, data.block));
									set.add(new PlaceData(-x + center.x, y + center.y, z, data.block));
									set.add(new PlaceData(y + center.x, -x + center.y, z, data.block));
									set.add(new PlaceData(y + center.x, x + center.y, z, data.block));
									set.add(new PlaceData(-x + center.x, -y + center.y, z, data.block));
								}
							} else {
								List<Integer> ylisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> ylisto = michenerCircleFragment4(radius + 0.5);
								for (int x = 0; x < ylisto.size(); x++) {
									int y = ylisto.get(x);
									if (x < ylisti.size()) {
										int yi = ylisti.get(x);
										for (int yl = y; yl >= yi; y--) {
											set.add(new PlaceData(x + center.x, yl + center.y, z, data.block));
											set.add(new PlaceData(x + center.x, -yl + center.y, z, data.block));
										}
									} else for (int yl = y; yl >= -y; y--)
										set.add(new PlaceData(x + center.x, yl + center.y, z, data.block));
								}
							}
						}
					break;
			}
		}
		return set;
	}

	private static List<Integer> michenerCircleFragment8(double radius) {
		Vec2i current = new Vec2i(0, MathHelper.fastFloor(radius + 0.5));
		List<Integer> list = new ArrayList<>();
		double d = 3 - 2 * radius;
		while (current.x <= current.y) {
			list.add(current.y);
			if (d < 0) {
				current = new Vec2i(current.x + 1, current.y);
				d += current.x * 4 + 6;
			} else {
				current = new Vec2i(current.x + 1, current.y - 1);
				d += (current.x - current.y) * 4 + 10;
			}
		}
		return list;
	}

	private static List<Integer> michenerCircleFragment4(double radius) {
		List<Integer> list = michenerCircleFragment8(radius);
		for (int i = list.size() - 1; i >= 0; i--)
			if (list.get(i) == list.size()) list.add(list.get(i), i);
		return list;
	}
}
