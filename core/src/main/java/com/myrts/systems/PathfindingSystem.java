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

        // 1. Initial Localization
        DelaunayTriangle startTri = mapManager.getTriangleAt(unitCenterX, unitCenterY);
        DelaunayTriangle targetTri = mapManager.getTriangleAt(intent.target.x, intent.target.y);

        Vector2 startPos = new Vector2(unitCenterX, unitCenterY);
        Vector2 endPos = new Vector2(intent.target.x, intent.target.y);

        // 2. Resolve Blocked Clicks
        boolean wasBlocked = false;
        if (targetTri == null) {
            targetTri = mapManager.getClosestWalkableTriangle(endPos.x, endPos.y, unitRadius);
            wasBlocked = true;
        }

        if (startTri != null && targetTri != null) {

            // 3. Pre-Process the Destination (The Fix!)
            // Always relax the destination BEFORE A* so it is perfectly safe.
            if (wasBlocked) {
                snapToTriangleEdge(endPos, targetTri);
            }
            relaxAwayFromWalls(endPos, targetTri, unitRadius);

            // --- THE BOUNDARY OVER-PUSH FIX ---
            // Because relaxation moved the physical coordinate of endPos, it may have been pushed
            // across a portal into a totally different triangle! We MUST re-sample the NavMesh here.
            DelaunayTriangle relaxedTri = mapManager.getTriangleAt(endPos.x, endPos.y);
            if (relaxedTri != null) {
                targetTri = relaxedTri;
            }

            // 4. Run A* Pathfinding
            Array<DelaunayTriangle> path = TrianglePathfinder.findPath(startTri, targetTri, startPos, endPos, unitRadius);

            if (path.size > 0) {

                // 5. Handle Islands (Partial Paths)
                if (path.peek() != targetTri) {
                    snapToTriangleEdge(endPos, path.peek());
                    relaxAwayFromWalls(endPos, path.peek(), unitRadius);

                    // If the island fallback pushes the point backward into a previous triangle on the path,
                    // we pop the invalid triangles off the path so the Funnel doesn't loop through them!
                    relaxedTri = mapManager.getTriangleAt(endPos.x, endPos.y);
                    if (relaxedTri != null && path.contains(relaxedTri, true)) {
                        while (path.peek() != relaxedTri) {
                            path.pop();
                        }
                    }
                }

                // 6. Generate Waypoints
                PathComponent pathComp = entity.getComponent(PathComponent.class);
                if (pathComp == null) {
                    pathComp = getEngine().createComponent(PathComponent.class);
                    entity.add(pathComp);
                }

                pathComp.waypoints.clear();
                pathComp.currentWaypointIndex = 0;
                pathComp.waypoints = FunnelSmoother.stringPull(path, startPos, endPos, unitRadius);

                // 7. Relax Intermediate Corners
                for (int i = 1; i < pathComp.waypoints.size - 1; i++) {
                    Vector2 wp = pathComp.waypoints.get(i);
                    DelaunayTriangle wpTri = mapManager.getTriangleAt(wp.x, wp.y);
                    if (wpTri == null) wpTri = mapManager.getClosestWalkableTriangle(wp.x, wp.y, unitRadius);
                    if (wpTri != null) relaxAwayFromWalls(wp, wpTri, unitRadius);
                }
            }
        }

        entity.remove(TargetDestinationComponent.class);
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

        // 1. Flood-fill outward to gather all nearby walls (3 rings deep)
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

        // 2. The Physics Solver
        float safeRadius = unitRadius * 1.05f; // 5% padding

        // Pre-calculate centroid to determine which side of the wall is "inside" the triangle
        float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
        float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
        Vector2 centroid = new Vector2(cx, cy);

        for (int iter = 0; iter < 10; iter++) {
            boolean moved = false;
            for (Vector2[] wall : walls) {
                Vector2 nearest = new Vector2();
                com.badlogic.gdx.math.Intersector.nearestSegmentPoint(wall[0], wall[1], pos, nearest);
                float dist = pos.dst(nearest);

                if (dist < safeRadius) {
                    Vector2 push = new Vector2(pos).sub(nearest);

                    // --- THE FLAWLESS ZERO-DISTANCE FIX ---
                    if (dist < 0.001f) {
                        // The point is EXACTLY on the wall line. Calculate the Surface Normal!
                        Vector2 wallDir = new Vector2(wall[1]).sub(wall[0]).nor();
                        Vector2 normal = new Vector2(-wallDir.y, wallDir.x);

                        // Ensure the normal points INWARD toward the safe center of the triangle
                        Vector2 toCenter = new Vector2(centroid).sub(nearest);
                        if (normal.dot(toCenter) < 0) {
                            normal.scl(-1);
                        }
                        push.set(normal);
                    } else {
                        push.nor();
                    }
                    // --------------------------------------

                    // Shove the point perfectly out of the collision radius
                    push.scl(safeRadius - dist);
                    pos.add(push);
                    moved = true;
                }
            }
            if (!moved) break; // If it's safe from all walls, we're done early!
        }
    }

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
        float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
        float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
        Vector2 nudge = new Vector2(cx, cy).sub(pos).nor().scl(0.1f);
        pos.add(nudge);
    }
}
