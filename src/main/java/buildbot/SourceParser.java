package main.java.buildbot;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.pattern.CharPredicates;
import org.jparsec.pattern.Patterns;

import main.java.buildbot.math.Vec2i;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

class SourceParser {
	private static final String PLACE_OP = "at";
	private static Parser<Integer> integer = Parsers.sequence(Scanners.isChar('-').optional(null).source(),
		Scanners.DEC_INTEGER.or(Scanners.isChar('0').source()), (a, b) -> Integer.valueOf(a + b));
	private static Parser<Double> decimal;
	@SuppressWarnings("unused")
	private static Parser<Integer> intspace;
	private static Parser<IntegerMayRanged> intmayrange;
	private static Parser<IntegerMayRanged> intmayrangespace;
	private static Parser<PositionMayRanged> position;
	private static Parser<Block> block;
	private static Parser<List<PlaceData>> parser;
	private static Parser<Forms> form;
	private static Parser<StructDataUnit> pattern;

	private SourceParser() {}

	static {
		decimal = Parsers.sequence(Scanners.isChar('-').optional(null).source(),
			Scanners.DEC_INTEGER.or(Scanners.isChar('0').source()),
			Patterns.FRACTION.toScanner("").optional(null).source(), (a, b, c) -> Double.valueOf(a + b + c));
		intspace = ignoresideoption(Scanners.WHITESPACES, integer, Scanners.WHITESPACES);
		intmayrange = infixlonce(integer, integer, "~", (a, b) -> new IntegerMayRanged(a, b))
				.or(integer.map((i) -> new IntegerMayRanged(i)));
		intmayrangespace = ignoresideoption(Scanners.WHITESPACES, intmayrange, Scanners.WHITESPACES);

		position = ignoresideoption(
			Scanners.isChar('('), Parsers.sequence(intmayrangespace, Scanners.isChar(','), intmayrangespace,
				Scanners.isChar(','), intmayrangespace, (x, d, y, d0, z) -> new PositionMayRanged(x, y, z)),
			Scanners.isChar(')'));

		block = Scanners.ANY_CHAR.until(Scanners.WHITESPACES)
				.next(list -> list.isEmpty() ? Parsers.expect("blockname") : Parsers.always()).source().map((str) -> {
					Block block = Block.getBlockFromName(str);
					if (block == null) throw new InvaildBlockNameException(str);
					return block;
				});

		form = Parsers.sequence(Scanners.WHITESPACES,
			Parsers.or(Scanners.string(Forms.LINE.toString()).retn(Forms.LINE),
				Scanners.string(Forms.PLANE.toString()).retn(Forms.PLANE),
				Scanners.string(Forms.BOX.toString()).retn(Forms.BOX),
				Scanners.string(Forms.CIRCLE.toString()).retn(Forms.CIRCLE),
				Scanners.string(Forms.CYLINDER.toString()).retn(Forms.CYLINDER))).optional(Forms.BLOCK);

		pattern = Parsers.sequence(block, form, (b, f) -> new Tuple<>(b, f)).next(o -> {
			Map<String, Object> map = new HashMap<>();
			Parser.Reference<Map<String, Object>> ref = Parser.newReference();
			ref.set(Scanners.ANY_CHAR.until(Scanners.WHITESPACES.or(Scanners.isChar(':'))).source().next(key -> {
				if (o.getSecond().options.containsKey(key))
					return ignoresideoption(Scanners.WHITESPACES, Scanners.isChar(':'), Scanners.WHITESPACES)
							.next(o.getSecond().getOption(key).next(value -> {
								map.put(key, value);
								return ignoresideoption(Scanners.WHITESPACES, Scanners.isChar(','),
									Scanners.WHITESPACES).next(ref.get()).optional(map).retn(map);
							}));
				else if (o.getSecond().flags.contains(key))
					return ignoresideoption(Scanners.WHITESPACES, Scanners.isChar(','), Scanners.WHITESPACES)
							.next(d -> {
								map.put(key, true);
								return ref.get();
							}).optional(map).retn(map);
				return Parsers.fail("invaild option name");
			}));
			return ref.get().between(Scanners.isChar('('), Scanners.isChar(')')).atomic().optional(map).retn(map)
					.map(op -> {
						o.getSecond().options.keySet().forEach((key) -> {
							if (!op.containsKey(key)) {
								if (o.getSecond().defaults.containsKey(key))
									op.put(key, o.getSecond().defaults.get(key));
								else throw new RuntimeException("expected option " + key + " not found");
							}
						});
						o.getSecond().flags.forEach(key -> op.putIfAbsent(key, false));
						return new StructDataUnit(o.getFirst(), o.getSecond(), op);
					});
		});

		parser = pattern.followedBy(Scanners.string(" " + PLACE_OP + " ")).next(data -> position.map(pos -> {
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
		})).atomic().or(Parsers.EOF.retn(Collections.emptyList()));
	}

