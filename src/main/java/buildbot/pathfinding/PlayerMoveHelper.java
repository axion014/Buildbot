package main.java.buildbot.pathfinding;

import main.java.buildbot.config.ConfigManager;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PlayerMoveHelper {

	/** The EntityPlayer that is being moved */
	protected final EntityPlayer player;
	protected double posX;
	protected double posY;
	protected double posZ;
	/** The speed at which the player should move */
	protected double speed;
	protected float moveForward;
	protected float moveStrafe;
	public EntityMoveHelper.Action action = EntityMoveHelper.Action.WAIT;
	private final PathNavigatePlayer navigater;

	public PlayerMoveHelper(PathNavigatePlayer navigater) {
		this.navigater = navigater;
		player = navigater.player;
	}

	/**
	 * Sets the speed and location to move to
	 */
	public void setMoveTo(double x, double y, double z, double speed) {
		posX = x;
		posY = y;
		posZ = z;
		this.speed = speed;
		action = EntityMoveHelper.Action.MOVE_TO;
	}

	public void onUpdateMoveHelper() {
		if (action == EntityMoveHelper.Action.STRAFE) {
			float f = (float) player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
			float f2 = moveForward;
			float f3 = moveStrafe;
			moveForward = (float) speed * f;
			float f4 = MathHelper.sqrt(f2 * f2 + f3 * f3);

			if (f4 < 1.0F) f4 = 1.0F;

			f4 = moveForward / f4;
			f2 *= f4;
			f3 *= f4;
			float f5 = MathHelper.sin(player.rotationYaw * 0.017453292F);
			float f6 = MathHelper.cos(player.rotationYaw * 0.017453292F);

			if (navigater.getNodeProcessor() != null && PlayerNodeProcessor.getPathNodeType(player.world,
				MathHelper.floor(player.posX + f2 * f6 - f3 * f5), MathHelper.floor(player.posY),
				MathHelper.floor(player.posZ + f3 * f6 + f2 * f5)) != PathNodeType.WALKABLE) {
				moveStrafe = 0.0F;
				moveForward = f;
			}

			action = EntityMoveHelper.Action.WAIT;
		} else if (action == EntityMoveHelper.Action.MOVE_TO) {
			action = EntityMoveHelper.Action.WAIT;
			double d0 = posX - player.posX;
			double d1 = posZ - player.posZ;
			double d2 = posY - player.posY;

			if (d0 * d0 + d2 * d2 + d1 * d1 < 0.25) {
				moveForward = 0.0F;
				return;
			}

			player.rotationYaw = limitAngle(player.rotationYaw,
				(float) (MathHelper.atan2(d1, d0) * (180D / Math.PI) - 90.0),
				(float) Math.max(ConfigManager.get().getPropLookspeed(), 10.0));
			moveForward = (float) (speed
					* player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()
					* (player.isSprinting() ? 2 : 1));

			if (d2 > (player.capabilities.isFlying ? -1.0 : player.stepHeight) && d0 * d0 + d1 * d1 < Math.max(1.0F, player.width)) {
				if (d2 > 1.6) {
					if (player.capabilities.allowFlying) {
						if (!player.capabilities.isFlying) {
							player.capabilities.isFlying = true;
							player.sendPlayerAbilities();
						}
						player.motionY += player.capabilities.getFlySpeed() * 3.0F;
					}
				} else {
					navigater.getJumpHelper().setJumping();
					action = EntityMoveHelper.Action.JUMPING;
				}
			}
			if (d2 < -0.6 && d0 * d0 + d1 * d1 < Math.max(0.5F, player.width / 2) && player.capabilities.isFlying)
				player.motionY -= player.capabilities.getFlySpeed() * 3.0F;
		} else if (action == EntityMoveHelper.Action.JUMPING) {
			moveForward = (float) (speed
					* player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());

			if (player.onGround) action = EntityMoveHelper.Action.WAIT;
		} else moveForward = 0.0F;

		/*
		 * float f8; if (player.onGround) { float f6 = player.world
		 * .getBlockState(new BlockPos(player.posX,
		 * player.getEntityBoundingBox().minY - 1.0, player.posZ))
		 * .getBlock().slipperiness * 0.91F; f8 = player.getAIMoveSpeed() *
		 * 0.16277136F / (f6 * f6 * f6); } else f8 = player.jumpMovementFactor;
		 * player.moveRelative(moveStrafe, moveForward, f8);
		 */
		player.moveEntityWithHeading(moveStrafe, moveForward);
	}

	/**
	 * Limits the given angle to a upper and lower limit.
	 */
	private static float limitAngle(float p_75639_1_, float p_75639_2_, float p_75639_3_) {
		float f = MathHelper.wrapDegrees(p_75639_2_ - p_75639_1_);

		if (f > p_75639_3_) f = p_75639_3_;
		if (f < -p_75639_3_) f = -p_75639_3_;

		return p_75639_1_ + f;
	}

}
