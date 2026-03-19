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
                // --- CHOKE POINT CHECK ---
                if (unitRadius > 0) {
                    float portalWidth = getPortalWidth(currentRecord.triangle, neighbor);

                    // Allow the unit to "squeeze" through gaps that are up to 5% smaller than it
                    float squeezeAllowance = (unitRadius * 2f) * 0.95f;

                    if (portalWidth < squeezeAllowance) {
                        continue; // The gap is truly blocked
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

    private static float getPortalWidth(DelaunayTriangle t1, DelaunayTriangle t2) {
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
            float dx = shared1.getXf() - shared2.getXf();
            float dy = shared1.getYf() - shared2.getYf();
            float portalLength = (float) Math.sqrt(dx * dx + dy * dy);

            int t1Neighbors = 0;
            int t2Neighbors = 0;
            for (int i = 0; i < 3; i++) {
                if (t1.neighbors[i] != null) t1Neighbors++;
                if (t2.neighbors[i] != null) t2Neighbors++;
            }

            if (t1Neighbors == 2 && t2Neighbors == 2) {
                float clearance1 = getTriangleClearance(t1);
                float clearance2 = getTriangleClearance(t2);
                return Math.min(portalLength, Math.min(clearance1, clearance2));
            } else {
                return portalLength;
            }
        }
        return 0f;
    }

    private static float getTriangleClearance(DelaunayTriangle tri) {
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
