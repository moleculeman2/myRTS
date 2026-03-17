package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.poly2tri.geometry.polygon.PolygonPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.List;

public class NavMeshClipper {

    private static final GeometryFactory geoFactory = new GeometryFactory();
    private static final PrecisionModel precisionModel = new PrecisionModel(10.0);
    private static final GeometryPrecisionReducer precisionReducer = new GeometryPrecisionReducer(precisionModel);

    // 1. Create the Pool
    private static final Pool<Coordinate> coordPool = new Pool<Coordinate>() {
        @Override
        protected Coordinate newObject() {
            return new Coordinate();
        }
    };

    // 2. Create a tracker so we don't lose the coordinates we borrow
    private static final Array<Coordinate> activeCoords = new Array<>();

    // 3. Helper method to easily grab and track a recycled coordinate
    private static Coordinate obtainCoord(float x, float y) {
        Coordinate c = coordPool.obtain();
        c.x = x;
        c.y = y;
        c.z = Coordinate.NULL_ORDINATE; // Ensure clean state
        activeCoords.add(c);
        return c;
    }

    /**
     * Merges the building footprint and all provided intersected triangles into a List of
     * Poly2Tri Polygons, snapped to the grid, with collinear vertices removed.
     */
    public static List<org.poly2tri.geometry.polygon.Polygon> mergeTrianglesAndBuilding(
        Array<DelaunayTriangle> intersectedTriangles,
        float bX, float bY, float bW, float bH, Array<Vector2> protectedVertices) {

        // Guarantee tracker is empty
        activeCoords.clear();

        try {
            List<Geometry> polygonsToMerge = new ArrayList<>();

            // 1. Fetch pooled coordinates for the building
            Coordinate[] buildingCoords = new Coordinate[] {
                obtainCoord(bX, bY),
                obtainCoord(bX + bW, bY),
                obtainCoord(bX + bW, bY + bH),
                obtainCoord(bX, bY + bH),
                obtainCoord(bX, bY) // Close the loop
            };
            Polygon buildingPoly = geoFactory.createPolygon(buildingCoords);
            polygonsToMerge.add(precisionReducer.reduce(buildingPoly).buffer(0));

            // 2. Fetch pooled coordinates for EVERY triangle
            for (DelaunayTriangle tri : intersectedTriangles) {
                Coordinate[] triCoords = new Coordinate[] {
                    obtainCoord(tri.points[0].getXf(), tri.points[0].getYf()),
                    obtainCoord(tri.points[1].getXf(), tri.points[1].getYf()),
                    obtainCoord(tri.points[2].getXf(), tri.points[2].getYf()),
                    obtainCoord(tri.points[0].getXf(), tri.points[0].getYf()) // Close the loop
                };
                Polygon triPoly = geoFactory.createPolygon(triCoords);
                polygonsToMerge.add(precisionReducer.reduce(triPoly).buffer(0));
            }

            // 3. Merge them all together
            // Optimization: pass exact size to avoid an extra internal array allocation
            GeometryCollection geometryCollection = geoFactory.createGeometryCollection(
                polygonsToMerge.toArray(new Geometry[polygonsToMerge.size()])
            );
            Geometry mergedGeometry = geometryCollection.union();
            mergedGeometry = precisionReducer.reduce(mergedGeometry).buffer(0);

            // 4. Return as a List to safely handle MultiPolygons
            List<org.poly2tri.geometry.polygon.Polygon> resultPolys = new ArrayList<>();

            if (mergedGeometry instanceof Polygon) {
                resultPolys.add(convertToCleanPoly2Tri((Polygon) mergedGeometry, protectedVertices));
            } else if (mergedGeometry instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) mergedGeometry;
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    resultPolys.add(convertToCleanPoly2Tri((Polygon) mp.getGeometryN(i), protectedVertices));
                }
            } else {
                System.err.println("Warning: Failed to generate a valid merged polygon.");
            }

