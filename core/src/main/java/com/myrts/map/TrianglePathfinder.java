package com.myrts.map;

import com.badlogic.gdx.utils.Array;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

public class TrianglePathfinder {

    // A simple wrapper to hold the A* scoring data for each triangle
    private static class NodeRecord implements Comparable<NodeRecord> {
        DelaunayTriangle triangle;
        float costSoFar;
        float estimatedTotalCost;
        DelaunayTriangle parent;

        public NodeRecord(DelaunayTriangle triangle, float costSoFar, float estimatedTotalCost, DelaunayTriangle parent) {
            this.triangle = triangle;
            this.costSoFar = costSoFar;
            this.estimatedTotalCost = estimatedTotalCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(NodeRecord other) {
            return Float.compare(this.estimatedTotalCost, other.estimatedTotalCost);
        }
    }

    /**
     * Finds the shortest path of triangles from the start to the end.
     */
    public static Array<DelaunayTriangle> findPath(DelaunayTriangle startTri, DelaunayTriangle endTri) {
        Array<DelaunayTriangle> path = new Array<>();
        if (startTri == null || endTri == null) return path;

        if (startTri == endTri) {
            path.add(startTri);
            return path;
        }

        PriorityQueue<NodeRecord> openList = new PriorityQueue<>();
        HashMap<DelaunayTriangle, NodeRecord> nodeRecords = new HashMap<>();
        HashSet<DelaunayTriangle> closedList = new HashSet<>();

        // 1. Setup the starting node
        NodeRecord startRecord = new NodeRecord(startTri, 0, heuristic(startTri, endTri), null);
        openList.add(startRecord);
        nodeRecords.put(startTri, startRecord);

        NodeRecord currentRecord = null;

        // 2. The main A* Loop
        while (!openList.isEmpty()) {
            currentRecord = openList.poll();

            // Did we reach the destination?
            if (currentRecord.triangle == endTri) {
                break;
            }

            closedList.add(currentRecord.triangle);

            // 3. Check all 3 neighbors of the current triangle
            for (int i = 0; i < 3; i++) {
                DelaunayTriangle neighbor = currentRecord.triangle.neighbors[i];

                // If neighbor is null (it's a wall/border) or we already evaluated it, skip it
                if (neighbor == null || closedList.contains(neighbor)) continue;

                // Cost is the physical distance between the centers of the two triangles
                float edgeCost = distance(currentRecord.triangle, neighbor);
                float tentativeCost = currentRecord.costSoFar + edgeCost;

                NodeRecord neighborRecord = nodeRecords.get(neighbor);

                if (neighborRecord == null) {
                    // First time seeing this neighbor, add it to the open list
                    neighborRecord = new NodeRecord(neighbor, tentativeCost, tentativeCost + heuristic(neighbor, endTri), currentRecord.triangle);
                    nodeRecords.put(neighbor, neighborRecord);
                    openList.add(neighborRecord);
                } else if (tentativeCost < neighborRecord.costSoFar) {
                    // We found a FASTER route to an already discovered neighbor! Update it.
                    neighborRecord.costSoFar = tentativeCost;
                    neighborRecord.estimatedTotalCost = tentativeCost + heuristic(neighbor, endTri);
                    neighborRecord.parent = currentRecord.triangle;

                    // Force the PriorityQueue to re-sort this specific record
                    openList.remove(neighborRecord);
                    openList.add(neighborRecord);
                }
            }
        }

        // 4. Trace the parents backwards to reconstruct the final path
        if (currentRecord != null && currentRecord.triangle == endTri) {
            while (currentRecord != null) {
                path.insert(0, currentRecord.triangle); // Insert at index 0 to reverse the order
                currentRecord = currentRecord.parent != null ? nodeRecords.get(currentRecord.parent) : null;
            }
        }

        return path; // Returns empty array if no path is possible (e.g., trapped by walls)
    }

    /**
     * Calculates the distance between the geometric centers (centroids) of two triangles.
     */
    private static float distance(DelaunayTriangle t1, DelaunayTriangle t2) {
        float x1 = (t1.points[0].getXf() + t1.points[1].getXf() + t1.points[2].getXf()) / 3f;
        float y1 = (t1.points[0].getYf() + t1.points[1].getYf() + t1.points[2].getYf()) / 3f;
        float x2 = (t2.points[0].getXf() + t2.points[1].getXf() + t2.points[2].getXf()) / 3f;
        float y2 = (t2.points[0].getYf() + t2.points[1].getYf() + t2.points[2].getYf()) / 3f;

        // Standard Euclidean distance formula: $d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$
        return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    /**
     * The A* Heuristic: Straight-line distance to the goal.
     */
    private static float heuristic(DelaunayTriangle current, DelaunayTriangle end) {
        return distance(current, end);
    }
}
