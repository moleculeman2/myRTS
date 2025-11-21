package com.myrts.delaunay;

// Edge.java
// Represents a line segment connecting two points.
public class Edge {
    Point p1;
    Point p2;

    public Edge(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    // We override equals() and hashCode() to treat Edge(a, b) and Edge(b, a)
    // as the same edge. This is important for finding shared edges.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Edge edge = (Edge) obj;
        return (p1.equals(edge.p1) && p2.equals(edge.p2)) ||
            (p1.equals(edge.p2) && p2.equals(edge.p1));
    }

    @Override
    public int hashCode() {
        // The hash code is order-independent
        return p1.hashCode() + p2.hashCode();
    }

    @Override
    public String toString() {
        return "Edge[" + p1 + ", " + p2 + "]";
    }
}
