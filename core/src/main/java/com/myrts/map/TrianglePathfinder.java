package com.myrts.map;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import org.poly2tri.triangulation.TriangulationPoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

public class TrianglePathfinder {

    private static class NodeRecord implements Comparable<NodeRecord> {
        DelaunayTriangle triangle;
        float costSoFar;
        float estimatedTotalCost;
        DelaunayTriangle parent;
        Vector2 entryPoint; // NEW: Tracks where the unit physically entered this triangle

        public NodeRecord(DelaunayTriangle triangle, float costSoFar, float estimatedTotalCost, DelaunayTriangle parent, Vector2 entryPoint) {
            this.triangle = triangle;
            this.costSoFar = costSoFar;
            this.estimatedTotalCost = estimatedTotalCost;
            this.parent = parent;
            this.entryPoint = entryPoint;
        }

        @Override
        public int compareTo(NodeRecord other) {
            return Float.compare(this.estimatedTotalCost, other.estimatedTotalCost);
        }
    }

    public static Array<DelaunayTriangle> findPath
        (DelaunayTriangle startTri, DelaunayTriangle endTri, Vector2 startPos, Vector2 endPos, float unitRadius) {
        Array<DelaunayTriangle> path = new Array<>();
        if (startTri == null || endTri == null) return path;

        if (startTri == endTri) {
            path.add(startTri);
            return path;
        }

        PriorityQueue<NodeRecord> openList = new PriorityQueue<>();
        HashMap<DelaunayTriangle, NodeRecord> nodeRecords = new HashMap<>();
        HashSet<DelaunayTriangle> closedList = new HashSet<>();

        NodeRecord startRecord = new NodeRecord(startTri, 0, heuristic(startPos, endPos), null, startPos);
        openList.add(startRecord);
        nodeRecords.put(startTri, startRecord);

        NodeRecord currentRecord = null;
        NodeRecord closestRecord = startRecord; // For partial pathing

        while (!openList.isEmpty()) {
            currentRecord = openList.poll();

            if (currentRecord.triangle == endTri) {
                break;
            }

            // Track the closest node in case we get blocked by a choke point
            if (heuristic(currentRecord.entryPoint, endPos) < heuristic(closestRecord.entryPoint, endPos)) {
                closestRecord = currentRecord;
            }

            closedList.add(currentRecord.triangle);

            for (int i = 0; i < 3; i++) {
                DelaunayTriangle neighbor = currentRecord.triangle.neighbors[i];

                if (neighbor == null || closedList.contains(neighbor)) continue;

                // --- CHOKE POINT CHECK ---
                if (unitRadius > 0) {
                    float squeezeAllowance = (unitRadius * 2f) * 0.95f;

                    // Count how many exits this triangle has
                    int neighborCount = 0;
                    for (int n = 0; n < 3; n++) {
                        if (neighbor.neighbors[n] != null) neighborCount++;
                    }

                    // 1. THE ROOM RULE: Is the triangle itself physically too narrow?
                    // BYPASS: If neighborCount is 1, it's a dead-end corner. We allow entry!
                    if (neighborCount > 1 && getTriangleClearance(neighbor) < squeezeAllowance) {
                        continue;
                    }

                    // 2. THE DOOR RULE: Is the portal connecting them too small?
                    if (getPortalWidth(currentRecord.triangle, neighbor) < squeezeAllowance) {
                        continue;
                    }
                }

                Vector2 exitPoint = getOptimalCrossingPoint(currentRecord.entryPoint, currentRecord.triangle, neighbor, endPos);

                // Measure the exact distance to the optimal crossing point!
                float edgeCost = currentRecord.entryPoint.dst(exitPoint);
                float tentativeCost = currentRecord.costSoFar + edgeCost;

                NodeRecord neighborRecord = nodeRecords.get(neighbor);

                if (neighborRecord == null) {
                    neighborRecord = new NodeRecord(neighbor, tentativeCost, tentativeCost + heuristic(exitPoint, endPos), currentRecord.triangle, exitPoint);
                    nodeRecords.put(neighbor, neighborRecord);
                    openList.add(neighborRecord);
                } else if (tentativeCost < neighborRecord.costSoFar) {
                    neighborRecord.costSoFar = tentativeCost;
                    neighborRecord.estimatedTotalCost = tentativeCost + heuristic(exitPoint, endPos);
                    neighborRecord.parent = currentRecord.triangle;
                    neighborRecord.entryPoint = exitPoint;

                    openList.remove(neighborRecord);
                    openList.add(neighborRecord);
                }
            }
        }

        // Trace back from either the exact target, OR the closest point we could reach (Partial Pathing)
        NodeRecord traceRecord = (currentRecord != null && currentRecord.triangle == endTri) ? currentRecord : closestRecord;

        while (traceRecord != null) {
            path.insert(0, traceRecord.triangle);
            traceRecord = traceRecord.parent != null ? nodeRecords.get(traceRecord.parent) : null;
        }

        return path;
    }

