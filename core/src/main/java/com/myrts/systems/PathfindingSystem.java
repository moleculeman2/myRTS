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
            if (clickedBlockedTile) {
                clampToClosestValidPoint(endPos, targetTri, unitRadius);
            }

            Array<DelaunayTriangle> path = TrianglePathfinder.findPath(startTri, targetTri, startPos, endPos, unitRadius);

            if (path.size > 0) {
                if (path.peek() != targetTri) {
                    // It was a partial path (island). Clamp to the edge of the nearest valid triangle.
                    clampToClosestValidPoint(endPos, path.peek(), unitRadius);
                } else {
                    // --- THE FIX: Wedge into the corner! ---
                    // We successfully reached the target triangle. Ensure the exact click
                    // isn't going to make the unit's body clip into the local walls.
                    wedgeIntoCorner(endPos, path.peek(), unitRadius);
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
     * Finds the exact point on the triangle's perimeter closest to the blocked click,
     * then pushes it inward by the unit's radius so it perfectly hugs the wall without clipping!
     */
    private void clampToClosestValidPoint(Vector2 pos, DelaunayTriangle tri, float unitRadius) {
        Vector2 originalPos = new Vector2(pos);
        float minDst2 = Float.MAX_VALUE;
        Vector2 closestPoint = new Vector2();
        Vector2 tempPoint = new Vector2();

        // 1. Find the absolute closest point on the 3 edges of the triangle
        for (int i = 0; i < 3; i++) {
            Vector2 p1 = new Vector2(tri.points[i].getXf(), tri.points[i].getYf());
            Vector2 p2 = new Vector2(tri.points[(i + 1) % 3].getXf(), tri.points[(i + 1) % 3].getYf());

            com.badlogic.gdx.math.Intersector.nearestSegmentPoint(p1, p2, originalPos, tempPoint);
            float dst2 = originalPos.dst2(tempPoint);

            if (dst2 < minDst2) {
                minDst2 = dst2;
                closestPoint.set(tempPoint);
            }
        }

        // 2. Push the point inward away from the wall so the unit's radius fits!
        if (unitRadius > 0) {
            float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
            float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
            Vector2 centroid = new Vector2(cx, cy);

            // Create a vector pointing from the wall edge toward the safe center of the triangle
            Vector2 pushDirection = new Vector2(centroid).sub(closestPoint).nor();

            // Push the point inward by the radius + 10% padding so it doesn't snag the wall
            closestPoint.mulAdd(pushDirection, unitRadius * 1.1f);
        }

        pos.set(closestPoint);
    }

    /**
     * Ensures the final destination is at least 'unitRadius' away from any physical walls
     * inside the final triangle, effectively "wedging" the unit snugly into corners.
     */
    private void wedgeIntoCorner(Vector2 pos, DelaunayTriangle tri, float unitRadius) {
        if (unitRadius <= 0) return;

        // Run 3 iterations to gently resolve sharp corners (pushing off wall A might push into wall B)
        for (int iteration = 0; iteration < 3; iteration++) {
            for (int i = 0; i < 3; i++) {
                if (tri.neighbors[i] == null) { // This edge is a physical wall
                    Vector2 p1 = new Vector2(tri.points[(i + 1) % 3].getXf(), tri.points[(i + 1) % 3].getYf());
                    Vector2 p2 = new Vector2(tri.points[(i + 2) % 3].getXf(), tri.points[(i + 2) % 3].getYf());

                    Vector2 nearestPoint = new Vector2();
                    com.badlogic.gdx.math.Intersector.nearestSegmentPoint(p1, p2, pos, nearestPoint);

                    float dist = pos.dst(nearestPoint);

                    // If we are closer to the wall than our radius, we are clipping!
                    if (dist < unitRadius) {
                        Vector2 pushOut = new Vector2(pos).sub(nearestPoint);

                        // Fallback if the click was exactly ON the wall line
                        if (pushOut.len2() < 0.001f) {
                            float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
                            float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
                            pushOut.set(cx, cy).sub(nearestPoint);
                        }

                        pushOut.nor();
                        // Push it exactly to the radius boundary (plus 1% padding for float safety)
                        float pushAmount = (unitRadius - dist) * 1.01f;
                        pos.add(pushOut.scl(pushAmount));
                    }
                }
            }
        }
    }
}
