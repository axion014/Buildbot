package main.java.buildbot.pathfinding;

import java.util.Map;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;

import main.java.buildbot.Buildbot;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.math.*;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

public class PathNavigatePlayer {

	static final Logger LOGGER = LogManager.getLogger();
	protected double speed;
	private boolean tryUpdatePath;
	private long lastTimeUpdated;
	/** The PathEntity being followed. */
	@Nullable
	protected Path currentPath;
	/** Time, in number of ticks, following the current path */
	private int totalTicks;
	/**
	 * The time when the last position check was done (to detect successful
	 * movement)
	 */
	private int ticksAtLastPos;
	/**
	 * Coordinates of the entity's position last time a check was done (part of
	 * monitoring getting 'stuck')
	 */
	private Vec3d lastPosCheck = Vec3d.ZERO;
	private Vec3d timeoutCachedNode = Vec3d.ZERO;
	private long timeoutTimer;
	private long lastTimeoutCheck;
	private double timeoutLimit;
	private float maxDistanceToWaypoint = 0.5F;
	protected PlayerNodeProcessor nodeProcessor;
	private BlockPos targetPos;
	protected EntityPlayer player;
	protected World world;
	private final PathFinder pathFinder;
	private final Map<PathNodeType, Float> mapPathPriority = Maps.newEnumMap(PathNodeType.class);
	private PlayerMoveHelper moveHelper;
	private PlayerJumpHelper jumpHelper;
	private PlayerLookHelper lookHelper;

	float getPathPriority(PathNodeType nodeType) {
		Float f = mapPathPriority.get(nodeType);
		return f == null ? nodeType.getPriority() : f.floatValue();
	}

	void setPathPriority(PathNodeType nodeType, float priority) {
		mapPathPriority.put(nodeType, Float.valueOf(priority));
	}

	public PathNavigatePlayer(EntityPlayer player, World world) {
		this.player = player;
		this.world = world;
		pathFinder = getPathFinder();
		moveHelper = new PlayerMoveHelper(this);
		lookHelper = new PlayerLookHelper(this);
		jumpHelper = new PlayerJumpHelper(player);
	}

	public void update() {
		onUpdateNavigation();
		moveHelper.onUpdateMoveHelper();
		lookHelper.onUpdateLook();
		jumpHelper.doJump();
	}

	protected PathFinder getPathFinder() {
		nodeProcessor = new PlayerNodeProcessor();
		nodeProcessor.setCanEnterDoors(true);
		return new PathFinder(nodeProcessor);
	}

	/**
	 * Sets the speed
	 */
	public void setSpeed(double speed) {
		this.speed = speed;
	}

	/**
	 * Returns true if path can be changed by
	 * {@link net.minecraft.pathfinding.PathNavigate#onUpdateNavigation()
	 * onUpdateNavigation()}
	 */
	public boolean canUpdatePathOnTimeout() {
		return tryUpdatePath;
	}

	public void updatePath() {
		if (world.getTotalWorldTime() - lastTimeUpdated > 20L) {
			synchronized (this) {
				if (targetPos != null) {
					currentPath = null;
					currentPath = getPathToPos(targetPos);
					lastTimeUpdated = world.getTotalWorldTime();
					tryUpdatePath = false;
				}
			}
		} else tryUpdatePath = true;
	}

	/**
	 * Returns the path to the given coordinates. Args : x, y, z
	 */
	@Nullable
	public final Path getPathToXYZ(double x, double y, double z) {
		return getPathToPos(new BlockPos(x, y, z));
	}

	/**
	 * Returns path to given BlockPos
	 */
	@Nullable
	public Path getPathToPos(BlockPos pos) {
		if (!canNavigate()) return null;
		if (currentPath != null && !currentPath.isFinished() && pos.equals(targetPos)) return currentPath;
		targetPos = pos;
		world.theProfiler.startSection("pathfind");
		BlockPos blockpos = new BlockPos(player);
		int i = 10;
		ChunkCache chunkcache = new ChunkCache(world, blockpos.add(-i, -i, -i), blockpos.add(i, i, i), 0);
		Path path = pathFinder.findPath(chunkcache, this, targetPos, 200.0F);
		world.theProfiler.endSection();

		if (path != null) LOGGER.trace(Buildbot.smartString(path));

		return path;
	}

