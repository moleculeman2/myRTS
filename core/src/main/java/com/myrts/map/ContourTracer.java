package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

import java.util.*;

/**
 * This class traces the contours of walkable areas in a tilemap.
 * It uses a flood-fill approach to find connected walkable tiles and then
 * traces the boundaries to create polygons suitable for triangulation.
 * JTS is utilized to safely resolve shared vertices, and all operations
 * are strictly locked to integer coordinates to prevent floating point drift.
 */
public class ContourTracer {

    // A simple class to represent a vertex point strictly using integer grid coordinates.
    // By enforcing integers, we completely eliminate floating point imprecision.
    public static class Point {
        public final int x, y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y; // Exact integer match
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    // Represents an edge between two points. Treats Edge(A,B) and Edge(B,A) as identical.
    private static class Edge {
        public final Point p1, p2;

        public Edge(Point p1, Point p2) {
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

                    // Simplify first to remove collinear points before JTS processes them
                    List<List<Point>> simplifiedPolygons = new ArrayList<>();
                    for (List<Point> polygonPath : rawPolygons) {
                        simplifiedPolygons.add(simplifyPolygon(polygonPath));
                    }

                    // 3. Convert to Poly2Tri via JTS to resolve shared vertices perfectly on the grid
                    List<Polygon> validPolys = buildValidNavmeshPolygons(simplifiedPolygons, mapManager);
                    finalPolygons.addAll(validPolys);
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

        // 1. Use an Array of Point objects instead of an IntArray.
        // This is much easier to read and debug.
        Array<Point> stack = new Array<>();

        // 2. Push the starting coordinates as a Point
        stack.add(new Point(startX, startY));
        visited[startX][startY] = true;

        int[] dx = {0, 1, 0, -1}; // Top, Right, Bottom, Left
        int[] dy = {1, 0, -1, 0};

        while (stack.size > 0) {
            // 3. Pop the Point from the end of the array (Depth-First Search)
            Point current = stack.pop();

            // 4. Access X and Y directly from the object properties
            int cx = current.x;
            int cy = current.y;

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (mapManager.isCollision(nx, ny)) {
                    // This neighbor is a wall, so we found a boundary edge.
                    Point p1 = null, p2 = null;
                    if (i == 0) { // Top edge
                        p1 = new Point(cx, cy + 1); p2 = new Point(cx + 1, cy + 1);
                    } else if (i == 1) { // Right edge
                        p1 = new Point(cx + 1, cy + 1); p2 = new Point(cx + 1, cy);
                    } else if (i == 2) { // Bottom edge
                        p1 = new Point(cx + 1, cy); p2 = new Point(cx, cy);
                    } else if (i == 3) { // Left edge
                        p1 = new Point(cx, cy); p2 = new Point(cx, cy + 1);
                    }
                    edges.add(new Edge(p1, p2));
                } else if (!visited[nx][ny]) {
                    visited[nx][ny] = true;
                    // Push the new unvisited neighbor onto the stack
                    stack.add(new Point(nx, ny));
                }
            }
        }
        return edges;
    }

    /**
     * Takes an array of floating point boundary edges, instantly snaps them to the integer grid,
     * and constructs standard integer polygons out of them.
     */
    public static List<List<Point>> assemblePolygons(Array<Vector2[]> boundaryEdges) {
        if (boundaryEdges == null || boundaryEdges.size == 0) return new ArrayList<>();

        Set<Edge> edges = new HashSet<>();

        for (Vector2[] bEdge : boundaryEdges) {
            // Snap floating point coordinates directly to the exact integer tile grid
            Point p1 = new Point(Math.round(bEdge[0].x), Math.round(bEdge[0].y));
            Point p2 = new Point(Math.round(bEdge[1].x), Math.round(bEdge[1].y));

            // Ignore degenerate zero-length edges that might occur after snapping
            if (p1.equals(p2)) continue;

            Edge e = new Edge(p1, p2);

            // If the edge is already in the set, it means two triangles shared it.
            // Therefore, it's an internal edge. Cancel it out!
            if (edges.contains(e)) {
                edges.remove(e);
            } else {
                edges.add(e);
            }
        }

        // Now that we have a clean set of strict integer Edges, delegate to the core assembler
        return assemblePolygons(edges);
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

                if (nextEdge == null) {
                    System.err.println("Warning: Open loop detected! Skipping broken cavity piece.");
                    break;
                }

                edges.remove(nextEdge);
                // Figure out which point of the new edge is the one we haven't visited yet
                currentPoint = nextEdge.p1.equals(currentPoint) ? nextEdge.p2 : nextEdge.p1;
                path.add(currentPoint);
            }

