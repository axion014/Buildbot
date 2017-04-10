package main.java.buildbot;

import java.util.List;

import com.google.common.base.Predicates;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.*;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
class LookTester {
	private EntityPlayer player;
	private Minecraft minecraft = Minecraft.getMinecraft();

	public LookTester(EntityPlayer player) {
		this.player = player;
	}

	PlaceTestResult getCanPlaceBlockAt(BlockData block) {
		Vec3d targetCenter = new Vec3d(block.pos).addVector(0.5, 0.5, 0.5);
		double reach = minecraft.playerController.getBlockReachDistance();
		final Vec3d eye = player.getPositionEyes(1.0F).subtract(0.0, 0.08, 0.0);
		if (MathHelper.sqrt(targetCenter.squareDistanceTo(eye)) >= reach + 1)
			return new PlaceTestResult(FailCauses.TOO_FAR);
		if (!player.world.isAirBlock(block.pos)) return new PlaceTestResult(FailCauses.OCCUPIED);
		if (!minecraft.world.checkNoEntityCollision(
			block.block.getDefaultState().getCollisionBoundingBox(minecraft.world, block.pos).offset(block.pos)))
			return new PlaceTestResult(FailCauses.TOO_NEAR);
		FailCauses failcause = null;
		for (EnumFacing face : EnumFacing.values()) {
			reach = minecraft.playerController.getBlockReachDistance();
			BlockPos pos = block.pos.offset(face);
			AxisAlignedBB bb = player.world.getBlockState(pos).getCollisionBoundingBox(player.world, pos);
			final double offset;
			if (bb == null
					|| face.getAxis() == Axis.X && (0.5 < bb.minY || 0.5 > bb.maxY || 0.5 < bb.minZ || 0.5 > bb.maxZ)
					|| face.getAxis() == Axis.Y && (0.5 < bb.minX || 0.5 > bb.maxX || 0.5 < bb.minZ || 0.5 > bb.maxZ)
					|| face.getAxis() == Axis.Z && (0.5 < bb.minX || 0.5 > bb.maxX || 0.5 < bb.minY || 0.5 > bb.maxY)) {
				if (failcause == null) failcause = FailCauses.NO_BASE_BLOCK;
				continue;
			}
			switch (face) {
				case UP:
					offset = bb.minY;
					break;
				case DOWN:
					offset = 1 - bb.maxY;
					break;
				case EAST:
					offset = bb.minX;
					break;
				case WEST:
					offset = 1 - bb.maxX;
					break;
				case NORTH:
					offset = 1 - bb.maxZ;
					break;
				default: // south
					offset = bb.minZ;
					break;
			}
			final Vec3d faceVec = targetCenter.add(new Vec3d(face.getDirectionVec()).scale(offset + 0.5));
			if (MathHelper.sqrt(faceVec.squareDistanceTo(eye)) >= reach - 0.2) {
				failcause = FailCauses.TOO_FAR;
				continue;
			}
			failcause = FailCauses.BLOCKED;
			RayTraceResult result = minecraft.world.rayTraceBlocks(eye, faceVec, false, false, true);
			double d1 = reach;

			if (minecraft.playerController.extendedReach()) reach = 6.0;

			if (result != null) d1 = result.hitVec.distanceTo(eye);

			Vec3d traced = faceVec.subtract(eye);
			Entity pointedEntity = null;
			Vec3d entityHitVec = null;
			List<Entity> list = minecraft.world.getEntitiesInAABBexcluding(player,
				player.getEntityBoundingBox().addCoord(traced.xCoord, traced.yCoord, traced.zCoord).expandXyz(1.0),
				Predicates.and(EntitySelectors.NOT_SPECTATING,
					p_apply_1_ -> p_apply_1_ != null && p_apply_1_.canBeCollidedWith()));
			double d2 = d1;

			for (Entity entity : list) {
				AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox().expandXyz(entity.getCollisionBorderSize());
				RayTraceResult raytraceresult = axisalignedbb.calculateIntercept(eye, traced);

				if (axisalignedbb.isVecInside(eye)) {
					if (d2 >= 0.0) {
						pointedEntity = entity;
						entityHitVec = raytraceresult == null ? eye : raytraceresult.hitVec;
						d2 = 0.0;
					}
				} else if (raytraceresult != null) {
					double d3 = eye.distanceTo(raytraceresult.hitVec);

					if (d3 < d2 || d2 == 0.0) {
						if (entity.getLowestRidingEntity() == player.getLowestRidingEntity()
								&& !player.canRiderInteract()) {
							if (d2 == 0.0) {
								pointedEntity = entity;
								entityHitVec = raytraceresult.hitVec;
							}
						} else {
							pointedEntity = entity;
							entityHitVec = raytraceresult.hitVec;
							d2 = d3;
						}
					}
				}
			}

			if (result != null && !(pointedEntity != null
					&& (!minecraft.playerController.extendedReach() && reach > 3.0 && eye.distanceTo(entityHitVec) > 3.0
							|| d2 < d1))
					&& result.typeOfHit == Type.MISS)
				return new PlaceTestResult(face);
		}
		return new PlaceTestResult(failcause);
	}

