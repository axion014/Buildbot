package main.java.buildbot.pathfinding;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PlayerLookHelper {
	private final EntityPlayer player;
	/**
	 * The amount of change that is made each update for an entity facing a
	 * direction.
	 */
	private float lookSpdYaw;
	/**
	 * The amount of change that is made each update for an entity facing a
	 * direction.
	 */
	private float lookSpdPitch;
	/** Whether or not the entity is trying to look at something. */
	private boolean isLooking;
	private Vec3d lookPos;

	public PlayerLookHelper(PathNavigatePlayer navigater) {
		player = navigater.player;
	}

	/**
	 * Sets position to look at using entity
	 */
	public void setLookPositionWithEntity(Entity entity, float deltaYaw, float deltaPitch) {
		setLookPosition(entity.posX,
			entity instanceof EntityLivingBase ? entity.posY + entity.getEyeHeight()
				: (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0,
			entity.posZ, deltaYaw, deltaPitch);
	}

	/**
	 * Sets position to look at
	 */
	public void setLookPosition(double x, double y, double z, float deltaYaw, float deltaPitch) {
		setLookPosition(new Vec3d(x, y, z), deltaYaw, deltaPitch);
	}

	public void setLookPosition(Vec3d lookPos, float deltaYaw, float deltaPitch) {
		this.lookPos = lookPos;
		lookSpdYaw = deltaYaw;
		lookSpdPitch = deltaPitch;
		isLooking = true;
	}
	
	public void lookIfNotAlreadyLooked(Vec3d lookPos, float deltaYaw, float deltaPitch) {
		if (!lookPos.equals(this.lookPos)) setLookPosition(lookPos, deltaYaw, deltaPitch);
	}

	/**
	 * Updates look
	 */
	public void onUpdateLook() {
		if (isLooking) {
			Vec3d diff = lookPos.subtract(player.getPositionEyes(1.0F));
			float f = (float) (MathHelper.atan2(diff.zCoord, diff.xCoord) * (180D / Math.PI)) - 90.0F;
			float f1 = (float) -(MathHelper.atan2(diff.yCoord,
				MathHelper.sqrt(diff.xCoord * diff.xCoord + diff.zCoord * diff.zCoord)) * (180D / Math.PI));
			player.rotationPitch = updateRotation(player.rotationPitch, f1, lookSpdPitch);
			player.rotationYaw = updateRotation(player.rotationYaw, f, lookSpdYaw);
			if (MathHelper.wrapDegrees(player.rotationPitch - f1) == 0
					&& MathHelper.wrapDegrees(player.rotationYaw - f) == 0)
				isLooking = false;
		}
	}

	private static float updateRotation(float p_75652_1_, float p_75652_2_, float p_75652_3_) {
		float f = MathHelper.wrapDegrees(p_75652_2_ - p_75652_1_);

		if (f > p_75652_3_) f = p_75652_3_;
		if (f < -p_75652_3_) f = -p_75652_3_;

		return p_75652_1_ + f;
	}

	public boolean getIsLooking() {
		return isLooking;
	}

	public Vec3d getLookPosition() {
		return lookPos;
	}

	public void relook() {
		isLooking = true;
	}

	public void resetLook() {
		lookPos = null;
	}
}