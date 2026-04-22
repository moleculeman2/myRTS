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

                // --- THE "THICK LINE OF SIGHT" FIX ---
                // 6.5 Gather all physical corner vertices from the A* path triangles
                java.util.HashSet<org.poly2tri.triangulation.TriangulationPoint> wallPoints = new java.util.HashSet<>();
                for (DelaunayTriangle tri : path) {
                    for (int i = 0; i < 3; i++) {
                        if (tri.neighbors[i] == null) {
                            // This edge is a physical wall. Log its vertices!
                            wallPoints.add(tri.points[(i + 1) % 3]);
                            wallPoints.add(tri.points[(i + 2) % 3]);
                        }
                    }
                }

                float safeRadius = unitRadius + 0.005f;
                boolean pathModified;
                int injectionLimit = 20; // Safeguard against weird float-precision infinite loops

                // Repeatedly scan and bend the path until it clears all corners
                do {
                    pathModified = false;
                    for (int i = 0; i < pathComp.waypoints.size - 1; i++) {
                        Vector2 wp1 = pathComp.waypoints.get(i);
                        Vector2 wp2 = pathComp.waypoints.get(i + 1);

                        Vector2 worstClipPoint = null;
                        float worstClipDepth = -1;

                        // Check if this line segment grazes any wall vertices
                        for (org.poly2tri.triangulation.TriangulationPoint tp : wallPoints) {
                            Vector2 v = new Vector2(tp.getXf(), tp.getYf());
                            Vector2 nearest = new Vector2();
                            com.badlogic.gdx.math.Intersector.nearestSegmentPoint(wp1, wp2, v, nearest);

                            // We only care if it clips strictly in the middle of the segment
                            if (nearest.dst(wp1) > 0.01f && nearest.dst(wp2) > 0.01f) {
                                float dist = v.dst(nearest);
                                if (dist < safeRadius) {
                                    float depth = safeRadius - dist;
                                    if (depth > worstClipDepth) {
                                        worstClipDepth = depth;
                                        worstClipPoint = nearest;
                                    }
                                }
                            }
                        }

                        if (worstClipPoint != null) {
                            // 1. Inject the point where the unit would have clipped the corner
                            pathComp.waypoints.insert(i + 1, worstClipPoint);

                            // 2. Immediately push it out to the safe equilibrium radius
                            DelaunayTriangle tri = mapManager.getTriangleAt(worstClipPoint.x, worstClipPoint.y);
                            if (tri == null) tri = mapManager.getClosestWalkableTriangle(worstClipPoint.x, worstClipPoint.y, unitRadius);
                            if (tri != null) {
                                relaxAwayFromWalls(pathComp.waypoints.get(i + 1), tri, unitRadius);
                            }

                            // 3. Restart the scan so we can verify the two newly bent segments
                            pathModified = true;
                            break;
                        }
                    }
                    injectionLimit--;
                } while (pathModified && injectionLimit > 0);

                // 7. Relax the ORIGINAL Intermediate Corners (from the Funnel)
                for (int i = 1; i < pathComp.waypoints.size - 1; i++) {
                    Vector2 wp = pathComp.waypoints.get(i);
                    DelaunayTriangle wpTri = mapManager.getTriangleAt(wp.x, wp.y);
                    if (wpTri == null) wpTri = mapManager.getClosestWalkableTriangle(wp.x, wp.y, unitRadius);
                    if (wpTri != null) relaxAwayFromWalls(wp, wpTri, unitRadius);
                }

                // --- THE "ALREADY THERE" PRUNING FIX ---
                // 8. Clean up redundant starting waypoints
                float clearRadius = unitRadius * 1.5f; // Slightly larger than the unit's body

                // If the path has multiple points, and we are practically already sitting on
                // the first waypoint (or it spawned slightly behind us), throw it in the trash!
                while (pathComp.waypoints.size > 1) {
                    Vector2 firstWaypoint = pathComp.waypoints.get(0);

                    if (startPos.dst(firstWaypoint) < clearRadius) {
                        // The waypoint is underneath or slightly behind the unit. Remove it!
                        pathComp.waypoints.removeIndex(0);
                    } else {
                        // The first waypoint is sufficiently far ahead in front of us.
                        break;
                    }
                }

                // --- THE "MICRO-STUTTER" FIX (Deduplication) ---
                // 9. Clean up clustered waypoints caused by physics convergence
                // We run this after all relaxing and pruning is completely finished.
                for (int i = 0; i < pathComp.waypoints.size - 1; i++) {
                    Vector2 current = pathComp.waypoints.get(i);
                    Vector2 next = pathComp.waypoints.get(i + 1);

                    // If the physics solver pushed two waypoints into the same physical space
                    // (e.g., closer than half the unit's radius), delete the redundant one!
                    if (current.dst(next) < unitRadius * 0.5f) {
                        pathComp.waypoints.removeIndex(i + 1);

                        // We must step back one index because the list just shrank,
                        // and we need to compare the current point to the NEW next point!
                        i--;
                    }
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

        // 1. Deep Flood Fill (8 Rings)
        int head = 0;
        for (int r = 0; r < 4; r++) {
            int currentQueueSize = queue.size;
            while (head < currentQueueSize) {
                DelaunayTriangle current = queue.get(head++);
                for (int i = 0; i < 3; i++) {
                    if (current.neighbors[i] == null) {
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

        // 2. The Physics Solver (Bounded Planes + Equilibrium Averaging)
        float safeRadius = unitRadius + 0.005f;

        for (int iter = 0; iter < 15; iter++) {
            Vector2 totalPush = new Vector2();
            int pushCount = 0;

            for (Vector2[] wall : walls) {
                Vector2 wallDir = new Vector2(wall[1]).sub(wall[0]).nor();
                Vector2 safeNormal = new Vector2(-wallDir.y, wallDir.x);

                Vector2 toPos = new Vector2(pos).sub(wall[0]);
                float planeDist = toPos.dot(safeNormal);

                // Check if we are violating the perpendicular plane
                if (planeDist < safeRadius) {

                    float segmentLen = wall[0].dst(wall[1]);
                    // Project the unit's position along the length of the wall itself
                    float proj = toPos.dot(wallDir);

                    // THE FIX: Bounded Planes!
                    // The plane ONLY pushes if the unit is directly parallel to the segment,
                    // or within a tiny 'safeRadius' wrap-around distance of its corners.
                    if (proj > -safeRadius && proj < segmentLen + safeRadius) {
                        float penetration = safeRadius - planeDist;

                        // Prevent massive teleportation from stray geometry bounds
                        if (penetration > 0 && penetration < safeRadius * 2f) {
                            Vector2 push = new Vector2(safeNormal).scl(penetration);
                            totalPush.add(push);
                            pushCount++;
                        }
                    }
                }
            }

            if (pushCount > 0) {
                // THE EQUILIBRIUM FIX: Averaging the pushes ensures that if a unit is
                // crammed into a 1x1 triangle smaller than itself, it gracefully
                // settles in the exact center instead of ping-ponging indefinitely.
                totalPush.scl(1f / pushCount);
                pos.add(totalPush);
            } else {
                break; // Perfectly clear of all relevant local geometry!
            }
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
