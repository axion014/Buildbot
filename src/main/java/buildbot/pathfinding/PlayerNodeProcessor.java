package main.java.buildbot.pathfinding;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

public class PlayerNodeProcessor {

	PathNavigatePlayer navigater;
	private EntityPlayer player;
	IBlockAccess blockaccess;
	private int playerSizeXZ;
	private int playerSizeY;
	private final IntHashMap<PathPoint> pointMap = new IntHashMap<>();
	private boolean canEnterDoors;
	private boolean canBreakDoors;
	private boolean canSwim;
	private float avoidsWater;

	public void initProcessor(IBlockAccess source, PathNavigatePlayer navigater) {
		blockaccess = source;
		this.navigater = navigater;
		player = navigater.player;
		pointMap.clearMap();
		playerSizeXZ = MathHelper.floor(player.width) + 1;
		playerSizeY = MathHelper.floor(player.height) + 1;
		avoidsWater = navigater.getPathPriority(PathNodeType.WATER);
	}

	/**
	 * This method is called when all nodes have been processed and PathEntity
	 * is created. {@link net.minecraft.pathfinding.WalkNodeProcessor
	 * WalkNodeProcessor} uses this to change its field
	 * {@link net.minecraft.pathfinding.WalkNodeProcessor#avoidsWater
	 * avoidsWater}
	 */
	public void postProcess() {
		navigater.setPathPriority(PathNodeType.WATER, avoidsWater);
		blockaccess = null;
		navigater = null;
		player = null;
	}

	/**
	 * Returns a mapped point or creates and adds one
	 */
	private PathPoint openPoint(int x, int y, int z) {
		int i = PathPoint.makeHash(x, y, z);
		PathPoint pathpoint = pointMap.lookup(i);

		if (pathpoint == null) {
			pathpoint = new PathPoint(x, y, z);
			pointMap.addKey(i, pathpoint);
		}

		return pathpoint;
	}

