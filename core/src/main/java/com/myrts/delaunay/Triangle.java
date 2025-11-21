package com.myrts.delaunay;

// Triangle.java
// Represents a triangle with three points (vertices).
public class Triangle {
    Point p1, p2, p3;
    Edge e1, e2, e3;
    Point circumcenter;
    double circumradiusSquared;

    public Triangle(Point p1, Point p2, Point p3) {
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;

        this.e1 = new Edge(p1, p2);
        this.e2 = new Edge(p2, p3);
        this.e3 = new Edge(p3, p1);

        calculateCircumcircle();
    }

    // This is the core math to find the circumcenter and radius of the triangle.
    private void calculateCircumcircle() {
        // Using formulas from Wikipedia: Circumscribed circle
        double D = 2 * (p1.x * (p2.y - p3.y) + p2.x * (p3.y - p1.y) + p3.x * (p1.y - p2.y));

        double ux = ((p1.x * p1.x + p1.y * p1.y) * (p2.y - p3.y) + (p2.x * p2.x + p2.y * p2.y) * (p3.y - p1.y) + (p3.x * p3.x + p3.y * p3.y) * (p1.y - p2.y)) / D;
        double uy = ((p1.x * p1.x + p1.y * p1.y) * (p3.x - p2.x) + (p2.x * p2.x + p2.y * p2.y) * (p1.x - p3.x) + (p3.x * p3.x + p3.y * p3.y) * (p2.x - p1.x)) / D;

        this.circumcenter = new Point(ux, uy);

        double dx = p1.x - circumcenter.x;
        double dy = p1.y - circumcenter.y;
        this.circumradiusSquared = dx * dx + dy * dy;
    }

    // Checks if a given point is inside the circumcircle of this triangle.
    public boolean containsInCircumcircle(Point point) {
        double dx = point.x - circumcenter.x;
        double dy = point.y - circumcenter.y;
        double distSquared = dx * dx + dy * dy;

        // If the distance from the point to the circumcenter is less than the radius,
        // the point is inside. We use squared distances to avoid a costly square root.
        return distSquared < this.circumradiusSquared;
    }

    // Checks if the triangle contains one of the vertices of the super triangle.
    public boolean containsVertex(Point vertex) {
        return p1.equals(vertex) || p2.equals(vertex) || p3.equals(vertex);
    }

    @Override
    public String toString() {
        return "Triangle[" + p1 + ", " + p2 + ", " + p3 + "]";
    }
}
