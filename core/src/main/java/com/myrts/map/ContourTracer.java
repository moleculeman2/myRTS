package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;

import java.util.*;

/**
 * This class traces the contours of walkable areas in a tilemap.
 * It uses a flood-fill approach to find connected walkable tiles and then
 * traces the boundaries to create polygons suitable for triangulation.
 */
public class ContourTracer {

    // A simple class to represent a vertex point using integer grid coordinates.
    // We override equals() and hashCode() so it can be used as a key in a Map.
    public static class Point {
        double x, y;

        Point(double  x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return Math.abs(point.x - x) < 1e-9 && Math.abs(point.y - y) < 1e-9;
        }

        @Override
        public int hashCode() {
            return 31 * (int)Math.round(x) + (int)Math.round(y);
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    // Represents an edge between two points. Also has a robust equals/hashCode
    // to treat Edge(A,B) and Edge(B,A) as the same.
    private static class Edge {
        final Point p1, p2;

        Edge(Point p1, Point p2) {
            // Standardize the order to make equals/hashCode reliable
            if (p1.hashCode() < p2.hashCode()) {
                this.p1 = p1;
                this.p2 = p2;
            } else {
                this.p1 = p2;
                this.p2 = p1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return p1.equals(edge.p1) && p2.equals(edge.p2);
        }

        @Override
        public int hashCode() {
            return p1.hashCode() + p2.hashCode();
        }
    }

    // A proper edge class that treats Edge(A,B) and Edge(B,A) as identical
    private static class UndirectedEdge {
        Vector2 v1, v2;

        UndirectedEdge(Vector2 v1, Vector2 v2) {
            // Sort by hashcode so direction doesn't matter for equals()
            if (v1.hashCode() < v2.hashCode()) {
                this.v1 = v1; this.v2 = v2;
            } else {
                this.v1 = v2; this.v2 = v1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UndirectedEdge)) return false;
            UndirectedEdge edge = (UndirectedEdge) o;
            // == is safe here ONLY because we unify Vector2 references first!
            return v1 == edge.v1 && v2 == edge.v2;
        }

        @Override
        public int hashCode() {
            return v1.hashCode() ^ v2.hashCode();
        }
    }

    /**
     * Main entry point. Traces all walkable areas in the map.
     * @param mapManager The map manager containing the collision data.
     * @return A list of Polygon objects, ready for triangulation. Each polygon has its holes already added.
     */
    public static List<Polygon> trace(MapManager mapManager) {
        boolean[][] visited = new boolean[mapManager.getMapWidth()][mapManager.getMapHeight()];
        List<Polygon> finalPolygons = new ArrayList<>();

        for (int y = 0; y < mapManager.getMapHeight(); y++) {
            for (int x = 0; x < mapManager.getMapWidth(); x++) {
                if (!mapManager.isCollision(x, y) && !visited[x][y]) {
                    // Found a new, unvisited walkable area.
                    // 1. Extract all boundary edges for this entire area.
                    Set<Edge> boundaryEdges = extractBoundaryEdges(mapManager, visited, x, y);

                    // 2. Assemble those edges into one or more closed loops (polygons).
                    List<List<Point>> rawPolygons = assemblePolygons(boundaryEdges);

                    if (rawPolygons.isEmpty()) continue;

                    // This happens BEFORE we send the data to Poly2Tri
                    resolveSharedVertices(rawPolygons);

                    // Create a new list to hold the simplified polygons.
                    List<List<Point>> simplifiedPolygons = new ArrayList<>();
                    for (List<Point> polygonPath : rawPolygons) {
                        // Replace each polygon with its simplified version.
                        simplifiedPolygons.add(simplifyPolygon(polygonPath));
                    }

                    // 3. Convert raw polygons into Poly2Tri polygons and identify holes.
                    Polygon mainPolygon = createNavmeshPolygon(simplifiedPolygons, mapManager);
                    finalPolygons.add(mainPolygon);
                }
            }
        }
        return finalPolygons;
    }

    /**
     * Uses flood-fill to find all connected walkable tiles and identify the boundary edges.
     */
    private static Set<Edge> extractBoundaryEdges(MapManager mapManager, boolean[][] visited, int startX, int startY) {
        Set<Edge> edges = new HashSet<>();
        Queue<Vector2> queue = new LinkedList<>();

        queue.add(new Vector2(startX, startY));
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            Vector2 current = queue.poll();
            int cx = (int) current.x;
            int cy = (int) current.y;

            // Check all 4 neighbors
            int[] dx = {0, 1, 0, -1}; // Corresponds to Top, Right, Bottom, Left edges
            int[] dy = {1, 0, -1, 0};

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (mapManager.isCollision(nx, ny)) {
                    // This neighbor is a wall, so we found a boundary edge.
                    // The vertices of a tile (cx,cy) are (cx,cy), (cx+1,cy), (cx+1,cy+1), (cx,cy+1)
                    Point p1, p2;
                    if (i == 0) { // Top edge
                        p1 = new Point(cx, cy + 1); p2 = new Point(cx + 1, cy + 1);
                    } else if (i == 1) { // Right edge
                        p1 = new Point(cx + 1, cy + 1); p2 = new Point(cx + 1, cy);
                    } else if (i == 2) { // Bottom edge
                        p1 = new Point(cx + 1, cy); p2 = new Point(cx, cy);
                    } else { // Left edge
                        p1 = new Point(cx, cy); p2 = new Point(cx, cy + 1);
                    }
                    edges.add(new Edge(p1, p2));
                } else if (!visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new Vector2(nx, ny));
                }
            }
        }
        return edges;
    }

    /**
     * Takes a set of unordered boundary edges and connects them into ordered polygon loops.
     */
    private static List<List<Point>> assemblePolygons(Set<Edge> edges) {
        List<List<Point>> polygons = new ArrayList<>();
        // A map to quickly find all edges connected to a specific point.
        Map<Point, List<Edge>> edgeMap = new HashMap<>();
        for (Edge edge : edges) {
            edgeMap.computeIfAbsent(edge.p1, k -> new ArrayList<>()).add(edge);
            edgeMap.computeIfAbsent(edge.p2, k -> new ArrayList<>()).add(edge);
        }

        while (!edges.isEmpty()) {
            List<Point> path = new ArrayList<>();
            Edge startEdge = edges.iterator().next(); // Grab any edge to start
            edges.remove(startEdge);

            Point startPoint = startEdge.p1;
            Point currentPoint = startEdge.p2;
            path.add(startPoint);
            path.add(currentPoint);

            while (!currentPoint.equals(startPoint)) {
                // Find the next edge connected to our current point
                Edge nextEdge = null;
                for (Edge candidate : edgeMap.get(currentPoint)) {
                    if (edges.contains(candidate)) {
                        nextEdge = candidate;
                        break;
                    }
                }

                if (nextEdge == null) break; // Should not happen in a closed loop

                edges.remove(nextEdge);
                // Figure out which point of the new edge is the one we haven't visited yet
                currentPoint = nextEdge.p1.equals(currentPoint) ? nextEdge.p2 : nextEdge.p1;
                path.add(currentPoint);
            }
            // The last point is a duplicate of the start, remove it.
            path.remove(path.size() - 1);
            polygons.add(path);
        }
        return polygons;
    }

    /**
     * Takes a set of unordered boundary edges and connects them into ordered polygon loops.
     */
    public static List<List<Point>> assemblePolygons(Array<Vector2[]> boundaryEdges) {
        List<List<Point>> polygons = new ArrayList<>();
        if (boundaryEdges == null || boundaryEdges.size == 0) return polygons;

        // 1. UNIFY VERTICES to fix floating-point drift!
        float EPSILON = 0.5f; // Tolerance (half a pixel/unit)
        List<Vector2> uniqueVertices = new ArrayList<>();

        for (Vector2[] bEdge : boundaryEdges) {
            for (int i = 0; i < 2; i++) {
                boolean found = false;
                for (Vector2 unique : uniqueVertices) {
                    if (unique.dst(bEdge[i]) < EPSILON) {
                        bEdge[i] = unique; // Snap to the unified reference
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    uniqueVertices.add(bEdge[i]);
                }
            }
        }

        // 2. Build Adjacency Map (Now flawlessly stable because references are unified)
        Map<Vector2, List<UndirectedEdge>> edgeMap = new HashMap<>();
        Set<UndirectedEdge> edges = new HashSet<>();

        for (Vector2[] bEdge : boundaryEdges) {
            UndirectedEdge e = new UndirectedEdge(bEdge[0], bEdge[1]);

            // THE FIX: If the edge is already in the set, it means two triangles shared it.
            // Therefore, it's an internal edge. Cancel it out!
            if (edges.contains(e)) {
                edges.remove(e);
            } else {
                edges.add(e);
            }
        }

        // Build the map only from the surviving, true outer boundary edges
        for (UndirectedEdge e : edges) {
            edgeMap.computeIfAbsent(e.v1, k -> new ArrayList<>()).add(e);
            edgeMap.computeIfAbsent(e.v2, k -> new ArrayList<>()).add(e);
        }

        // 3. Assemble the Loops
        while (!edges.isEmpty()) {
            List<Point> path = new ArrayList<>();
            UndirectedEdge startEdge = edges.iterator().next();
            edges.remove(startEdge);

            Vector2 startPoint = startEdge.v1;
            Vector2 currentPoint = startEdge.v2;

            path.add(new Point(startPoint.x, startPoint.y));
            path.add(new Point(currentPoint.x, currentPoint.y));

            // We can safely use != because we unified the Object references in Step 1
            while (currentPoint != startPoint) {
                UndirectedEdge nextEdge = null;
                for (UndirectedEdge candidate : edgeMap.get(currentPoint)) {
                    if (edges.contains(candidate)) {
                        nextEdge = candidate;
                        break;
                    }
                }

                if (nextEdge == null) {
                    System.err.println("Warning: Open loop detected! Skipping broken cavity piece.");
                    break; // Gracefully handle instead of infinite looping
                }

                edges.remove(nextEdge);
                currentPoint = (nextEdge.v1 == currentPoint) ? nextEdge.v2 : nextEdge.v1;
                path.add(new Point(currentPoint.x, currentPoint.y));
            }

            // Remove the closing duplicate point if loop completed successfully
            if (path.size() > 2 && path.get(path.size() - 1).x == path.get(0).x && path.get(path.size() - 1).y == path.get(0).y) {
                path.remove(path.size() - 1);
            }

            // ONLY pass valid, completely closed polygons down the line
            if (path.size() >= 3) {
                polygons.add(path);
            }
        }
        return polygons;
    }

    /**
     * Converts the raw integer-based polygons into Poly2Tri format and identifies holes.
     */
    private static Polygon createNavmeshPolygon(List<List<Point>> rawPolygons, MapManager mapManager) {
        // Your heuristic: the polygon with the most points is the outer one.
        Collections.sort(rawPolygons, new Comparator<List<Point>>() {
            @Override
            public int compare(List<Point> p1, List<Point> p2) {
                // We want to sort in descending order (largest first),
                // so we compare p2's size to p1's size.
                return Integer.valueOf(p2.size()).compareTo(p1.size());
            }
        });

        // Create the main outer polygon
        List<Point> mainRawPoly = rawPolygons.get(0);
        List<PolygonPoint> mainPolyPoints = new ArrayList<>();
        for (Point p : mainRawPoly) {
            mainPolyPoints.add(new PolygonPoint(p.x * mapManager.getTileWidth(), p.y * mapManager.getTileHeight()));
        }
        Polygon mainPolygon = new Polygon(mainPolyPoints);

        // Add the rest as holes
        for (int i = 1; i < rawPolygons.size(); i++) {
            List<Point> holeRawPoly = rawPolygons.get(i);
            List<PolygonPoint> holePolyPoints = new ArrayList<>();
            for (Point p : holeRawPoly) {
                // IMPORTANT: Poly2Tri requires holes to have the opposite winding order (e.g., clockwise)
                // of the outer polygon (e.g., counter-clockwise). Reversing the list is a simple way to do this.
                holePolyPoints.add(new PolygonPoint(p.x * mapManager.getTileWidth(), p.y * mapManager.getTileHeight()));
            }
            Collections.reverse(holePolyPoints); // Flip the winding order
            mainPolygon.addHole(new Polygon(holePolyPoints));
        }

        return mainPolygon;
    }

    /**
     * Finds and resolves vertices that are shared between different polygon loops.
     * This prevents self-intersecting polygons by "nudging" one of the vertices
     * by a tiny epsilon value, effectively separating the polygons.
     * @param rawPolygons The list of traced polygon loops.
     */
    private static void resolveSharedVertices(List<List<Point>> rawPolygons) {
        // Create a set of all vertices for each polygon for quick lookups.
        List<Set<Point>> polygonVertexSets = new ArrayList<>();
        for (List<Point> poly : rawPolygons) {
            polygonVertexSets.add(new HashSet<>(poly));
        }

        // Compare every polygon against every other polygon
        for (int i = 0; i < rawPolygons.size(); i++) {
            for (int j = i + 1; j < rawPolygons.size(); j++) {
                Set<Point> poly1Set = polygonVertexSets.get(i);
                List<Point> poly2List = rawPolygons.get(j);

                // Now, check for shared points.
                for (int k = 0; k < poly2List.size(); k++) {
                    Point p = poly2List.get(k);
                    if (poly1Set.contains(p)) {
                        // SHARED VERTEX FOUND!
                        // Nudge the vertex in the second polygon by a small amount.
                        Point nudgedPoint = new Point((int) p.x, (int) p.y); // Create a new point to modify

                        // We need to create a new Point object since our Point class is immutable.
                        // Let's modify the Point class to be mutable for this to be easier.
                        // For now, let's assume we can't and we replace it in the list.
                        // A better implementation would make the Point class mutable.

                        // In a real scenario, you would make the Point class's x/y fields non-final
                        // and just modify them. Since ours are final, we replace the object.
                        // This is less efficient but demonstrates the logic.
                        // Let's pretend we can modify it for simplicity in the final code.

                        // **Let's make a quick change to the Point class for this.**
                        // Change the Point class fields from 'final int x, y;' to 'double x, y;'

                        // Nudge logic: Move it slightly towards the center of its own edge.
                        Point prev = poly2List.get((k == 0) ? poly2List.size() - 1 : k - 1);
                        Point next = poly2List.get((k + 1) % poly2List.size());

                        double dx1 = p.x - prev.x;
                        double dy1 = p.y - prev.y;
                        double dx2 = p.x - next.x;
                        double dy2 = p.y - next.y;

                        // Move the point 0.1% towards the average of its neighbors
                        p.x -= (dx1 + dx2) * 0.001;
                        p.y -= (dy1 + dy2) * 0.001;
                    }
                }
            }
        }
    }
    /**
     * Removes collinear points from a polygon path to simplify it.
     * This reduces the vertex count without changing the shape.
     * @param path The list of points representing the polygon loop.
     * @return A new list of points with redundant vertices removed.
     */
    private static List<Point> simplifyPolygon(List<Point> path) {
        // We need at least 3 points to have a collinear point to remove.
        if (path.size() < 3) {
            return path;
        }

        List<Point> simplifiedPath = new ArrayList<>();

        // Iterate through all points in the path. The modulo operator (%) handles the wrap-around
        // for the first and last points of the list.
        for (int i = 0; i < path.size(); i++) {
            Point prevPoint = path.get((i + path.size() - 1) % path.size());
            Point currentPoint = path.get(i);
            Point nextPoint = path.get((i + 1) % path.size());

            // We use the 2D cross-product to check for collinearity. If the area
            // of the triangle formed by the three points is zero, they are on a line.
            double crossProduct = (currentPoint.x - prevPoint.x) * (nextPoint.y - prevPoint.y) -
                (currentPoint.y - prevPoint.y) * (nextPoint.x - prevPoint.x);

            // Keep the current point only if it's a corner (not collinear).
            // We use a small epsilon to handle floating-point inaccuracies.
            if (Math.abs(crossProduct) > 1e-9) {
                simplifiedPath.add(currentPoint);
            }
        }

        return simplifiedPath;
    }

}
