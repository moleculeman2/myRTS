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

        // 1. Extract RAW Portals (No expanding here!)
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

        // 2. Pure Funnel Algorithm (Guaranteed never to detour)
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

        // 3. The "Corner Fillet" Post-Processor
        if (unitRadius <= 0f || waypoints.size < 3) return waypoints;

        Array<Vector2> finalPath = new Array<>();
        finalPath.add(new Vector2(waypoints.first()));

        for (int i = 1; i < waypoints.size - 1; i++) {
            Vector2 prev = waypoints.get(i - 1);
            Vector2 curr = waypoints.get(i);
            Vector2 next = waypoints.get(i + 1);

            Vector2 in = new Vector2(curr).sub(prev).nor();
            Vector2 out = new Vector2(next).sub(curr).nor();
            Vector2 miter = new Vector2(in).sub(out);
            float len2 = miter.len2();

            if (len2 > 0.01f) {
                float miterDist = (2f * unitRadius) / (float)Math.sqrt(len2);

                // If it's a very sharp corner, the miter spike will be huge.
                // Instead of a massive spike, we insert TWO points to smoothly round the corner.
                if (miterDist > unitRadius * 1.8f) {
                    Vector2 nIn = new Vector2(-in.y, in.x).scl(unitRadius);
                    if (nIn.dot(miter) < 0) nIn.scl(-1); // Point outward

                    Vector2 nOut = new Vector2(-out.y, out.x).scl(unitRadius);
                    if (nOut.dot(miter) < 0) nOut.scl(-1); // Point outward

                    // Place a point slightly before the corner, and slightly after
                    finalPath.add(new Vector2(curr).sub(new Vector2(in).scl(unitRadius)).add(nIn));
                    finalPath.add(new Vector2(curr).add(new Vector2(out).scl(unitRadius)).add(nOut));
                } else {
                    // Normal shallow corner, just push it out cleanly
                    finalPath.add(new Vector2(curr).add(miter.nor().scl(miterDist)));
                }
            } else {
                finalPath.add(new Vector2(curr));
            }
        }

        finalPath.add(new Vector2(waypoints.peek()));
        return finalPath;
    }

    private static float triArea2(Vector2 a, Vector2 b, Vector2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
