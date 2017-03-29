package main.java.buildbot;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class BlockData {
	public final BlockPos pos;
	public final Block block;

	public BlockData(BlockPos pos, Block block) {
		this.pos = pos;
		this.block = block;
	}
	
	public BlockData(int x, int y, int z, Block block) {
		this(new BlockPos(x, y, z), block);
	}

	@Override
	public String toString() {
		return Buildbot.smartString(block) + " at " + Buildbot.smartString(pos);
	}
}
