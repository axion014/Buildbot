package main.java.buildbot;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class ItemBringRequest implements IMessage {
	
	public Block block;

	public ItemBringRequest() {}
	
	public ItemBringRequest(Block block) {
		this.block = block;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		block = Block.getBlockById(buf.readInt());
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(Block.getIdFromBlock(block));
	}

}