	/**
	 * Returns the path to the given EntityLiving. Args : entity
	 */
	@Nullable
	public Path getPathToEntityLiving(Entity entityIn) {
		if (!canNavigate()) return null;
		BlockPos blockpos = new BlockPos(entityIn);

		if (currentPath != null && !currentPath.isFinished() && blockpos.equals(targetPos)) return currentPath;
		targetPos = blockpos;
		world.theProfiler.startSection("pathfind");
		BlockPos blockpos1 = new BlockPos(player).up();
		int i = 18;
		ChunkCache chunkcache = new ChunkCache(world, blockpos1.add(-i, -i, -i), blockpos1.add(i, i, i), 0);
		Path path = pathFinder.findPath(chunkcache, this, entityIn, 2.0F);
		world.theProfiler.endSection();
		return path;
	}

	/**
	 * Try to find and set a path to XYZ. Returns true if successful. Args : x,
	 * y, z, speed
	 */
	public boolean tryMoveToXYZ(double x, double y, double z, double speed) {
		return setPath(getPathToXYZ(x, y, z), speed);
	}

	/**
	 * Try to find and set a path to EntityLiving. Returns true if successful.
	 * Args : entity, speed
	 */
	public boolean tryMoveToEntityLiving(Entity entity, double speed) {
		Path path = getPathToEntityLiving(entity);
		return path != null && setPath(path, speed);
	}

	/**
	 * Sets a new path. If it's diferent from the old path. Checks to adjust
	 * path for sun avoiding, and stores start coords. Args : path, speed
	 */
	public boolean setPath(@Nullable Path pathentity, double speed) {
		if (pathentity == null) {
			currentPath = null;
			return false;
		}
		if (!pathentity.isSamePath(currentPath)) currentPath = pathentity;

		if (currentPath.getCurrentPathLength() == 0) return false;
		this.speed = speed;
		Vec3d vec3d = getEntityPosition();
		ticksAtLastPos = totalTicks;
		lastPosCheck = vec3d;
		return true;
	}

	/**
	 * gets the actively used PathEntity
	 */
	@Nullable
	public Path getPath() {
		return currentPath;
	}

	public void onUpdateNavigation() {
		++totalTicks;

		if (tryUpdatePath) updatePath();

		if (!noPath()) {
			synchronized (this) {
				if (canNavigate()) {
					pathFollow();
				} else if (currentPath != null
						&& currentPath.getCurrentPathIndex() < currentPath.getCurrentPathLength()) {
					Vec3d vec3d = getEntityPosition();
					Vec3d vec3d1 = currentPath.getVectorFromIndex(player, currentPath.getCurrentPathIndex());

					if (vec3d.yCoord > vec3d1.yCoord && !player.onGround
							&& MathHelper.floor(vec3d.xCoord) == MathHelper.floor(vec3d1.xCoord)
							&& MathHelper.floor(vec3d.zCoord) == MathHelper.floor(vec3d1.zCoord)) {
						currentPath.setCurrentPathIndex(currentPath.getCurrentPathIndex() + 1);
					}
				}

				if (!noPath()) {
					Vec3d vec3d2 = currentPath.getPosition(player);

					if (vec3d2 != null) {
						BlockPos blockpos = new BlockPos(vec3d2).down();
						vec3d2 = vec3d2.subtract(0.0,
							1.0 - world.getBlockState(blockpos).getBoundingBox(world, blockpos).maxY, 0.0);
						moveHelper.setMoveTo(vec3d2.xCoord, vec3d2.yCoord, vec3d2.zCoord, speed);
					}
				}
			}
		}
	}

	protected void pathFollow() {
		Vec3d vec3d = getEntityPosition();
		int i = currentPath.getCurrentPathLength();

		for (int j = currentPath.getCurrentPathIndex(); j < currentPath.getCurrentPathLength(); ++j) {
			if (currentPath.getPathPointFromIndex(j).yCoord != Math.floor(vec3d.yCoord)) {
				i = j;
				break;
			}
		}

		maxDistanceToWaypoint = player.width > 0.75F ? player.width / 2.0F : 0.75F - player.width / 2.0F;
		Vec3d vec3d1 = currentPath.getCurrentPos();

		if (MathHelper.abs((float) (player.posX - (vec3d1.xCoord + 0.5D))) < maxDistanceToWaypoint
				&& MathHelper.abs((float) (player.posZ - (vec3d1.zCoord + 0.5D))) < maxDistanceToWaypoint
				&& MathHelper.abs((float) (player.posY - vec3d1.yCoord)) < 1.0D) {
			currentPath.setCurrentPathIndex(currentPath.getCurrentPathIndex() + 1);
			if (currentPath.getCurrentPathIndex() == currentPath.getCurrentPathLength() - 2)
				KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindSprint.getKeyCode(), false);
		}

