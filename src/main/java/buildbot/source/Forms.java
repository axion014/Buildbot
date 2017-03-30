package main.java.buildbot.source;

import java.util.*;

import org.jparsec.Parser;

enum Forms {
		BLOCK(Type.CUBIC, 0), LINE(Type.CUBIC, 1), PLANE(Type.CUBIC, 2), BOX(Type.CUBIC, 3), CIRCLE(Type.CIRCULER, 0),
		CYLINDER(Type.CIRCULER, 1);

		enum Type {
			CUBIC, CIRCULER
		}

		final int dimension;
		final Type type;
		final Map<String, Parser<?>> options = new HashMap<>();
		final Map<String, Object> defaults = new HashMap<>();
		final Set<String> flags = new HashSet<>();

		private Forms(Type type, int dim) {
			this.type = type;
			dimension = dim;
		}

		<T> void addOption(String key, Parser<T> parser, Object defaultValue) {
			options.put(key, parser);
			defaults.put(key, defaultValue);
		}

		@SuppressWarnings("unchecked") <T> Parser<T> getOption(String key, T... dummy) {
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