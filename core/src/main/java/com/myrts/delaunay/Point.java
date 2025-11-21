package com.myrts.delaunay;

// Point.java
// A simple class to represent a 2D point.
public class Point {
    double x;
    double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // We override equals() and hashCode() so we can correctly
    // compare points and use them in collections like HashSet.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Point point = (Point) obj;
        // Using a small tolerance for floating point comparison
        return Math.abs(point.x - x) < 1e-9 && Math.abs(point.y - y) < 1e-9;
    }

    @Override
    public int hashCode() {
        // A simple hash code generation
        return Double.hashCode(x) + 31 * Double.hashCode(y);
    }

    @Override
    public String toString() {
        return "Point(" + x + ", " + y + ")";
    }
}
