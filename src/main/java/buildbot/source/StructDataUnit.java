package main.java.buildbot.source;

import java.util.Map;

import main.java.buildbot.source.Forms;
import net.minecraft.block.Block;

class StructDataUnit {
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
	<T> T getOption(String key, T... dummy) {
		if (dummy.length != 0) throw new IllegalArgumentException();
		Class<?> type = dummy.getClass().getComponentType();
		if (!options.containsKey(key)) return null;
		if (type.isInstance(options.get(key))) return (T) options.get(key);
		throw new ClassCastException(
			"object[" + options.get(key) + "][" + options.get(key).getClass() + "] is not element type[" + type + "]");
	}
}