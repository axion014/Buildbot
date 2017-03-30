package main.java.buildbot.source;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.pattern.CharPredicates;
import org.jparsec.pattern.Patterns;

import main.java.buildbot.InvaildBlockNameException;
import main.java.buildbot.PlaceData;
import main.java.buildbot.math.IntegerMayRanged;
import main.java.buildbot.math.PositionMayRanged;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.Tuple;

public class SourceParser {
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

		List<Parser<Forms>> formparsers = new LinkedList<>();

		for (Forms form : Forms.values()) {
			form.addOption("hollow", integer, 0);
			switch (form.type) {
				case CIRCULER:
					form.options.put("axis", Scanners
							.isChar(CharPredicates.or(CharPredicates.range('x', 'z'), CharPredicates.range('X', 'Z')))
							.source().map(ch -> Axis.byName(ch)));
					form.options.put("radius", decimal);
					form.flags.add("bold");
					break;
			}
		}

		form = Parsers.sequence(Scanners.WHITESPACES, Parsers.or(formparsers)).optional(Forms.BLOCK);

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

		parser = pattern.followedBy(Scanners.string(" " + PLACE_OP + " "))
				.next(data -> position.map(pos -> Dataadder.bringData(data, pos))).atomic()
				.or(Parsers.EOF.retn(Collections.emptyList()));
	}

	/*
	 * static void parse(Set<PlaceData> set, String source) { for(String line :
	 * source.split("\n")) parseLine(set, line); }
	 */

	public static void parseLine(Set<PlaceData> set, String source) {
		if (source.indexOf('#') != -1) source = source.substring(0, source.indexOf('#'));
		set.addAll(parser.parse(source));
	}

	private static <T> Parser<T> ignoresideoption(Parser<?> left, Parser<T> center, Parser<?> right) {
		return center.between(left.optional(null), right.optional(null));
	}

	private static <L, R, T> Parser<T> infixlonce(Parser<L> left, Parser<R> right, String name, BiFunction<L, R, T> c) {
		BiFunction<BiFunction<L, R, T>, R, Function<L, T>> rightToLeft = (op, r) -> l -> op.apply(l, r);
		return left.next(
			(l) -> Parsers.sequence(Scanners.string(name).retn(c), right, rightToLeft).map((func) -> func.apply(l)));
	}
}