    // --- HELPER METHODS ---

    /**
     * Theta* Approximation: Finds the optimal point to cross a portal to minimize zig-zagging.
     */
    private static Vector2 getOptimalCrossingPoint(Vector2 entryPoint, DelaunayTriangle t1, DelaunayTriangle t2, Vector2 goalPos) {
        TriangulationPoint shared1 = null, shared2 = null;
        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 3; k++) {
                if (t1.points[j].equals(t2.points[k])) {
                    if (shared1 == null) shared1 = t1.points[j];
                    else shared2 = t1.points[j];
                }
            }
        }

        if (shared1 != null && shared2 != null) {
            Vector2 p1 = new Vector2(shared1.getXf(), shared1.getYf());
            Vector2 p2 = new Vector2(shared2.getXf(), shared2.getYf());

            Vector2 intersection = new Vector2();

            // 1. The Ideal Case: A straight line to the goal passes right through this portal.
            if (Intersector.intersectSegments(entryPoint, goalPos, p1, p2, intersection)) {
                return intersection;
            }

            // 2. The Corner Case: The direct line misses the portal (blocked by a wall).
            // Instead of falling back to the zig-zagging midpoint, we find the point on the
            // portal that is physically closest to the goal. This naturally hugs corners!
            Vector2 nearest = new Vector2();
            Intersector.nearestSegmentPoint(p1, p2, goalPos, nearest);
            return nearest;
        }

        // Failsafe for degenerate triangles
        return getCentroid(t1);
    }

    /**
     * Calculates the true physical bottleneck of a portal. If the portal spans across a corridor,
     * it measures the strict perpendicular distance between the opposing walls.
     */
    private static float getPortalWidth(DelaunayTriangle t1, DelaunayTriangle t2) {
        TriangulationPoint p1 = null, p2 = null;
        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 3; k++) {
                if (t1.points[j].equals(t2.points[k])) {
                    if (p1 == null) p1 = t1.points[j];
                    else p2 = t1.points[j];
                }
            }
        }

        if (p1 != null && p2 != null) {
            float p1x = p1.getXf(), p1y = p1.getYf();
            float p2x = p2.getXf(), p2y = p2.getYf();
            float literalLength = (float) Math.hypot(p1x - p2x, p1y - p2y);

            // 1. Gather all physical walls touching these two triangles (Max 4 walls)
            float[] wallsX1 = new float[4];
            float[] wallsY1 = new float[4];
            float[] wallsX2 = new float[4];
            float[] wallsY2 = new float[4];
            int wallCount = 0;

            wallCount = extractWalls(t1, wallsX1, wallsY1, wallsX2, wallsY2, wallCount);
            wallCount = extractWalls(t2, wallsX1, wallsY1, wallsX2, wallsY2, wallCount);

            boolean p1TouchesWall = false;
            boolean p2TouchesWall = false;

            for (int i = 0; i < wallCount; i++) {
                if (isPointOnWall(p1x, p1y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i])) p1TouchesWall = true;
                if (isPointOnWall(p2x, p2y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i])) p2TouchesWall = true;
            }

            // 2. THE FIX: If the portal bridges two different walls, measure the true corridor width!
            if (p1TouchesWall && p2TouchesWall) {
                float minClearance = literalLength;

                for (int i = 0; i < wallCount; i++) {
                    boolean touchesP1 = isPointOnWall(p1x, p1y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i]);
                    boolean touchesP2 = isPointOnWall(p2x, p2y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i]);

                    // If this wall touches P1, measure the perpendicular distance to P2
                    if (touchesP1 && !touchesP2) {
                        float dist = distPointToSegment(p2x, p2y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i]);
                        minClearance = Math.min(minClearance, dist);
                    }
                    // If this wall touches P2, measure the perpendicular distance to P1
                    if (touchesP2 && !touchesP1) {
                        float dist = distPointToSegment(p1x, p1y, wallsX1[i], wallsY1[i], wallsX2[i], wallsY2[i]);
                        minClearance = Math.min(minClearance, dist);
                    }
                }
                return minClearance;
            }

            // 3. If it's in an open field, or just tracing alongside one wall, the literal length is safe
            return literalLength;
        }
        return 0f;
    }

    // --- High-Performance Helpers ---

    private static int extractWalls(DelaunayTriangle tri, float[] x1, float[] y1, float[] x2, float[] y2, int count) {
        for (int i = 0; i < 3; i++) {
            if (tri.neighbors[i] == null) { // It has no neighbor, so it's a physical wall
                x1[count] = tri.points[(i + 1) % 3].getXf();
                y1[count] = tri.points[(i + 1) % 3].getYf();
                x2[count] = tri.points[(i + 2) % 3].getXf();
                y2[count] = tri.points[(i + 2) % 3].getYf();
                count++;
            }
        }
        return count;
    }

    private static boolean isPointOnWall(float px, float py, float x1, float y1, float x2, float y2) {
        float eps = 0.001f;
        return (Math.abs(px - x1) < eps && Math.abs(py - y1) < eps) ||
            (Math.abs(px - x2) < eps && Math.abs(py - y2) < eps);
    }

    public static float getTriangleClearance(DelaunayTriangle tri) {
        float minClearance = Float.MAX_VALUE;
        boolean hasWall = false;

        for (int i = 0; i < 3; i++) {
            if (tri.neighbors[i] == null) {
                hasWall = true;
                TriangulationPoint wallP1 = tri.points[(i + 1) % 3];
                TriangulationPoint wallP2 = tri.points[(i + 2) % 3];
                TriangulationPoint opp = tri.points[i];

                float dist = distPointToSegment(
                    opp.getXf(), opp.getYf(),
                    wallP1.getXf(), wallP1.getYf(),
                    wallP2.getXf(), wallP2.getYf()
                );
                minClearance = Math.min(minClearance, dist);
            }
        }
        return hasWall ? minClearance : Float.MAX_VALUE;
    }

    private static float distPointToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return (float) Math.hypot(px - x1, py - y1);
        float t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2;
        t = Math.max(0, Math.min(1, t));
        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);
        return (float) Math.hypot(px - projX, py - projY);
    }

    private static Vector2 getCentroid(DelaunayTriangle t) {
        return new Vector2(
            (t.points[0].getXf() + t.points[1].getXf() + t.points[2].getXf()) / 3f,
            (t.points[0].getYf() + t.points[1].getYf() + t.points[2].getYf()) / 3f
        );
    }

    /**
     * The A* Heuristic: Straight-line distance to the EXACT target coordinate.
     */
    private static float heuristic(Vector2 currentPoint, Vector2 endPos) {
        return currentPoint.dst(endPos) * 1.1f;
    }
}
