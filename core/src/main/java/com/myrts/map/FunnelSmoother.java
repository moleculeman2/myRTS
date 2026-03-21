package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import org.poly2tri.triangulation.TriangulationPoint;

public class FunnelSmoother {

    public static Array<Vector2> stringPull(Array<DelaunayTriangle> path, Vector2 start, Vector2 end, float unitRadius) {
        Array<Vector2> waypoints = new Array<>();
        if (path == null || path.size == 0) return waypoints;
        if (path.size == 1) { waypoints.add(new Vector2(end)); return waypoints; }

        Array<Vector2> leftPortals = new Array<>();
        Array<Vector2> rightPortals = new Array<>();
        leftPortals.add(new Vector2(start)); rightPortals.add(new Vector2(start));

        // 1. Extract RAW Portals
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
                float cx = (current.points[0].getXf() + current.points[1].getXf() + current.points[2].getXf()) / 3f;
                float cy = (current.points[0].getYf() + current.points[1].getYf() + current.points[2].getYf()) / 3f;
                Vector2 center = new Vector2(cx, cy);

                if (triArea2(center, p1, p2) > 0.0f) {
                    leftPortals.add(p2); rightPortals.add(p1);
                } else {
                    leftPortals.add(p1); rightPortals.add(p2);
                }
            }
        }

        leftPortals.add(new Vector2(end)); rightPortals.add(new Vector2(end));

        // --- 2. THE UNIFIED SAFE PORTAL SQUEEZE ---
        if (unitRadius > 0f) {
            // THE FIX: Do NOT multiply by unitRadius here!
            // We want the waypoints to land practically exactly on the physical corner
            // so our PathfindingSystem's normal-accumulation solver can perfectly bisect it.
            // This tiny 0.01f epsilon simply prevents floating-point collinearity skipping.
            float squeeze = 0.01f;

            for (int i = 1; i < leftPortals.size - 1; i++) {
                Vector2 L = leftPortals.get(i);
                Vector2 R = rightPortals.get(i);
                float width = L.dst(R);

                if (width > squeeze * 2f) {
                    Vector2 dir = new Vector2(R).sub(L).nor();
                    L.mulAdd(dir, squeeze);
                    R.mulAdd(dir, -squeeze);
                } else {
                    Vector2 mid = new Vector2(L).add(R).scl(0.5f);
                    L.set(mid);
                    R.set(mid);
                }
            }
        }
        // 3. Pure Funnel Algorithm (Now runs on mathematically perfect portals)
        Vector2 portalApex = new Vector2(start);
        Vector2 portalLeft = new Vector2(leftPortals.get(0));
        Vector2 portalRight = new Vector2(rightPortals.get(0));
        int apexIndex = 0, leftIndex = 0, rightIndex = 0;
        waypoints.add(new Vector2(portalApex));

        for (int i = 1; i < leftPortals.size; i++) {
            Vector2 left = leftPortals.get(i);
            Vector2 right = rightPortals.get(i);

            if (triArea2(portalApex, portalRight, right) >= 0.0f) {
                if (portalApex.equals(portalRight) || triArea2(portalApex, portalLeft, right) < 0.0f) {
                    portalRight = right; rightIndex = i;
                } else {
                    portalApex = portalLeft;
                    if (!waypoints.peek().equals(portalApex)) waypoints.add(new Vector2(portalApex));
                    apexIndex = leftIndex; portalLeft = portalApex; portalRight = portalApex;
                    i = apexIndex; continue;
                }
            }

            if (triArea2(portalApex, portalLeft, left) <= 0.0f) {
                if (portalApex.equals(portalLeft) || triArea2(portalApex, portalRight, left) > 0.0f) {
                    portalLeft = left; leftIndex = i;
                } else {
                    portalApex = portalRight;
                    if (!waypoints.peek().equals(portalApex)) waypoints.add(new Vector2(portalApex));
                    apexIndex = rightIndex; portalLeft = portalApex; portalRight = portalApex;
                    i = apexIndex; continue;
                }
            }
        }

        if (!waypoints.peek().equals(end)) waypoints.add(new Vector2(end));

        // No Post-Processing needed! The waypoints are already flawless.
        return waypoints;
    }

    private static float triArea2(Vector2 a, Vector2 b, Vector2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
