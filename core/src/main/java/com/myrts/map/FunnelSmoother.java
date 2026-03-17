package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import org.poly2tri.triangulation.TriangulationPoint;

public class FunnelSmoother {

    public static Array<Vector2> stringPull(Array<DelaunayTriangle> path, Vector2 start, Vector2 end) {
        Array<Vector2> waypoints = new Array<>();
        if (path == null || path.size == 0) return waypoints;

        if (path.size == 1) {
            waypoints.add(new Vector2(end));
            return waypoints;
        }

        Array<Vector2> leftPortals = new Array<>();
        Array<Vector2> rightPortals = new Array<>();

        leftPortals.add(new Vector2(start));
        rightPortals.add(new Vector2(start));

        // 1. Extract the Portals
        for (int i = 0; i < path.size - 1; i++) {
            DelaunayTriangle current = path.get(i);
            DelaunayTriangle next = path.get(i + 1);

            TriangulationPoint shared1 = null, shared2 = null;
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    if (current.points[j].equals(next.points[k])) {
                        if (shared1 == null) shared1 = current.points[j];
                        else shared2 = current.points[j];
                    }
                }
            }

            if (shared1 != null && shared2 != null) {
                Vector2 p1 = new Vector2(shared1.getXf(), shared1.getYf());
                Vector2 p2 = new Vector2(shared2.getXf(), shared2.getYf());

                // --- THE FIX IS HERE ---
                // Calculate left/right relative to the current triangle's center!
                float cx = (current.points[0].getXf() + current.points[1].getXf() + current.points[2].getXf()) / 3f;
                float cy = (current.points[0].getYf() + current.points[1].getYf() + current.points[2].getYf()) / 3f;
                Vector2 center = new Vector2(cx, cy);

                // If p2 is strictly to the left of the directed line from center to p1
                if (triArea2(center, p1, p2) > 0.0f) {
                    leftPortals.add(p2);
                    rightPortals.add(p1);
                } else {
                    leftPortals.add(p1);
                    rightPortals.add(p2);
                }
            }
        }

        leftPortals.add(new Vector2(end));
        rightPortals.add(new Vector2(end));

        // 2. The Funnel Algorithm
        Vector2 portalApex = new Vector2(start);
        Vector2 portalLeft = new Vector2(leftPortals.get(0));
        Vector2 portalRight = new Vector2(rightPortals.get(0));

        int apexIndex = 0;
        int leftIndex = 0;
        int rightIndex = 0;

        waypoints.add(new Vector2(portalApex));

        for (int i = 1; i < leftPortals.size; i++) {
            Vector2 left = leftPortals.get(i);
            Vector2 right = rightPortals.get(i);

            // Update right vertex (Moves Left/Inward)
            if (triArea2(portalApex, portalRight, right) >= 0.0f) {
                if (portalApex.equals(portalRight) || triArea2(portalApex, portalLeft, right) < 0.0f) {
                    portalRight = right; // Tighten the funnel
                    rightIndex = i;
                } else {
                    // Right crossed left!
                    portalApex = portalLeft;

                    // Prevent adding duplicate coordinates if the string snapped to the same corner twice
                    if (!waypoints.peek().equals(portalApex)) {
                        waypoints.add(new Vector2(portalApex));
                    }

                    apexIndex = leftIndex;
                    portalLeft = portalApex;
                    portalRight = portalApex;
                    i = apexIndex;
                    continue;
                }
            }

            // Update left vertex (Moves Right/Inward)
            if (triArea2(portalApex, portalLeft, left) <= 0.0f) {
                if (portalApex.equals(portalLeft) || triArea2(portalApex, portalRight, left) > 0.0f) {
                    portalLeft = left; // Tighten the funnel
                    leftIndex = i;
                } else {
                    // Left crossed right!
                    portalApex = portalRight;

                    if (!waypoints.peek().equals(portalApex)) {
                        waypoints.add(new Vector2(portalApex));
                    }

                    apexIndex = rightIndex;
                    portalLeft = portalApex;
                    portalRight = portalApex;
                    i = apexIndex;
                    continue;
                }
            }
        }

        // Add the destination if it wasn't the very last apex
        if (!waypoints.peek().equals(end)) {
            waypoints.add(new Vector2(end));
        }

        return waypoints;
    }

    /**
     * Standard 2D Cross Product Math.
     * Returns > 0 if Point C is to the LEFT of the directed line A->B.
     * Returns < 0 if Point C is to the RIGHT of the directed line A->B.
     */
    private static float triArea2(Vector2 a, Vector2 b, Vector2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
