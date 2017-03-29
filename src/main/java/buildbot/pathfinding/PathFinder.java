package main.java.buildbot.pathfinding;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathHeap;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class PathFinder {

	/** The path being generated */
	private final PathHeap path = new PathHeap();
	private final PathPoint[] pathOptions = new PathPoint[32];

	private final PlayerNodeProcessor nodeProcessor;

	public PathFinder(PlayerNodeProcessor processor) {
		nodeProcessor = processor;
	}

	@Nullable
	public Path findPath(IBlockAccess world, PathNavigatePlayer navigate, Entity target, float d) {
		return this.findPath(world, navigate, target.posX, target.getEntityBoundingBox().minY, target.posZ, d);
	}

	@Nullable
	public Path findPath(IBlockAccess world, PathNavigatePlayer navigate, BlockPos targetpos, float d) {
		return this.findPath(world, navigate, targetpos.getX() + 0.5F, targetpos.getY() + 0.5F, targetpos.getZ() + 0.5F,
			d);
	}

	@Nullable
	private synchronized Path findPath(IBlockAccess world, PathNavigatePlayer navigate, double x, double y, double z,
			float d) {
		path.clearPath();
		nodeProcessor.initProcessor(world, navigate);
		PathPoint pathpoint = nodeProcessor.getStart();
		PathPoint pathpoint1 = nodeProcessor.getPathPointToCoords(x, y, z);
		Path path = this.findPath(pathpoint, pathpoint1, d);
		nodeProcessor.postProcess();
		return path;
	}

	@Nullable
	private Path findPath(PathPoint p_186335_1_, PathPoint p_186335_2_, float p_186335_3_) {
		p_186335_1_.totalPathDistance = 0.0F;
		p_186335_1_.distanceToNext = p_186335_1_.distanceManhattan(p_186335_2_);
		p_186335_1_.distanceToTarget = p_186335_1_.distanceToNext;
		path.clearPath();
		path.addPoint(p_186335_1_);
		PathPoint pathpoint = p_186335_1_;
		int i = 0;

		while (!path.isPathEmpty()) {
			++i;

			if (i >= 200) break;

			PathPoint pathpoint1 = path.dequeue();

			if (pathpoint1.equals(p_186335_2_)) {
				pathpoint = p_186335_2_;
				break;
			}

			if (pathpoint1.distanceManhattan(p_186335_2_) < pathpoint.distanceManhattan(p_186335_2_))
				pathpoint = pathpoint1;

			pathpoint1.visited = true;
			int j = nodeProcessor.findPathOptions(pathOptions, pathpoint1, p_186335_2_, p_186335_3_);

			for (int k = 0; k < j; ++k) {
				PathPoint pathpoint2 = pathOptions[k];
				float f = pathpoint1.distanceManhattan(pathpoint2);
				pathpoint2.distanceFromOrigin = pathpoint1.distanceFromOrigin + f;
				pathpoint2.cost = f + pathpoint2.costMalus;
				float f1 = pathpoint1.totalPathDistance + pathpoint2.cost;

				if (pathpoint2.distanceFromOrigin < p_186335_3_
						&& (!pathpoint2.isAssigned() || f1 < pathpoint2.totalPathDistance)) {
					pathpoint2.previous = pathpoint1;
					pathpoint2.totalPathDistance = f1;
					pathpoint2.distanceToNext = pathpoint2.distanceManhattan(p_186335_2_) + pathpoint2.costMalus;

					if (pathpoint2.isAssigned())
						path.changeDistance(pathpoint2, pathpoint2.totalPathDistance + pathpoint2.distanceToNext);
					else {
						pathpoint2.distanceToTarget = pathpoint2.totalPathDistance + pathpoint2.distanceToNext;
						path.addPoint(pathpoint2);
					}
				}
			}
		}

		if (pathpoint == p_186335_1_) return null;
		Path path = createEntityPath(p_186335_1_, pathpoint);
		return path;
	}

	/**
	 * Returns a new PathEntity for a given start and end point
	 */
	private static Path createEntityPath(PathPoint start, PathPoint end) {
		int i = 1;

		for (PathPoint pathpoint = end; pathpoint.previous != null; pathpoint = pathpoint.previous) ++i;

		PathPoint[] apathpoint = new PathPoint[i];
		PathPoint pathpoint1 = end;
		--i;

		for (apathpoint[i] = end; pathpoint1.previous != null; apathpoint[i] = pathpoint1) {
			pathpoint1 = pathpoint1.previous;
			--i;
		}

		return new Path(apathpoint);
	}

}
