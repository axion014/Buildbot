package main.java.buildbot.pathfinding;

import net.minecraft.entity.player.EntityPlayer;

public class PlayerJumpHelper {
	private final EntityPlayer player;
	protected boolean isJumping;

	public PlayerJumpHelper(EntityPlayer player) {
		this.player = player;
	}

	public void setJumping() {
		isJumping = true;
	}

	/**
	 * Called to actually make the entity jump if isJumping is true.
	 */
	public void doJump() {
		if (isJumping) player.jump();
		isJumping = false;
	}
}