		int k = MathHelper.ceil(player.width);
		int l = MathHelper.ceil(player.height);

		for (int j1 = i - 1; j1 >= currentPath.getCurrentPathIndex(); --j1) {
			if (isDirectPathBetweenPoints(vec3d, currentPath.getVectorFromIndex(player, j1), k, l)) {
				currentPath.setCurrentPathIndex(j1);
				break;
			}
		}

		checkForStuck(vec3d);
	}

	/**
	 * Checks if entity haven't been moved when last checked and if so, clears
	 * current {@link net.minecraft.pathfinding.PathEntity}
	 */
	protected void checkForStuck(Vec3d positionVec3) {
		if (totalTicks - ticksAtLastPos > 100) {
			if (positionVec3.squareDistanceTo(lastPosCheck) < 2.25D) clearPathEntity();

			ticksAtLastPos = totalTicks;
			lastPosCheck = positionVec3;
		}

		if (currentPath != null && !currentPath.isFinished()) {
			Vec3d vec3d = currentPath.getCurrentPos();

			if (vec3d.equals(timeoutCachedNode)) {
				timeoutTimer += System.currentTimeMillis() - lastTimeoutCheck;
			} else {
				timeoutCachedNode = vec3d;
				double d0 = positionVec3.distanceTo(timeoutCachedNode);
				timeoutLimit = player.getAIMoveSpeed() > 0.0F ? d0 / player.getAIMoveSpeed() * 1000.0D : 0.0D;
			}

			if (timeoutLimit > 0.0D && timeoutTimer > timeoutLimit * 3.0D) {
				timeoutCachedNode = Vec3d.ZERO;
				timeoutTimer = 0L;
				timeoutLimit = 0.0D;
				clearPathEntity();
			}

			lastTimeoutCheck = System.currentTimeMillis();
		}
	}

	/**
	 * If null path or reached the end
	 */
	public boolean noPath() {
		return currentPath == null || currentPath.isFinished();
	}

	/**
	 * sets active PathEntity to null
	 */
	public synchronized void clearPathEntity() {
		currentPath = null;
	}

	protected Vec3d getEntityPosition() {
		return new Vec3d(player.posX, getPathablePosY(), player.posZ);
	}

	/**
	 * Gets the safe pathing Y position for the entity depending on if it can
	 * path swim or not
	 */
	private int getPathablePosY() {
		if (player.isInWater() && getCanSwim()) {
			int i = (int) player.getEntityBoundingBox().minY;
			Block block = world
					.getBlockState(new BlockPos(MathHelper.floor(player.posX), i, MathHelper.floor(player.posZ)))
					.getBlock();
			int j = 0;

			while (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
				++i;
				block = world
						.getBlockState(new BlockPos(MathHelper.floor(player.posX), i, MathHelper.floor(player.posZ)))
						.getBlock();
				++j;

				if (j > 16) return (int) player.getEntityBoundingBox().minY;
			}

			return i;
		}
		return (int) (player.getEntityBoundingBox().minY + 0.5D);
	}

	/**
	 * Returns true if the entity is in water or lava, false otherwise
	 */
	protected boolean isInLiquid() {
		return player.isInWater() || player.isInLava();
	}

	/**
	 * If on ground or swimming and can swim
	 */
	protected boolean canNavigate() {
		return player.onGround || player.capabilities.allowFlying || getCanSwim() && isInLiquid() || player.isRiding();
	}

	/**
	 * Checks if the specified entity can safely walk to the specified location.
	 */
	protected boolean isDirectPathBetweenPoints(Vec3d posVec31, Vec3d posVec32, int sizeXZ, int sizeY) {
		int i = MathHelper.floor(posVec31.xCoord);
		int j = MathHelper.floor(posVec31.zCoord);
		double d0 = posVec32.xCoord - posVec31.xCoord;
		double d1 = posVec32.zCoord - posVec31.zCoord;
		double d2 = d0 * d0 + d1 * d1;

		if (d2 < 1.0E-8D) return false;
		double d3 = 1.0D / Math.sqrt(d2);
		d0 = d0 * d3;
		d1 = d1 * d3;
		sizeXZ += 2;

		if (!isSafeToStandAt(i, (int) posVec31.yCoord, j, sizeXZ, sizeY, posVec31, d0, d1)) return false;
		sizeXZ -= 2;
		double d4 = 1.0D / Math.abs(d0);
		double d5 = 1.0D / Math.abs(d1);
		double d6 = i - posVec31.xCoord;
		double d7 = j - posVec31.zCoord;

		if (d0 >= 0.0D) ++d6;
		if (d1 >= 0.0D) ++d7;

		d6 = d6 / d0;
		d7 = d7 / d1;
		int k = d0 < 0.0D ? -1 : 1;
		int l = d1 < 0.0D ? -1 : 1;
		int i1 = MathHelper.floor(posVec32.xCoord);
		int j1 = MathHelper.floor(posVec32.zCoord);
		int k1 = i1 - i;
		int l1 = j1 - j;

		while (k1 * k > 0 || l1 * l > 0) {
			if (d6 < d7) {
				d6 += d4;
				i += k;
				k1 = i1 - i;
			} else {
				d7 += d5;
				j += l;
				l1 = j1 - j;
			}

			if (!isSafeToStandAt(i, (int) posVec31.yCoord, j, sizeXZ, sizeY, posVec31, d0, d1)) return false;
		}

		return true;
	}

	/**
	 * Returns true when an entity could stand at a position, including solid
	 * blocks under the entire entity.
	 */
	private boolean isSafeToStandAt(int x, int y, int z, int sizeXZ, int sizeY, Vec3d vec31, double p_179683_8_,
			double p_179683_10_) {
		int i = x - sizeXZ / 2;
		int j = z - sizeXZ / 2;

		if (!isPositionClear(i, y, j, sizeXZ, sizeY, sizeXZ, vec31, p_179683_8_, p_179683_10_)) return false;
		for (int k = i; k < i + sizeXZ; ++k) {
			for (int l = j; l < j + sizeXZ; ++l) {
				double d0 = k + 0.5D - vec31.xCoord;
				double d1 = l + 0.5D - vec31.zCoord;

				if (d0 * p_179683_8_ + d1 * p_179683_10_ >= 0.0D) {
					PathNodeType pathnodetype = PlayerNodeProcessor.getPathNodeType(world, k, y - 1, l, this, sizeXZ,
						sizeY, true, true);

					if (pathnodetype == PathNodeType.WATER) return false;
					if (pathnodetype == PathNodeType.LAVA) return false;
					if (pathnodetype == PathNodeType.OPEN) return false;

					pathnodetype = PlayerNodeProcessor.getPathNodeType(world, k, y, l, this, sizeXZ, sizeY, true, true);
					float f = getPathPriority(pathnodetype);

					if (f < 0.0F || f >= 8.0F) return false;

					if (pathnodetype == PathNodeType.DAMAGE_FIRE || pathnodetype == PathNodeType.DANGER_FIRE
							|| pathnodetype == PathNodeType.DAMAGE_OTHER)
						return false;
				}
			}
		}

		return true;
	}

	/**
	 * Returns true if an entity does not collide with any solid blocks at the
	 * position.
	 */
	private boolean isPositionClear(int p_179692_1_, int p_179692_2_, int p_179692_3_, int p_179692_4_, int p_179692_5_,
			int p_179692_6_, Vec3d p_179692_7_, double p_179692_8_, double p_179692_10_) {
		for (BlockPos blockpos : BlockPos.getAllInBox(new BlockPos(p_179692_1_, p_179692_2_, p_179692_3_), new BlockPos(
			p_179692_1_ + p_179692_4_ - 1, p_179692_2_ + p_179692_5_ - 1, p_179692_3_ + p_179692_6_ - 1))) {
			double d0 = blockpos.getX() + 0.5D - p_179692_7_.xCoord;
			double d1 = blockpos.getZ() + 0.5D - p_179692_7_.zCoord;

			if (d0 * p_179692_8_ + d1 * p_179692_10_ >= 0.0D) {
				Block block = world.getBlockState(blockpos).getBlock();

				if (!block.isPassable(world, blockpos)) return false;
			}
		}

		return true;
	}

	public boolean canEntityStandOnPos(BlockPos pos) {
		return world.getBlockState(pos.down()).isFullBlock();
	}

	public void setCanSwim(boolean canSwim) {
		nodeProcessor.setCanSwim(canSwim);
	}

	public boolean getCanSwim() {
		return nodeProcessor.getCanSwim();
	}

	public PlayerNodeProcessor getNodeProcessor() {
		return nodeProcessor;
	}

	public PlayerLookHelper getLookHelper() {
		return lookHelper;
	}

	public PlayerJumpHelper getJumpHelper() {
		return jumpHelper;
	}

	public PlayerMoveHelper getMoveHelper() {
		return moveHelper;
	}
}
