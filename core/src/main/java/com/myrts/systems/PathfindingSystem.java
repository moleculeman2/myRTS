package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.myrts.components.PathComponent;
import com.myrts.components.TargetDestinationComponent;
import com.myrts.components.TransformComponent;
import com.myrts.components.UnitComponent;
import com.myrts.map.FunnelSmoother;
import com.myrts.map.MapManager;
import com.myrts.map.TrianglePathfinder;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class PathfindingSystem extends IteratingSystem {

    private final MapManager mapManager;

    public PathfindingSystem(MapManager mapManager) {
        // Only process entities that have a Transform, a Unit definition, AND a Target Destination
        super(Family.all(TransformComponent.class, UnitComponent.class, TargetDestinationComponent.class).get());
        this.mapManager = mapManager;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        UnitComponent unit = entity.getComponent(UnitComponent.class);
        TargetDestinationComponent intent = entity.getComponent(TargetDestinationComponent.class);

        float unitRadius = unit.radius;
        float unitCenterX = transform.position.x + (transform.width / 2f);
        float unitCenterY = transform.position.y + (transform.height / 2f);

        DelaunayTriangle startTri = mapManager.getTriangleAt(unitCenterX, unitCenterY);
        DelaunayTriangle targetTri = mapManager.getTriangleAt(intent.target.x, intent.target.y);

        Vector2 startPos = new Vector2(unitCenterX, unitCenterY);
        Vector2 endPos = new Vector2(intent.target.x, intent.target.y);

        boolean clickedBlockedTile = false;
        if (targetTri == null) {
            targetTri = mapManager.getClosestWalkableTriangle(intent.target.x, intent.target.y, unitRadius);
            clickedBlockedTile = true;
        }

        if (startTri != null && targetTri != null) {
            // --- NEW: Pre-Clamp the destination if they clicked a building ---
            if (clickedBlockedTile) {
                snapToTriangleEdge(endPos, targetTri);
                relaxAwayFromWalls(endPos, targetTri, unitRadius);
            }

            Array<DelaunayTriangle> path = TrianglePathfinder.findPath(startTri, targetTri, startPos, endPos, unitRadius);

            if (path.size > 0) {
                if (path.peek() != targetTri) {
                    // --- NEW: Partial Path (Island) Fallback ---
                    snapToTriangleEdge(endPos, path.peek());
                    relaxAwayFromWalls(endPos, path.peek(), unitRadius);
                } else if (!clickedBlockedTile) {
                    // --- NEW: Perfect Path ---
                    // Even if the click was safe, we run the solver to ensure they didn't
                    // click so close to a wall that their shoulders will clip it!
                    relaxAwayFromWalls(endPos, path.peek(), unitRadius);
                }

                PathComponent pathComp = entity.getComponent(PathComponent.class);
                if (pathComp == null) {
                    pathComp = getEngine().createComponent(PathComponent.class);
                    entity.add(pathComp);
                }

                pathComp.waypoints.clear();
                pathComp.currentWaypointIndex = 0;
                pathComp.waypoints = FunnelSmoother.stringPull(path, startPos, endPos, unitRadius);
            }
        } else {
            System.out.println("Cannot find path. Start is off the NavMesh.");
        }

        // CRITICAL: Remove the intent component so we don't recalculate the path next frame!
        entity.remove(TargetDestinationComponent.class);
    }

    /**
     * If the user clicks off the mesh, this securely snaps the destination to the absolute
     * closest walkable edge, and nudges it microscopically inward so it's mathematically valid.
     */
    private void snapToTriangleEdge(Vector2 pos, DelaunayTriangle tri) {
        Vector2 originalPos = new Vector2(pos);
        float minDst2 = Float.MAX_VALUE;
        Vector2 closestEdgePoint = new Vector2();

        for (int i = 0; i < 3; i++) {
            Vector2 p1 = new Vector2(tri.points[i].getXf(), tri.points[i].getYf());
            Vector2 p2 = new Vector2(tri.points[(i + 1) % 3].getXf(), tri.points[(i + 1) % 3].getYf());

            Vector2 tempPoint = new Vector2();
            com.badlogic.gdx.math.Intersector.nearestSegmentPoint(p1, p2, originalPos, tempPoint);
            float dst2 = originalPos.dst2(tempPoint);

            if (dst2 < minDst2) {
                minDst2 = dst2;
                closestEdgePoint.set(tempPoint);
            }
        }

        pos.set(closestEdgePoint);

        // Nudge it slightly toward the centroid so floating point errors don't place it out of bounds
        float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
        float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
        Vector2 centroid = new Vector2(cx, cy);
        Vector2 nudge = new Vector2(centroid).sub(pos).nor().scl(0.1f);
        pos.add(nudge);
    }

    /**
     * THE RELAXATION SOLVER: Gathers every physical wall within 3 grid rings, and acts
     * like a physics engine to shove the destination point away until the unit perfectly fits.
     */
    private void relaxAwayFromWalls(Vector2 pos, DelaunayTriangle tri, float unitRadius) {
        if (unitRadius <= 0) return;

        Array<Vector2[]> walls = new Array<>();
        java.util.HashSet<DelaunayTriangle> visited = new java.util.HashSet<>();
        Array<DelaunayTriangle> queue = new Array<>();
        queue.add(tri);
        visited.add(tri);

        // 1. Flood-fill outward to gather all nearby walls (3 rings deep is plenty)
        int head = 0;
        for (int r = 0; r < 3; r++) {
            int currentQueueSize = queue.size;
            while (head < currentQueueSize) {
                DelaunayTriangle current = queue.get(head++);
                for (int i = 0; i < 3; i++) {
                    if (current.neighbors[i] == null) {
                        // It's a physical wall!
                        Vector2 p1 = new Vector2(current.points[(i + 1) % 3].getXf(), current.points[(i + 1) % 3].getYf());
                        Vector2 p2 = new Vector2(current.points[(i + 2) % 3].getXf(), current.points[(i + 2) % 3].getYf());
                        walls.add(new Vector2[]{p1, p2});
                    } else if (!visited.contains(current.neighbors[i])) {
                        visited.add(current.neighbors[i]);
                        queue.add(current.neighbors[i]);
                    }
                }
            }
        }

        // 2. The Physics Solver (Iteratively massage the point away from all local walls)
        float safeRadius = unitRadius * 1.05f; // 5% padding
        for (int iter = 0; iter < 10; iter++) {
            boolean moved = false;
            for (Vector2[] wall : walls) {
                Vector2 nearest = new Vector2();
                com.badlogic.gdx.math.Intersector.nearestSegmentPoint(wall[0], wall[1], pos, nearest);
                float dist = pos.dst(nearest);

                if (dist < safeRadius) {
                    Vector2 push = new Vector2(pos).sub(nearest);
                    if (push.len2() < 0.001f) {
                        float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
                        float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
                        push.set(cx, cy).sub(nearest); // Fallback push toward centroid
                    }
                    // Shove the point perfectly out of the collision radius
                    push.nor().scl(safeRadius - dist);
                    pos.add(push);
                    moved = true;
                }
            }
            if (!moved) break; // If it's safe from all walls, we're done early!
        }
    }
}