	/*
	 * static void parse(Set<PlaceData> set, String source) { for(String line :
	 * source.split("\n")) parseLine(set, line); }
	 */

	static void parseLine(Set<PlaceData> set, String source) {
		if (source.indexOf('#') != -1) source = source.substring(0, source.indexOf('#'));
		set.addAll(parser.parse(source));
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

	private static <T> Parser<T> ignoresideoption(Parser<?> left, Parser<T> center, Parser<?> right) {
		return center.between(left.optional(null), right.optional(null));
	}

	private static <L, R, T> Parser<T> infixlonce(Parser<L> left, Parser<R> right, String name, BiFunction<L, R, T> c) {
		BiFunction<BiFunction<L, R, T>, R, Function<L, T>> rightToLeft = (op, r) -> l -> op.apply(l, r);
		return left.next(
			(l) -> Parsers.sequence(Scanners.string(name).retn(c), right, rightToLeft).map((func) -> func.apply(l)));
	}

	private static class StructDataUnit {

		public final Block block;
		public final Map<String, ?> options;
		public final Forms form;

		public StructDataUnit(Block block, Forms form, Map<String, ?> options) {
			this.block = block;
			this.form = form;
			this.options = options;
			if (form.type == Forms.Type.CUBIC && getOption("hollow", new Integer[0]) > form.dimension
					|| form.type == Forms.Type.CIRCULER && getOption("hollow", new Integer[0]) > form.dimension + 1)
				throw new RuntimeException("Syntax error: Hollow level must not exceed form dimension");
		}

		@SuppressWarnings("unchecked")
		private <T> T getOption(String key, T... dummy) {
			if (dummy.length != 0) throw new IllegalArgumentException();
			Class<?> type = dummy.getClass().getComponentType();
			if (!options.containsKey(key)) return null;
			if (type.isInstance(options.get(key))) return (T) options.get(key);
			throw new ClassCastException("object[" + options.get(key) + "][" + options.get(key).getClass()
					+ "] is not element type[" + type + "]");
		}
	}

	private enum Forms {
		BLOCK(Type.CUBIC, 0), LINE(Type.CUBIC, 1), PLANE(Type.CUBIC, 2), BOX(Type.CUBIC, 3), CIRCLE(Type.CIRCULER, 0),
		CYLINDER(Type.CIRCULER, 1);

		private enum Type {
			CUBIC, CIRCULER
		}

		private final int dimension;
		private final Type type;
		private final Map<String, Parser<?>> options = new HashMap<>();
		private final Map<String, Object> defaults = new HashMap<>();
		private final Set<String> flags = new HashSet<>();

		private Forms(Type type, int dim) {
			this.type = type;
			addOption("hollow", integer, 0);
			switch (type) {
				case CIRCULER:
					options.put("axis", Scanners
							.isChar(CharPredicates.or(CharPredicates.range('x', 'z'), CharPredicates.range('X', 'Z')))
							.source().map(ch -> Axis.byName(ch)));
					options.put("radius", decimal);
					flags.add("bold");
					break;
			}
			dimension = dim;
		}

		<T> void addOption(String key, Parser<T> parser, Object defaultValue) {
			options.put(key, parser);
			defaults.put(key, defaultValue);
		}

		@SuppressWarnings("unchecked")
		private <T> Parser<T> getOption(String key, T... dummy) {
			if (dummy.length != 0) throw new IllegalArgumentException();
			Class<?> type = dummy.getClass().getComponentType();
			if (type.isInstance(options.get(key))) return (Parser<T>) options.get(key);
			throw new ClassCastException("object[" + options.get(key) + "][" + options.get(key).getClass()
					+ "] is not element type[" + type + "]");
		}

		@Override
		public String toString() {
			return super.toString().toLowerCase();
		}
	}

	private static class PositionMayRanged {
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
}
