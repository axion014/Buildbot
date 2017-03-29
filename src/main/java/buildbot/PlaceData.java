package main.java.buildbot;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class PlaceData extends BlockData {
	
	public boolean errored = false;
	public double priority = 0.0;

	public PlaceData(BlockPos pos, Block block) {
		super(pos, block);
	}

	public PlaceData(int i, int j, int k, Block block) {
		super(i, j, k, block);
	}
	
}
