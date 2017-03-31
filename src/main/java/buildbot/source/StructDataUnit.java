package main.java.buildbot.source;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import main.java.buildbot.source.Forms;
import net.minecraft.block.Block;

class StructDataUnit {
	private Block block;
	private int index;
	public final Map<String, ?> options;
	public final Forms form;
	public final List<Block> blocks;
	public final boolean ismultiblock;

	public StructDataUnit(List<Block> list, Forms form, Map<String, ?> options) {
		if (list.isEmpty()) throw new IllegalArgumentException();
		ismultiblock = list.size() != 1;
		block = ismultiblock ? null : list.get(0);
		blocks = Collections.unmodifiableList(list);
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
	
	public Block currentBlock() {
		return block;
	}

	public void nextBlock() {
		if (index >= blocks.size()) index = 0;
		block = blocks.get(index++);
	}
}