	LookTestResult getCanLookAt(BlockPos target) {
		return getCanLookAt(new Vec3d(target).addVector(0.5, 0.5, 0.5));
	}

	LookTestResult getCanLookAt(Vec3d target) {
		double reach = minecraft.playerController.getBlockReachDistance();
		Vec3d eye = player.getPositionEyes(1.0F).subtract(0.0, 0.08, 0.0);
		if (MathHelper.sqrt(target.squareDistanceTo(eye)) >= reach) return new LookTestResult(FailCauses.TOO_FAR);
		RayTraceResult result = minecraft.world.rayTraceBlocks(eye, target, false, false, true);
		double d1 = reach;

		if (minecraft.playerController.extendedReach()) reach = 6.0;

		if (result != null) d1 = result.hitVec.distanceTo(eye);

		Vec3d traced = target.subtract(eye);
		Entity pointedEntity = null;
		Vec3d entityHitVec = null;
		List<Entity> list = minecraft.world.getEntitiesInAABBexcluding(player,
			player.getEntityBoundingBox().addCoord(traced.xCoord, traced.yCoord, traced.zCoord).expandXyz(1.0),
			Predicates.and(EntitySelectors.NOT_SPECTATING,
				p_apply_1_ -> p_apply_1_ != null && p_apply_1_.canBeCollidedWith()));
		double d2 = d1;

		for (Entity entity : list) {
			AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox().expandXyz(entity.getCollisionBorderSize());
			RayTraceResult raytraceresult = axisalignedbb.calculateIntercept(eye, traced);

			if (axisalignedbb.isVecInside(eye)) {
				if (d2 >= 0.0) {
					pointedEntity = entity;
					entityHitVec = raytraceresult == null ? eye : raytraceresult.hitVec;
					d2 = 0.0;
				}
			} else if (raytraceresult != null) {
				double d3 = eye.distanceTo(raytraceresult.hitVec);

				if (d3 < d2 || d2 == 0.0) {
					if (entity.getLowestRidingEntity() == player.getLowestRidingEntity()
							&& !player.canRiderInteract()) {
						if (d2 == 0.0) {
							pointedEntity = entity;
							entityHitVec = raytraceresult.hitVec;
						}
					} else {
						pointedEntity = entity;
						entityHitVec = raytraceresult.hitVec;
						d2 = d3;
					}
				}
			}
		}

		if (result == null || pointedEntity != null
				&& (!minecraft.playerController.extendedReach() && reach > 3.0 && eye.distanceTo(entityHitVec) > 3.0
						|| d2 < d1))
			return new LookTestResult(FailCauses.BLOCKED);

		if (result.getBlockPos().equals(target)) return new LookTestResult(true);
		if (result.typeOfHit == Type.MISS) return new LookTestResult(false);
		return new LookTestResult(FailCauses.BLOCKED);
	}

	boolean canLookAt(BlockPos target) {
		return getCanLookAt(target).issuccess;
	}

	boolean canLookAt(Vec3d target) {
		return getCanLookAt(target).issuccess;
	}
}

class LookTestResult {
	final boolean issuccess;
	final boolean isoccupied;
	final FailCauses cause;

	LookTestResult(boolean occupied) {
		issuccess = true;
		isoccupied = occupied;
		cause = null;
	}

	LookTestResult(FailCauses failcause) {
		if (failcause == FailCauses.OCCUPIED) throw new IllegalArgumentException();
		issuccess = false;
		cause = failcause;
		isoccupied = false;
	}
}

class PlaceTestResult {
	final boolean issuccess;
	final EnumFacing placableface;
	final FailCauses cause;

	PlaceTestResult(EnumFacing face) {
		issuccess = true;
		placableface = face;
		cause = null;
	}

	PlaceTestResult(FailCauses failcause) {
		issuccess = false;
		cause = failcause;
		placableface = null;
	}
}

enum FailCauses {
	OCCUPIED, TOO_FAR, NO_BASE_BLOCK, BLOCKED, TOO_NEAR
}