            // Remove the closing duplicate point if loop completed successfully
            if (path.size() > 2 && path.get(path.size() - 1).equals(path.get(0))) {
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
     * Removes collinear points from a polygon path to simplify it.
     * This reduces the vertex count without changing the shape.
     * @param path The list of points representing the polygon loop.
     * @return A new list of points with redundant vertices removed.
     */
    public static List<Point> simplifyPolygon(List<Point> path) {
        if (path.size() < 3) {
            return path;
        }

        List<Point> simplifiedPath = new ArrayList<>();

        for (int i = 0; i < path.size(); i++) {
            Point prevPoint = path.get((i + path.size() - 1) % path.size());
            Point currentPoint = path.get(i);
            Point nextPoint = path.get((i + 1) % path.size());

            // Use an exact long integer cross-product area.
            // If the area is exactly 0, they are perfectly collinear. No epsilons!
            long crossProduct = (long)(currentPoint.x - prevPoint.x) * (nextPoint.y - prevPoint.y) -
                (long)(currentPoint.y - prevPoint.y) * (nextPoint.x - prevPoint.x);

            if (crossProduct != 0) {
                simplifiedPath.add(currentPoint);
            }
        }

        return simplifiedPath;
    }

    /**
     * Uses JTS to safely split polygons with shared vertices without losing grid precision.
     */
    private static List<Polygon> buildValidNavmeshPolygons(List<List<Point>> rawPolygons, MapManager mapManager) {
        List<Polygon> result = new ArrayList<>();
        if (rawPolygons.isEmpty()) return result;

        // Sort by size descending: index 0 is the outer shell, rest are holes
        rawPolygons.sort((p1, p2) -> Integer.compare(p2.size(), p1.size()));

        GeometryFactory gf = new GeometryFactory();

        // Build Shell
        LinearRing shell = createJtsRing(gf, rawPolygons.get(0));

        // Build Holes
        LinearRing[] holes = new LinearRing[rawPolygons.size() - 1];
        for (int i = 1; i < rawPolygons.size(); i++) {
            holes[i - 1] = createJtsRing(gf, rawPolygons.get(i));
        }

        // Create initial JTS Polygon. It might have shared vertices (invalid topology for Poly2Tri).
        org.locationtech.jts.geom.Polygon jtsPoly = gf.createPolygon(shell, holes);

        // --- THE MAGIC BULLET ---
        // buffer(0) strictly preserves grid coordinates. It acts as a topology normalizer.
        // It resolves "pinches" (shared vertices) by splitting the invalid polygon into a valid MultiPolygon.
        Geometry fixedGeo = jtsPoly.buffer(0);

        // fixedGeo might be a single Polygon or a MultiPolygon (if shared vertices were split)
        for (int i = 0; i < fixedGeo.getNumGeometries(); i++) {
            org.locationtech.jts.geom.Polygon validJtsPoly = (org.locationtech.jts.geom.Polygon) fixedGeo.getGeometryN(i);

            // Convert JTS Polygon back to Poly2Tri Polygon
            Polygon p2tPolygon = toPoly2Tri(validJtsPoly.getExteriorRing(), mapManager, false);

            // Add the interior holes back in
            for (int j = 0; j < validJtsPoly.getNumInteriorRing(); j++) {
                p2tPolygon.addHole(toPoly2Tri(validJtsPoly.getInteriorRingN(j), mapManager, true));
            }
            result.add(p2tPolygon);
        }

        return result;
    }

    private static LinearRing createJtsRing(GeometryFactory gf, List<Point> path) {
        Coordinate[] coords = new Coordinate[path.size() + 1];
        for (int i = 0; i < path.size(); i++) {
            coords[i] = new Coordinate(path.get(i).x, path.get(i).y);
        }
        // JTS rings must explicitly close (last vertex == first vertex)
        coords[path.size()] = new Coordinate(path.get(0).x, path.get(0).y);
        return gf.createLinearRing(coords);
    }

    private static Polygon toPoly2Tri(org.locationtech.jts.geom.LineString ring, MapManager mapManager, boolean isHole) {
        Coordinate[] coords = ring.getCoordinates();
        List<PolygonPoint> points = new ArrayList<>();

        // JTS rings are closed, so we skip the last coordinate for Poly2Tri (which expects open paths)
        for (int i = 0; i < coords.length - 1; i++) {
            points.add(new PolygonPoint(coords[i].x * mapManager.getTileWidth(), coords[i].y * mapManager.getTileHeight()));
        }

        // Poly2Tri generally expects opposite winding orders for holes.
        if (isHole) {
            Collections.reverse(points);
        }

        return new Polygon(points);
    }
}