            return resultPolys;

        } finally {
            // 5. THE MAGIC: Throw all coordinates back into the pool, no GC penalty!
            coordPool.freeAll(activeCoords);
            activeCoords.clear();
        }
    }

    /**
     * Converts a JTS Polygon to Poly2Tri while stripping out any collinear boundary points.
     */
    private static org.poly2tri.geometry.polygon.Polygon convertToCleanPoly2Tri
    (Polygon jtsPolygon, Array<Vector2> protectedVertices) {
        Coordinate[] outerCoords = jtsPolygon.getExteriorRing().getCoordinates();
        List<PolygonPoint> p2tOuter = removeCollinearVertices(outerCoords, protectedVertices);

        org.poly2tri.geometry.polygon.Polygon p2tPoly = new org.poly2tri.geometry.polygon.Polygon(p2tOuter);

        for (int i = 0; i < jtsPolygon.getNumInteriorRing(); i++) {
            Coordinate[] holeCoords = jtsPolygon.getInteriorRingN(i).getCoordinates();
            List<PolygonPoint> p2tHole = removeCollinearVertices(holeCoords, protectedVertices);

            // Only add the hole if it still has enough points to be a valid polygon
            if (p2tHole.size() >= 3) {
                p2tPoly.addHole(new org.poly2tri.geometry.polygon.Polygon(p2tHole));
            }
        }

        return p2tPoly;
    }

    /**
     * Iterates through a JTS Coordinate array (closed loop) and strips out vertices
     * that lie flatly on the line segment between their neighbors.
     */
    private static List<PolygonPoint> removeCollinearVertices(Coordinate[] coords, Array<Vector2> protectedVertices) {

        List<PolygonPoint> cleanedPoints = new ArrayList<>();
        int pointCount = coords.length - 1;

        if (pointCount < 3) return cleanedPoints;

        for (int i = 0; i < pointCount; i++) {
            Coordinate prev = coords[(i - 1 + pointCount) % pointCount];
            Coordinate curr = coords[i];
            Coordinate next = coords[(i + 1) % pointCount];

            // --- NEW: Check if this vertex is protected ---
            boolean isProtected = false;
            if (protectedVertices != null) {
                for (Vector2 pt : protectedVertices) {
                    // Use a small epsilon for floating point vs double comparisons
                    if (Math.abs(pt.x - curr.x) < 0.001f && Math.abs(pt.y - curr.y) < 0.001f) {
                        isProtected = true;
                        break;
                    }
                }
            }

            // If it's a T-junction needed by the surrounding navmesh, KEEP IT.
            if (isProtected) {
                cleanedPoints.add(new PolygonPoint(curr.x, curr.y));
                continue;
            }

            // Otherwise, do the normal collinear math to strip out useless points
            double crossProduct = (curr.x - prev.x) * (next.y - curr.y) - (curr.y - prev.y) * (next.x - curr.x);

            if (Math.abs(crossProduct) > 0.001f) {
                cleanedPoints.add(new PolygonPoint(curr.x, curr.y));
            }
        }
        return cleanedPoints;
    }

    /**
     * Unions all intersected triangles to form a cavity, subtracts the building,
     * and returns clean Poly2Tri polygons.
     */
    public static List<org.poly2tri.geometry.polygon.Polygon> cutBuildingFromTriangles(
        com.badlogic.gdx.utils.Array<DelaunayTriangle> intersectedTriangles,
        float bX, float bY, float bW, float bH, Array<Vector2> protectedVertices) {

        // Guarantee tracker is empty before we start
        activeCoords.clear();

        try {
            List<Geometry> polygonsToMerge = new ArrayList<>();

            // 1. Make polygons from each triangle to form the cavity using POOLED coordinates
            for (DelaunayTriangle tri : intersectedTriangles) {
                Coordinate[] triCoords = new Coordinate[] {
                    obtainCoord(tri.points[0].getXf(), tri.points[0].getYf()),
                    obtainCoord(tri.points[1].getXf(), tri.points[1].getYf()),
                    obtainCoord(tri.points[2].getXf(), tri.points[2].getYf()),
                    obtainCoord(tri.points[0].getXf(), tri.points[0].getYf()) // Close the loop
                };
                Polygon triPoly = geoFactory.createPolygon(triCoords);
                polygonsToMerge.add(precisionReducer.reduce(triPoly).buffer(0));
            }

            // 2. Union them all together to get the total cavity area
            GeometryCollection geometryCollection = geoFactory.createGeometryCollection(
                polygonsToMerge.toArray(new Geometry[polygonsToMerge.size()])
            );
            Geometry cavityGeometry = geometryCollection.union();
            cavityGeometry = precisionReducer.reduce(cavityGeometry).buffer(0);

            // 3. Make a polygon for the building footprint using POOLED coordinates
            Coordinate[] buildingCoords = new Coordinate[] {
                obtainCoord(bX, bY),
                obtainCoord(bX + bW, bY),
                obtainCoord(bX + bW, bY + bH),
                obtainCoord(bX, bY + bH),
                obtainCoord(bX, bY) // Close the loop
            };
            Polygon buildingPoly = geoFactory.createPolygon(buildingCoords);
            Geometry validBuilding = precisionReducer.reduce(buildingPoly).buffer(0);

            // 4. Subtract the building from the cavity!
            Geometry result = cavityGeometry.difference(validBuilding);
            result = precisionReducer.reduce(result).buffer(0);

            // 5. Convert back to Poly2Tri, stripping out dangerous collinear vertices!
            List<org.poly2tri.geometry.polygon.Polygon> resultPolys = new ArrayList<>();

            if (result.isEmpty()) {
                return resultPolys; // The building completely filled the space
            }

            if (result instanceof Polygon) {
                resultPolys.add(convertToCleanPoly2Tri((Polygon) result, protectedVertices));
            } else if (result instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) result;
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    resultPolys.add(convertToCleanPoly2Tri((Polygon) mp.getGeometryN(i), protectedVertices));
                }
            }

            return resultPolys;

        } finally {
            // 6. Free all coordinates back to the pool to prevent GC spikes!
            coordPool.freeAll(activeCoords);
            activeCoords.clear();
        }
    }


}
