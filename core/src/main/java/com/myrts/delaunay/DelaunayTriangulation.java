package com.myrts.delaunay;

// DelaunayTriangulation.java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DelaunayTriangulation {

    public List<Triangle> triangulate(List<Point> points) {
        List<Triangle> triangles = new ArrayList<>();

        // 1. Create a "super-triangle" that encloses all points
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (Point p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }

        double dx = maxX - minX;
        double dy = maxY - minY;
        double deltaMax = Math.max(dx, dy);
        double midX = minX + dx * 0.5;
        double midY = minY + dy * 0.5;

        Point p1 = new Point(midX - 20 * deltaMax, midY - deltaMax);
        Point p2 = new Point(midX, midY + 20 * deltaMax);
        Point p3 = new Point(midX + 20 * deltaMax, midY - deltaMax);

        Triangle superTriangle = new Triangle(p1, p2, p3);
        triangles.add(superTriangle);

        // 2. Add points one by one
        for (Point point : points) {
            List<Triangle> badTriangles = new ArrayList<>();
            // Find all triangles whose circumcircle contains the new point
            for (Triangle triangle : triangles) {
                if (triangle.containsInCircumcircle(point)) {
                    badTriangles.add(triangle);
                }
            }

            // Find the boundary of the polygonal hole
            List<Edge> polygon = new ArrayList<>();
            for (Triangle triangle : badTriangles) {
                // Check each edge of the bad triangle
                if (!isEdgeShared(triangle.e1, badTriangles)) polygon.add(triangle.e1);
                if (!isEdgeShared(triangle.e2, badTriangles)) polygon.add(triangle.e2);
                if (!isEdgeShared(triangle.e3, badTriangles)) polygon.add(triangle.e3);
            }

            // Remove bad triangles from the main list
            triangles.removeAll(badTriangles);

            // Re-triangulate the polygonal hole
            for (Edge edge : polygon) {
                triangles.add(new Triangle(edge.p1, edge.p2, point));
            }
        }

        // 3. Clean up: remove triangles connected to the super-triangle
        List<Triangle> result = new ArrayList<>();
        for (Triangle triangle : triangles) {
            if (!triangle.containsVertex(p1) && !triangle.containsVertex(p2) && !triangle.containsVertex(p3)) {
                result.add(triangle);
            }
        }

        return result;
    }

    // Helper method to check if an edge is shared by any other bad triangle
    private boolean isEdgeShared(Edge edge, List<Triangle> badTriangles) {
        int count = 0;
        for (Triangle triangle : badTriangles) {
            if (triangle.e1.equals(edge) || triangle.e2.equals(edge) || triangle.e3.equals(edge)) {
                count++;
            }
        }
        // If the edge is shared by more than one bad triangle, it's an internal edge
        return count > 1;
    }

    // --- The Constrained Part ---
    // This is a simplified placeholder. A full implementation requires a robust
    // edge-flipping algorithm which can get very complex.
    public List<Triangle> triangulateWithConstraints(List<Point> points, List<Edge> constraints) {
        // First, perform a standard Delaunay triangulation
        List<Triangle> delaunayTriangles = triangulate(points);

        // This is where you would insert constraints. For each constraint:
        // 1. Find all triangles the constraint edge intersects.
        // 2. Remove those triangles.
        // 3. Re-triangulate the two resulting polygonal holes on either side of the constraint.
        // 4. Use an edge-flipping algorithm to restore the Delaunay property where possible.

        System.out.println("Constraint handling is a complex topic. This result is the initial Delaunay triangulation before inserting constraints.");

        return delaunayTriangles;
    }

    public static void main(String[] args) {
        // Create a list of points
        List<Point> myPoints = new ArrayList<>();
        myPoints.add(new Point(100, 100));
        myPoints.add(new Point(200, 300));
        myPoints.add(new Point(300, 50));
        myPoints.add(new Point(400, 250));

        // Create an instance of our triangulator
        DelaunayTriangulation triangulator = new DelaunayTriangulation();

        // Perform the triangulation
        List<Triangle> finalTriangles = triangulator.triangulate(myPoints);

        System.out.println("Generated " + finalTriangles.size() + " triangles:");
        for (Triangle t : finalTriangles) {
            System.out.println(t);
        }
    }
}
