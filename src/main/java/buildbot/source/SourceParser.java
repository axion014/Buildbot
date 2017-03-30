package main.java.buildbot.source;

import java.util.*;
import java.util.function.BiFunction;

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
	private static Parser<Set<PlaceData>> parser;
	private static Parser<Forms> form;
	private static Parser<StructDataUnit> pattern;
	private static Parser<Void> commamayrangespace;

	private SourceParser() {}

	static {
		decimal = Parsers.sequence(Scanners.isChar('-').optional(null).source(),
			Scanners.DEC_INTEGER.or(Scanners.isChar('0').source()),
			Patterns.FRACTION.toScanner("").optional(null).source(), (a, b, c) -> Double.valueOf(a + b + c));
		intspace = optAroundSpace(integer);
		intmayrange = infixlonce(integer, integer, "~", (a, b) -> new IntegerMayRanged(a, b))
				.or(integer.map((i) -> new IntegerMayRanged(i)));
		intmayrangespace = optAroundSpace(intmayrange);
		commamayrangespace = optAroundSpace(Scanners.isChar(','));

		position = ignoresideoption(Scanners.isChar('('),
			Parsers.sequence(intmayrangespace.followedBy(Scanners.isChar(',')),
				intmayrangespace.followedBy(Scanners.isChar(',')), intmayrangespace,
				(x, y, z) -> new PositionMayRanged(x, y, z)),
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
			formparsers.add(Scanners.string(form.toString()).retn(form));
		}

		form = Parsers.sequence(Scanners.WHITESPACES, Parsers.or(formparsers)).optional(Forms.BLOCK);

		pattern = Parsers.sequence(block, form, (b, f) -> new Tuple<>(b, f)).next(o -> {
			Map<String, Object> map = new HashMap<>();
			Parser.Reference<Map<String, Object>> ref = Parser.newReference();
			ref.set(Scanners.ANY_CHAR.until(Scanners.WHITESPACES.or(Scanners.isChar(':'))).source().next(key -> {
				if (o.getSecond().options.containsKey(key))
					return optAroundSpace(Scanners.isChar(':')).next(o.getSecond().getOption(key).next(value -> {
						map.put(key, value);
						return commamayrangespace.next(ref.get()).optional(map).retn(map);
					}));
				else if (o.getSecond().flags.contains(key)) return commamayrangespace.next(d -> {
					map.put(key, true);
					return ref.get();
				}).optional(map).retn(map);
				return Parsers.fail("invaild option name " + key);
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

		parser = infixlonce(pattern, position, PLACE_OP, (data, pos) -> Dataadder.bringData(data, pos)).atomic()
				.or(Parsers.EOF.retn(Collections.emptySet()));
	}

	public static void parse(Set<PlaceData> set, String source) {
		for (String line : source.split("\n"))
			parseLine(set, line);
	}

	public static void parseLine(Set<PlaceData> set, String source) {
		if (source.indexOf('#') != -1) source = source.substring(0, source.indexOf('#'));
		set.addAll(parser.parse(source));
	}

	private static <T> Parser<T> ignoresideoption(Parser<?> left, Parser<T> center, Parser<?> right) {
		return center.between(left.optional(null), right.optional(null));
	}

	private static <T> Parser<T> optAroundSpace(Parser<T> raw) {
		return ignoresideoption(Scanners.WHITESPACES, raw, Scanners.WHITESPACES);
	}

	private static <L, R, T> Parser<T> infixlonce(Parser<L> left, Parser<R> right, String name, BiFunction<L, R, T> c) {
		return left.followedBy(optAroundSpace(Scanners.string(name))).next(l -> right.map((r) -> c.apply(l, r)));
	}
}