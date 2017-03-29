package main.java.buildbot;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class Result implements IMessage {

	public boolean issuccess;
	
	public Result() {}

	public Result(boolean b) {
		issuccess = b;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		issuccess = buf.readBoolean();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeBoolean(issuccess);
	}

}
