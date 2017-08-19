package main.java.buildbot.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.pattern.CharPredicates;
import org.jparsec.pattern.Patterns;

import com.google.common.collect.Lists;

import main.java.buildbot.Buildbot;
import main.java.buildbot.PlaceData;
import main.java.buildbot.math.DoubleMayRanged;
import main.java.buildbot.math.IntegerMayRanged;
import main.java.buildbot.math.PositionMayRanged;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class SourceParser {
	private static final String PLACE_OP = "at";
	private static Parser<Integer> integer = Parsers.sequence(Scanners.isChar('-').optional(null).source(),
		Scanners.DEC_INTEGER.or(Scanners.isChar('0').source()), (a, b) -> Integer.valueOf(a + b));
	private static Parser<Double> decimal = Parsers.sequence(Scanners.isChar('-').optional(null).source(),
		Scanners.DEC_INTEGER.or(Scanners.isChar('0').source()), Patterns.FRACTION.toScanner("").optional(null).source(),
		(a, b, c) -> Double.valueOf(a + b + c));
	private static Parser<IntegerMayRanged> intmayrange;
	private static Parser<BlockPos> position;
	private static Parser<PositionMayRanged> positionmayrange;
	private static Parser<Block> block;
	private static Parser<Forms> form;
	private static Parser<StructDataUnit> pattern;
	private static Parser<Void> commamayrangespace = optAroundSpace(Scanners.isChar(','));
	private static Parser<List<Block>> multipleblock;
	private static Parser<Map<BlockPos, Block>> parser;
	private static BlockPos offset = new BlockPos(0, 0, 0);
	private static Parser<String> file;
	private static Parser<DoubleMayRanged> decimalmayrange;
	private static Map<String, Object> optionmap = new HashMap<>();
	
	private SourceParser() {
	}

	static {
		intmayrange = infixlonce(integer, integer, "~", (a, b) -> new IntegerMayRanged(a, b))
				.or(integer.map((i) -> new IntegerMayRanged(i)));
		
		decimalmayrange = infixlonce(decimal, decimal, "~", (a, b) -> new DoubleMayRanged(a, b))
				.or(decimal.map((i) -> new DoubleMayRanged(i)));
		
		position = ignoresideoption(Scanners.isChar('('), Parsers.sequence(
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posX)),
				integer, Integer::sum)).followedBy(Scanners.isChar(',')),
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posY)),
				integer, Integer::sum)).followedBy(Scanners.isChar(',')),
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posZ)), 
				integer, Integer::sum)),
				(x, y, z) -> new BlockPos(x, y, z)),
			Scanners.isChar(')'));

		positionmayrange = ignoresideoption(Scanners.isChar('('), Parsers.sequence(
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posX)),
				intmayrange, IntegerMayRanged::new, Function.identity(), IntegerMayRanged::add)).followedBy(Scanners.isChar(',')),
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posY)),
				intmayrange, IntegerMayRanged::new, Function.identity(), IntegerMayRanged::add)).followedBy(Scanners.isChar(',')),
			optAroundSpace(inclusiveOr(Scanners.isChar('~').retn(MathHelper.floor(Minecraft.getMinecraft().player.posZ)),
				intmayrange, IntegerMayRanged::new, Function.identity(), IntegerMayRanged::add)),
			(x, y, z) -> new PositionMayRanged(x, y, z)),
			Scanners.isChar(')'));

		block = Scanners.ANY_CHAR.until(Scanners.WHITESPACES.or(Scanners.isChar(',')).or(Scanners.isChar(')')))
				.next(list -> list.isEmpty() ? Parsers.expect("blockname") : Parsers.always()).source().map((str) -> {
					Block block = Block.getBlockFromName(str);
					if (block == null) Parsers.expect("blockname");
					return block;
				});

		multipleblock = block.map(blk -> (List<Block>) Lists.newArrayList(blk))
				.infixl(commamayrangespace.retn((a, b) -> {
					a.addAll(b);
					return a;
				})).between(Scanners.isChar('('), Scanners.isChar(')')).atomic()
				.or(block.map(blk -> (List<Block>) Lists.newArrayList(blk)));

		List<Parser<Forms>> formparsers = new LinkedList<>();

		for (Forms form : Forms.values()) {
			form.addOption("hollow", integer, 0);
			switch (form.type) {
				case CIRCULER:
					form.options.put("axis", Scanners
							.isChar(CharPredicates.or(CharPredicates.range('x', 'z'), CharPredicates.range('X', 'Z')))
							.source().map(ch -> Axis.byName(ch)));
					form.options.put("radius", decimalmayrange);
					form.flags.add("bold");
					form.flags.add("fill");
					break;
			}
			formparsers.add(Scanners.stringCaseInsensitive(form.toString()).retn(form));
		}

		form = Parsers.sequence(Scanners.WHITESPACES, Parsers.or(formparsers)).optional(Forms.BLOCK);

		pattern = Parsers.sequence(multipleblock, form, (b, f) -> new Tuple<>(b, f)).next(o -> {
			if (o.getFirst().size() > 1) {
				switch (o.getSecond().dimension) {
					case 0:
						return Parsers.fail("single block don't support multiple block");
					case 2:
						if (!isSquareNumber(o.getFirst().size()))
							return Parsers.fail("on 2D shape, multiple block variant must be square number");
						break;
					case 3:
						if (!isCubeNumber(o.getFirst().size()))
							return Parsers.fail("on 3D shape, multiple block variant must be cube number");
				}
			}
			Parser.Reference<Map<String, Object>> ref = Parser.newReference();
			ref.set(Scanners.ANY_CHAR.until(Scanners.WHITESPACES.or(Scanners.isChar(':')).or(Scanners.isChar(')'))).source().next(key -> {
				if (o.getSecond().options.containsKey(key))
					return optAroundSpace(Scanners.isChar(':')).next(o.getSecond().getOption(key).next(value -> {
						optionmap.put(key, value);
						return commamayrangespace.next(ref.get()).optional(optionmap).retn(optionmap);
					}));
				else if (o.getSecond().flags.contains(key)) {
					optionmap.put(key, true);
					return commamayrangespace.next(ref.get()).optional(optionmap).retn(optionmap);
				}
				return Parsers.fail("invaild option name " + key);
			}));
			return ref.get().between(Scanners.isChar('('), Scanners.isChar(')')).atomic().optional(optionmap).retn(optionmap)
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
		file = Scanners.many1(CharPredicates.ALWAYS).between(Scanners.isChar('"'), Scanners.isChar('"'))
				.or(Scanners.many1(CharPredicates.not(CharPredicates.IS_WHITESPACE))).source().next((path) -> {
			if (Files.exists(Paths.get(path))) {
				try {
					return Parsers.constant(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
				} catch (IOException e) {
					return Parsers.fail(e.getLocalizedMessage());
				}
			}
			return Parsers.fail("Invaild path");
		});
		parser = Parsers.or(infixloncespacerequired(file, position, PLACE_OP, (data, pos) -> {
			offset = offset.add(pos);
			Map<BlockPos, Block> result = parseimpl(data);
			offset = offset.subtract(pos);
			return result;
		}).atomic(), file.map(SourceParser::parseimpl), infixloncespacerequired(pattern, positionmayrange, PLACE_OP, (data, pos) -> Dataadder.bringData(data, pos.add(offset)))
				.atomic(), Parsers.EOF.retn(Collections.emptyMap()));
	}
	
	private static Map<BlockPos, Block> parseimpl(String source) {
		Buildbot.LOGGER.info("offset is: " + offset);
		Map<BlockPos, Block> map = new HashMap<>();
		for (String line : source.split("\n")) {
			if (line.indexOf('#') != -1) line = line.substring(0, line.indexOf('#'));
			map.putAll(parser.parse(line));
			optionmap = new HashMap<>();
		}
		Buildbot.LOGGER.info(Arrays.toString(map.entrySet().toArray()));
		return map;
	}
	
	public static Set<PlaceData> parse(String source) {
		return parseimpl(source).entrySet().parallelStream().map(e -> new PlaceData(e.getKey(), e.getValue()))
				.collect(Collectors.toCollection(HashSet::new));
	}

	private static <T> Parser<T> ignoresideoption(Parser<?> left, Parser<T> center, Parser<?> right) {
		return center.between(left.optional(null), right.optional(null));
	}

	private static <T> Parser<T> aroundSpace(Parser<T> raw) {
		return raw.between(Scanners.WHITESPACES, Scanners.WHITESPACES);
	}

	private static <T> Parser<T> optAroundSpace(Parser<T> raw) {
		return ignoresideoption(Scanners.WHITESPACES, raw, Scanners.WHITESPACES);
	}

	private static <L, R, T> Parser<T> infixlonce(Parser<L> left, Parser<R> right, String name, BiFunction<L, R, T> c) {
		return left.followedBy(optAroundSpace(Scanners.string(name))).next(l -> right.map((r) -> c.apply(l, r)));
	}

	private static <L, R, T> Parser<T> infixloncespacerequired(Parser<L> left, Parser<R> right, String name,
			BiFunction<L, R, T> c) {
		return left.followedBy(aroundSpace(Scanners.string(name))).next(l -> right.map((r) -> c.apply(l, r)));
	}
	
	private static <T> Parser<T> inclusiveOr(Parser<T> left, Parser<T> right, BiFunction<T, T, T> c) {
		return inclusiveOr(left, right, Function.identity(), Function.identity(), c);
	}
	
	private static <T, A, B> Parser<T> inclusiveOr(Parser<A> left, Parser<B> right, Function<A, T> fl, Function<B, T> fr, BiFunction<A, B, T> c) {
		return Parsers.or(Parsers.sequence(left, right, c), left.map(fl), right.map(fr));
	}

	public static boolean isSquareNumber(long x) {
		long y = (long) Math.sqrt(x);
		return y * y == x;
	}

	public static boolean isCubeNumber(long x) {
		long y = (long) Math.cbrt(x);
		return y * y * y == x;
	}
}