	/**
	 * Returns PathPoint for given coordinates
	 */
	public PathPoint getPathPointToCoords(double x, double y, double z) {
		return openPoint(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
	}

	public PathPoint getStart() {
		int i;

		if (getCanSwim() && player.isInWater()) {
			i = (int) player.getEntityBoundingBox().minY;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
				MathHelper.floor(player.posX), i, MathHelper.floor(player.posZ));

			for (Block block = blockaccess.getBlockState(pos).getBlock(); block == Blocks.FLOWING_WATER
				|| block == Blocks.WATER; block = blockaccess.getBlockState(pos).getBlock())
				pos.setY(++i);
		} else if (player.onGround || player.capabilities.isFlying)
			i = MathHelper.floor(player.getEntityBoundingBox().minY + 0.5);
		else {
			BlockPos blockpos = new BlockPos(player);

			for (; (blockaccess.getBlockState(blockpos).getMaterial() == Material.AIR
					|| blockaccess.getBlockState(blockpos).getBlock().isPassable(blockaccess, blockpos))
					&& blockpos.getY() > 0; blockpos = blockpos.down())
				;

			i = blockpos.up().getY();
		}

		BlockPos blockpos2 = new BlockPos(player);
		if (navigater.getPathPriority(this.getPathNodeType(navigater, blockpos2.getX(), i, blockpos2.getZ())) < 0.0F) {
			Set<BlockPos> set = Sets.newHashSet();
			set.add(new BlockPos(player.getEntityBoundingBox().minX, i, player.getEntityBoundingBox().minZ));
			set.add(new BlockPos(player.getEntityBoundingBox().minX, i, player.getEntityBoundingBox().maxZ));
			set.add(new BlockPos(player.getEntityBoundingBox().maxX, i, player.getEntityBoundingBox().minZ));
			set.add(new BlockPos(player.getEntityBoundingBox().maxX, i, player.getEntityBoundingBox().maxZ));

			for (BlockPos blockpos1 : set)
				if (navigater.getPathPriority(this.getPathNodeType(navigater, blockpos1)) >= 0.0F)
					return openPoint(blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
		}

		return openPoint(blockpos2.getX(), i, blockpos2.getZ());
	}

	public int findPathOptions(PathPoint[] pathOptions, PathPoint currentPoint, PathPoint targetPoint,
			float maxDistance) {
		int i = 0;
		int j = 0;
		PathNodeType pathnodetype = this.getPathNodeType(navigater, currentPoint.xCoord, currentPoint.yCoord + 1,
			currentPoint.zCoord);

		if (navigater.getPathPriority(pathnodetype) >= 0.0F) j = Math.max(1, MathHelper.floor(player.stepHeight));

		BlockPos blockpos = new BlockPos(currentPoint.xCoord, currentPoint.yCoord, currentPoint.zCoord).down();
		double d0 = currentPoint.yCoord - 1.0
				+ blockaccess.getBlockState(blockpos).getBoundingBox(blockaccess, blockpos).maxY;

		PathPoint pathpoint = getSafePoint(currentPoint.xCoord, currentPoint.yCoord, currentPoint.zCoord + 1, j, d0,
			EnumFacing.SOUTH);
		PathPoint pathpoint1 = getSafePoint(currentPoint.xCoord - 1, currentPoint.yCoord, currentPoint.zCoord, j, d0,
			EnumFacing.WEST);
		PathPoint pathpoint2 = getSafePoint(currentPoint.xCoord + 1, currentPoint.yCoord, currentPoint.zCoord, j, d0,
			EnumFacing.EAST);
		PathPoint pathpoint3 = getSafePoint(currentPoint.xCoord, currentPoint.yCoord, currentPoint.zCoord - 1, j, d0,
			EnumFacing.NORTH);

		if (pathpoint != null && !pathpoint.visited && pathpoint.distanceTo(targetPoint) < maxDistance)
			pathOptions[i++] = pathpoint;
		if (pathpoint1 != null && !pathpoint1.visited && pathpoint1.distanceTo(targetPoint) < maxDistance)
			pathOptions[i++] = pathpoint1;
		if (pathpoint2 != null && !pathpoint2.visited && pathpoint2.distanceTo(targetPoint) < maxDistance)
			pathOptions[i++] = pathpoint2;
		if (pathpoint3 != null && !pathpoint3.visited && pathpoint3.distanceTo(targetPoint) < maxDistance)
			pathOptions[i++] = pathpoint3;

		boolean flag = pathpoint3 == null || pathpoint3.nodeType == PathNodeType.OPEN || pathpoint3.costMalus != 0.0F;
		boolean flag1 = pathpoint == null || pathpoint.nodeType == PathNodeType.OPEN || pathpoint.costMalus != 0.0F;
		boolean flag2 = pathpoint2 == null || pathpoint2.nodeType == PathNodeType.OPEN || pathpoint2.costMalus != 0.0F;
		boolean flag3 = pathpoint1 == null || pathpoint1.nodeType == PathNodeType.OPEN || pathpoint1.costMalus != 0.0F;

		if (flag && flag3) {
			PathPoint pathpoint4 = getSafePoint(currentPoint.xCoord - 1, currentPoint.yCoord, currentPoint.zCoord - 1,
				j, d0, EnumFacing.NORTH);
			if (pathpoint4 != null && !pathpoint4.visited && pathpoint4.distanceTo(targetPoint) < maxDistance)
				pathOptions[i++] = pathpoint4;
		}
		if (flag && flag2) {
			PathPoint pathpoint5 = getSafePoint(currentPoint.xCoord + 1, currentPoint.yCoord, currentPoint.zCoord - 1,
				j, d0, EnumFacing.NORTH);
			if (pathpoint5 != null && !pathpoint5.visited && pathpoint5.distanceTo(targetPoint) < maxDistance)
				pathOptions[i++] = pathpoint5;
		}
		if (flag1 && flag3) {
			PathPoint pathpoint6 = getSafePoint(currentPoint.xCoord - 1, currentPoint.yCoord, currentPoint.zCoord + 1,
				j, d0, EnumFacing.SOUTH);
			if (pathpoint6 != null && !pathpoint6.visited && pathpoint6.distanceTo(targetPoint) < maxDistance)
				pathOptions[i++] = pathpoint6;
		}
		if (flag1 && flag2) {
			PathPoint pathpoint7 = getSafePoint(currentPoint.xCoord + 1, currentPoint.yCoord, currentPoint.zCoord + 1,
				j, d0, EnumFacing.SOUTH);
			if (pathpoint7 != null && !pathpoint7.visited && pathpoint7.distanceTo(targetPoint) < maxDistance)
				pathOptions[i++] = pathpoint7;
		}

		return i;
	}

	/**
	 * Returns a point that the entity can safely move to
	 */
	@Nullable
	private PathPoint getSafePoint(int x, int y, int z, final int stepheight, double p_186332_5_, EnumFacing facing) {
		PathPoint pathpoint = null;
		BlockPos blockpos = new BlockPos(x, y, z);
		BlockPos blockpos1 = blockpos.down();
		double d0 = y - 1.0 + blockaccess.getBlockState(blockpos1).getBoundingBox(blockaccess, blockpos1).maxY;

		if (d0 - p_186332_5_ > 1.125 && !player.capabilities.allowFlying) return null;
		PathNodeType pathnodetype = this.getPathNodeType(navigater, x, y, z);
		float f = navigater.getPathPriority(pathnodetype);
		double d1 = player.width / 2.0;

		if (f >= 0.0F) {
			pathpoint = openPoint(x, y, z);
			pathpoint.nodeType = pathnodetype;
			pathpoint.costMalus = Math.max(pathpoint.costMalus, f);
		}

		if (pathnodetype == PathNodeType.WALKABLE) return pathpoint;
		if (pathpoint == null && stepheight > 0 && pathnodetype != PathNodeType.FENCE
				&& pathnodetype != PathNodeType.TRAPDOOR) {
			pathpoint = getSafePoint(x, y + 1, z, player.capabilities.allowFlying ? Integer.MAX_VALUE : stepheight - 1,
				p_186332_5_, facing);

			if (pathpoint != null
					&& (pathpoint.nodeType == PathNodeType.OPEN || pathpoint.nodeType == PathNodeType.WALKABLE)
					&& player.width < 1.0F) {
				double d2 = x - facing.getFrontOffsetX() + 0.5;
				double d3 = z - facing.getFrontOffsetZ() + 0.5;

				if (player.world.collidesWithAnyBlock(
					new AxisAlignedBB(d2 - d1, y + 0.001D, d3 - d1, d2 + d1, y + player.height, d3 + d1).addCoord(0.0,
						blockaccess.getBlockState(blockpos).getBoundingBox(blockaccess, blockpos).maxY - 0.002, 0.0)))
					pathpoint = null;
			}
		}

		if (pathnodetype == PathNodeType.OPEN) {
			if (player.world.collidesWithAnyBlock(new AxisAlignedBB(x - d1 + 0.5, y + 0.001, z - d1 + 0.5, x + d1 + 0.5,
				y + player.height, z + d1 + 0.5))) return null;

			if (player.width >= 1.0F && this.getPathNodeType(navigater, x, y - 1, z) == PathNodeType.BLOCKED) {
				pathpoint = openPoint(x, y, z);
				pathpoint.nodeType = PathNodeType.WALKABLE;
				pathpoint.costMalus = Math.max(pathpoint.costMalus, f);
				return pathpoint;
			}

			if (stepheight != Integer.MAX_VALUE) {
				int i = 0;
				while (y > 0 && pathnodetype == PathNodeType.OPEN) {
					--y;

					if (i++ >= player.getMaxFallHeight() && !player.capabilities.allowFlying) return null;

					pathnodetype = this.getPathNodeType(navigater, x, y, z);
					f = navigater.getPathPriority(pathnodetype);

					if (pathnodetype != PathNodeType.OPEN && f >= 0.0F) {
						pathpoint = openPoint(x, y, z);
						pathpoint.nodeType = pathnodetype;
						pathpoint.costMalus = Math.max(pathpoint.costMalus, f + i - 1);
						break;
					}

					if (f < 0.0F) return null;
				}
			} else {
				pathpoint = openPoint(x, y, z);
				pathpoint.nodeType = pathnodetype;
				pathpoint.costMalus = Math.max(pathpoint.costMalus, f);
			}
		}

		return pathpoint;
	}

	public static PathNodeType getPathNodeType(IBlockAccess blockaccess, int x, int y, int z,
			PathNavigatePlayer navigater, int xzSize, int ySize, boolean canBreakDoors, boolean canEnterDoors) {
		EnumSet<PathNodeType> enumset = EnumSet.<PathNodeType>noneOf(PathNodeType.class);
		PathNodeType pathnodetype = PathNodeType.BLOCKED;
		BlockPos blockpos = new BlockPos(navigater.player);

		for (int i = 0; i < xzSize; ++i) {
			for (int j = 0; j < ySize; ++j) {
				for (int k = 0; k < xzSize; ++k) {
					int l = i + x;
					int i1 = j + y;
					int j1 = k + z;
					PathNodeType pathnodetype1 = PlayerNodeProcessor.getPathNodeType(blockaccess, l, i1, j1);

					if (pathnodetype1 == PathNodeType.DOOR_WOOD_CLOSED && canBreakDoors && canEnterDoors)
						pathnodetype1 = PathNodeType.WALKABLE;

					if (pathnodetype1 == PathNodeType.DOOR_OPEN && !canEnterDoors) pathnodetype1 = PathNodeType.BLOCKED;

					if (pathnodetype1 == PathNodeType.RAIL
							&& !(blockaccess.getBlockState(blockpos).getBlock() instanceof BlockRailBase)
							&& !(blockaccess.getBlockState(blockpos.down()).getBlock() instanceof BlockRailBase)) {
						pathnodetype1 = PathNodeType.FENCE;
					}

					if (i == 0 && j == 0 && k == 0) pathnodetype = pathnodetype1;

					enumset.add(pathnodetype1);
				}
			}
		}

		if (enumset.contains(PathNodeType.FENCE)) return PathNodeType.FENCE;

		PathNodeType pathnodetype2 = PathNodeType.BLOCKED;

		for (PathNodeType pathnodetype3 : enumset) {
			if (navigater.getPathPriority(pathnodetype3) < 0.0F) return pathnodetype3;

			if (navigater.getPathPriority(pathnodetype3) >= navigater.getPathPriority(pathnodetype2))
				pathnodetype2 = pathnodetype3;
		}

		if (pathnodetype == PathNodeType.OPEN && navigater.getPathPriority(pathnodetype2) == 0.0F)
			return PathNodeType.OPEN;
		return pathnodetype2;
	}

	private PathNodeType getPathNodeType(PathNavigatePlayer navigater, BlockPos pos) {
		return this.getPathNodeType(navigater, pos.getX(), pos.getY(), pos.getZ());
	}

	private PathNodeType getPathNodeType(PathNavigatePlayer navigater, int x, int y, int z) {
		return PlayerNodeProcessor.getPathNodeType(blockaccess, x, y, z, navigater, playerSizeXZ, playerSizeY,
			getCanBreakDoors(), getCanEnterDoors());
	}

	public static PathNodeType getPathNodeType(IBlockAccess blockaccess, int x, int y, int z) {
		PathNodeType pathnodetype = getPathNodeTypeRaw(blockaccess, x, y, z);

		if (pathnodetype == PathNodeType.OPEN && y >= 1) {
			Block block = blockaccess.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
			PathNodeType pathnodetype1 = getPathNodeTypeRaw(blockaccess, x, y - 1, z);
			pathnodetype = pathnodetype1 != PathNodeType.WALKABLE && pathnodetype1 != PathNodeType.OPEN
					&& pathnodetype1 != PathNodeType.WATER && pathnodetype1 != PathNodeType.LAVA ? PathNodeType.WALKABLE
						: PathNodeType.OPEN;

			if (pathnodetype1 == PathNodeType.DAMAGE_FIRE || block == Blocks.MAGMA)
				pathnodetype = PathNodeType.DAMAGE_FIRE;

			if (pathnodetype1 == PathNodeType.DAMAGE_CACTUS) pathnodetype = PathNodeType.DAMAGE_CACTUS;
		}

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

		if (pathnodetype == PathNodeType.WALKABLE) {
			for (int j = -1; j <= 1; ++j) {
				for (int i = -1; i <= 1; ++i) {
					if (j != 0 || i != 0) {
						Block block1 = blockaccess.getBlockState(pos.setPos(j + x, y, i + z)).getBlock();

						if (block1 == Blocks.CACTUS) pathnodetype = PathNodeType.DANGER_CACTUS;
						else if (block1 == Blocks.FIRE) pathnodetype = PathNodeType.DANGER_FIRE;
						else if (block1.isBurning(blockaccess, pos)) pathnodetype = PathNodeType.DAMAGE_FIRE;
					}
				}
			}
		}

		pos.release();
		return pathnodetype;
	}

	private static PathNodeType getPathNodeTypeRaw(IBlockAccess p_189553_1_, int p_189553_2_, int p_189553_3_,
			int p_189553_4_) {
		BlockPos blockpos = new BlockPos(p_189553_2_, p_189553_3_, p_189553_4_);
		IBlockState iblockstate = p_189553_1_.getBlockState(blockpos);
		Block block = iblockstate.getBlock();
		Material material = iblockstate.getMaterial();
		PathNodeType type = block.getAiPathNodeType(iblockstate, p_189553_1_, blockpos);
		if (type != null) return type;
		return material == Material.AIR ? PathNodeType.OPEN
			: block != Blocks.TRAPDOOR && block != Blocks.IRON_TRAPDOOR && block != Blocks.WATERLILY
				? block == Blocks.FIRE ? PathNodeType.DAMAGE_FIRE
					: block == Blocks.CACTUS ? PathNodeType.DAMAGE_CACTUS
						: block instanceof BlockDoor && material == Material.WOOD
								&& !iblockstate.getValue(BlockDoor.OPEN).booleanValue()
									? PathNodeType.DOOR_WOOD_CLOSED
									: block instanceof BlockDoor && material == Material.IRON && !iblockstate.getValue(
										BlockDoor.OPEN).booleanValue()
											? PathNodeType.DOOR_IRON_CLOSED
											: block instanceof BlockDoor && iblockstate.getValue(
												BlockDoor.OPEN).booleanValue()
													? PathNodeType.DOOR_OPEN
													: block instanceof BlockRailBase ? PathNodeType.RAIL
														: !(block instanceof BlockFence)
																&& !(block instanceof BlockWall)
																&& (!(block instanceof BlockFenceGate) || iblockstate
																		.getValue(BlockFenceGate.OPEN).booleanValue())
																			? material == Material.WATER
																				? PathNodeType.WATER
																				: material == Material.LAVA
																					? PathNodeType.LAVA
																					: block.isPassable(p_189553_1_,
																						blockpos) ? PathNodeType.OPEN
																							: PathNodeType.BLOCKED
																			: PathNodeType.FENCE
				: PathNodeType.TRAPDOOR;
	}

	public void setCanEnterDoors(boolean canEnterDoors) {
		this.canEnterDoors = canEnterDoors;
	}

	public void setCanBreakDoors(boolean canBreakDoors) {
		this.canBreakDoors = canBreakDoors;
	}

	public void setCanSwim(boolean canSwim) {
		this.canSwim = canSwim;
	}

	public boolean getCanEnterDoors() {
		return canEnterDoors;
	}

	public boolean getCanBreakDoors() {
		return canBreakDoors;
	}

	public boolean getCanSwim() {
		return canSwim;
	}

}
