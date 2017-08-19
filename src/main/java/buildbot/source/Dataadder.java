package main.java.buildbot.source;

import java.util.*;

import main.java.buildbot.Buildbot;
import main.java.buildbot.math.DoubleMayRanged;
import main.java.buildbot.math.PositionMayRanged;
import main.java.buildbot.math.Vec2i;
import main.java.buildbot.source.Forms;
import main.java.buildbot.source.StructDataUnit;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;

class Dataadder {
	static Map<BlockPos, Block> bringData(StructDataUnit data, PositionMayRanged pos) {
		Buildbot.LOGGER.info("options is:");
		data.options.forEach((k, v) -> Buildbot.LOGGER.info("  " + k + ": " + v));
		Map<BlockPos, Block> map = new HashMap<>();
		if (pos.getRangeLevel() != data.form.dimension) throw new IllegalStateException("Wrong ranged axis count");
		if (data.form == Forms.BLOCK) return Collections
				.singletonMap(new BlockPos(pos.x.value, pos.y.value, pos.z.value), data.currentBlock());
		if (data.form.type == Forms.Type.CUBIC) {
			int h = data.getOption("hollow", new Integer[0]) + 3 - data.form.dimension;
			int length;
			switch (data.form.dimension) {
				case 1:
					length = data.blocks.size();
					break;
				case 2:
					length = (int) Math.sqrt(data.blocks.size());
					break;
				case 3:
					length = (int) Math.cbrt(data.blocks.size());
					break;
				default:
					throw new InternalError();
			}

			Vec3i blockpatsize = new Vec3i(pos.x.ranged ? length : 1, pos.y.ranged ? length : 1,
				pos.z.ranged ? length : 1);
			Block[][][] blocks = new Block[blockpatsize.getX()][blockpatsize.getY()][blockpatsize.getZ()];

			for (int i = 0, z = 0; z < blocks.length; z++)
				for (int y = 0; y < blocks[0].length; y++)
					for (int x = 0; x < blocks[0][0].length; x++) {
						blocks[x][y][z] = data.blocks.get(i);
						i++;
					}

			for (int x = pos.x.min; x <= pos.x.max; x++) {
				boolean xedged = x == pos.x.min || x == pos.x.max;
				for (int y = pos.y.min; y <= pos.y.max; y++) {
					boolean yedged = y == pos.y.min || y == pos.y.max;
					for (int z = pos.z.min; z <= pos.z.max; z++) {
						int edgeLevel = xedged ? 1 : 0;
						if (yedged) edgeLevel++;
						if (z == pos.z.min || z == pos.z.max) edgeLevel++;
						if (edgeLevel >= h)
							map.put(new BlockPos(x, y, z), blocks[(x - pos.x.min) % blockpatsize.getX()][(y - pos.y.min)
									% blockpatsize.getY()][(z - pos.z.min) % blockpatsize.getZ()]);
					}
				}
			}
		} else if (data.form.type == Forms.Type.CIRCULER) {
			if (((boolean) data.getOption("fill")) && ((boolean) data.getOption("bold"))) throw new IllegalArgumentException("fill and bold can't assign both");
			int h = data.getOption("hollow", new Integer[0]);
			Axis axis = data.getOption("axis");
			DoubleMayRanged radiusrange = data.getOption("radius");
			if (pos.getRangeLevel() == 0 && radiusrange.ranged) throw new IllegalStateException("Circle radius can't have range");
			switch (axis) {
				case X:
					if (pos.y.ranged || pos.z.ranged) throw new IllegalStateException();
					for (int x = pos.x.min; x <= pos.x.max; x++) {
						double radius = radiusrange.getByRatio(((double) -(pos.x.min - x)) / (pos.x.max - pos.x.min));
						data.nextBlock();
						if (data.form.dimension >= h || x == pos.x.min || x == pos.x.max) {
							final Vec2i center = new Vec2i(pos.z.value, pos.y.value);
							if ((boolean) data.getOption("fill")) {
								List<Integer> ylist = michenerCircleFragment4(radius);
								for (int z = 0; z < ylist.size(); z++) {
									int y = ylist.get(z);
									for (int yl = y; yl >= -y; yl--) {
										map.put(new BlockPos(x, yl + center.y, z + center.x), data.currentBlock());
										map.put(new BlockPos(x, yl + center.y, -z + center.x), data.currentBlock());
									}
								}
							} else if ((boolean) data.getOption("bold")) {
								List<Integer> ylisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> ylisto = michenerCircleFragment4(radius + 0.5);
								for (int z = 0; z < ylisto.size(); z++) {
									int y = ylisto.get(z);
									if (z < ylisti.size()) {
										int yi = ylisti.get(z);
										for (int yl = y; yl >= yi; yl--) {
											map.put(new BlockPos(x, yl + center.y, z + center.x), data.currentBlock());
											map.put(new BlockPos(x, -yl + center.y, z + center.x), data.currentBlock());
											map.put(new BlockPos(x, yl + center.y, -z + center.x), data.currentBlock());
											map.put(new BlockPos(x, -yl + center.y, -z + center.x), data.currentBlock());
										}
									} else for (int yl = y; yl >= -y; yl--) {
										map.put(new BlockPos(x, yl + center.y, z + center.x), data.currentBlock());
										map.put(new BlockPos(x, yl + center.y, -z + center.x), data.currentBlock());
									}
								}
							} else {
								List<Integer> ylist = michenerCircleFragment8(radius);
								for (int z = 0; z < ylist.size(); z++) {
									int y = ylist.get(x);
									map.put(new BlockPos(x, y + center.y, z + center.x), data.currentBlock());
									map.put(new BlockPos(x, -z + center.y, -y + center.x), data.currentBlock());
									map.put(new BlockPos(x, z + center.y, -y + center.x), data.currentBlock());
									map.put(new BlockPos(x, -y + center.y, z + center.x), data.currentBlock());
									map.put(new BlockPos(x, y + center.y, -z + center.x), data.currentBlock());
									map.put(new BlockPos(x, -z + center.y, y + center.x), data.currentBlock());
									map.put(new BlockPos(x, z + center.y, y + center.x), data.currentBlock());
									map.put(new BlockPos(x, -y + center.y, -z + center.x), data.currentBlock());
								}
							}
						}
					}
					break;
				case Y:
					if (pos.x.ranged || pos.z.ranged) throw new IllegalStateException();
					for (int y = pos.y.min; y <= pos.y.max; y++) {
						double radius = radiusrange.getByRatio(((double) -(pos.y.min - y)) / (pos.y.max - pos.y.min));
						data.nextBlock();
						if (data.form.dimension >= h || y == pos.y.min || y == pos.y.max) {
							final Vec2i center = new Vec2i(pos.x.value, pos.z.value);
							if ((boolean) data.getOption("fill")) {
								List<Integer> zlist = michenerCircleFragment4(radius);
								for (int x = 0; x < zlist.size(); x++) {
									int z = zlist.get(x);
									for (int zl = z; zl >= -z; zl--) {
										map.put(new BlockPos(x + center.x, y, zl + center.y), data.currentBlock());
										map.put(new BlockPos(-x + center.x, y, zl + center.y), data.currentBlock());
									}
								}
							} else if ((boolean) data.getOption("bold")) {
								List<Integer> zlisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> zlisto = michenerCircleFragment4(radius + 0.5);
								for (int x = 0; x < zlisto.size(); x++) {
									int z = zlisto.get(x);
									if (x < zlisti.size()) {
										int zi = zlisti.get(x);
										for (int zl = z; zl >= zi; zl--) {
											map.put(new BlockPos(x + center.x, y, zl + center.y), data.currentBlock());
											map.put(new BlockPos(x + center.x, y, -zl + center.y), data.currentBlock());
											map.put(new BlockPos(-x + center.x, y, zl + center.y), data.currentBlock());
											map.put(new BlockPos(-x + center.x, y, -zl + center.y), data.currentBlock());
										}
									} else for (int zl = z; zl >= -z; zl--) {
										map.put(new BlockPos(x + center.x, y, zl + center.y), data.currentBlock());
										map.put(new BlockPos(-x + center.x, y, zl + center.y), data.currentBlock());
									}
								}
							} else {
								List<Integer> zlist = michenerCircleFragment8(radius);
								for (int x = 0; x < zlist.size(); x++) {
									int z = zlist.get(x);
									map.put(new BlockPos(x + center.x, y, z + center.y), data.currentBlock());
									map.put(new BlockPos(-z + center.x, y, -x + center.y), data.currentBlock());
									map.put(new BlockPos(-z + center.x, y, x + center.y), data.currentBlock());
									map.put(new BlockPos(x + center.x, y, -z + center.y), data.currentBlock());
									map.put(new BlockPos(-x + center.x, y, z + center.y), data.currentBlock());
									map.put(new BlockPos(z + center.x, y, -x + center.y), data.currentBlock());
									map.put(new BlockPos(z + center.x, y, x + center.y), data.currentBlock());
									map.put(new BlockPos(-x + center.x, y, -z + center.y), data.currentBlock());
								}
							}
						}
					}
					break;
				case Z:
					if (pos.x.ranged || pos.y.ranged) throw new IllegalStateException();
					for (int z = pos.z.min; z <= pos.z.max; z++) {
						double radius = radiusrange.getByRatio(((double) -(pos.z.min - z)) / (pos.z.max - pos.z.min));
						data.nextBlock();
						if (data.form.dimension >= h || z == pos.z.min || z == pos.z.max) {
							final Vec2i center = new Vec2i(pos.x.value, pos.y.value);
							if ((boolean) data.getOption("fill")) {
								List<Integer> ylist = michenerCircleFragment4(radius);
								for (int x = 0; x < ylist.size(); x++) {
									int y = ylist.get(x);
									for (int yl = y; yl >= -y; yl--) {
										map.put(new BlockPos(x + center.x, yl + center.y, z), data.currentBlock());
										map.put(new BlockPos(-x + center.x, yl + center.y, z), data.currentBlock());
									}
								}
							} else if ((boolean) data.getOption("bold")) {
								List<Integer> ylisti = michenerCircleFragment4(radius - 0.5);
								List<Integer> ylisto = michenerCircleFragment4(radius + 0.5);
								for (int x = 0; x < ylisto.size(); x++) {
									int y = ylisto.get(x);
									if (x < ylisti.size()) {
										int yi = ylisti.get(x);
										for (int yl = y; yl >= yi; yl--) {
											map.put(new BlockPos(x + center.x, yl + center.y, z), data.currentBlock());
											map.put(new BlockPos(x + center.x, -yl + center.y, z), data.currentBlock());
											map.put(new BlockPos(-x + center.x, yl + center.y, z), data.currentBlock());
											map.put(new BlockPos(-x + center.x, -yl + center.y, z), data.currentBlock());
										}
									} else for (int yl = y; yl >= -y; yl--) {
										map.put(new BlockPos(x + center.x, yl + center.y, z), data.currentBlock());
										map.put(new BlockPos(-x + center.x, yl + center.y, z), data.currentBlock());
									}
								}
							} else {
								List<Integer> ylist = michenerCircleFragment8(radius);
								for (int x = 0; x < ylist.size(); x++) {
									int y = ylist.get(x);
									map.put(new BlockPos(x + center.x, y + center.y, z), data.currentBlock());
									map.put(new BlockPos(-y + center.x, -x + center.y, z), data.currentBlock());
									map.put(new BlockPos(-y + center.x, x + center.y, z), data.currentBlock());
									map.put(new BlockPos(x + center.x, -y + center.y, z), data.currentBlock());
									map.put(new BlockPos(-x + center.x, y + center.y, z), data.currentBlock());
									map.put(new BlockPos(y + center.x, -x + center.y, z), data.currentBlock());
									map.put(new BlockPos(y + center.x, x + center.y, z), data.currentBlock());
									map.put(new BlockPos(-x + center.x, -y + center.y, z), data.currentBlock());
								}
							}
						}
					}
					break;
			}
		}
		return map;
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
