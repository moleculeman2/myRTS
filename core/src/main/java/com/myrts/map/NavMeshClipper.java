package com.myrts.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.poly2tri.geometry.polygon.PolygonPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

import java.util.ArrayList;
import java.util.List;

public class NavMeshClipper {

    private static final GeometryFactory geoFactory = new GeometryFactory();
    private static final PrecisionModel precisionModel = new PrecisionModel(10.0);
    private static final GeometryPrecisionReducer precisionReducer = new GeometryPrecisionReducer(precisionModel);
    private static final Coordinate[] reusableBuildingCoords = new Coordinate[] {
        new Coordinate(),
        new Coordinate(),
        new Coordinate(),
        new Coordinate(),
        new Coordinate()
    };

    /**
     * Merges the building footprint and all provided intersected triangles into a List of
     * Poly2Tri Polygons, snapped to the grid, with collinear vertices removed.
     */
    public static List<org.poly2tri.geometry.polygon.Polygon> mergeTrianglesAndBuilding(
        Array<DelaunayTriangle> intersectedTriangles,
        float bX, float bY, float bW, float bH, Array<Vector2> protectedVertices) {

        List<Geometry> polygonsToMerge = new ArrayList<>();

        // 1. Make a polygon from the building footprint info
        Coordinate[] buildingCoords = new Coordinate[] {
            new Coordinate(bX, bY),
            new Coordinate(bX + bW, bY),
            new Coordinate(bX + bW, bY + bH),
            new Coordinate(bX, bY + bH),
            new Coordinate(bX, bY) // Close the loop
        };
        Polygon buildingPoly = geoFactory.createPolygon(buildingCoords);
        polygonsToMerge.add(precisionReducer.reduce(buildingPoly).buffer(0));

        // 2. Make polygons from each triangle
        for (DelaunayTriangle tri : intersectedTriangles) {
            Coordinate[] triCoords = new Coordinate[] {
                new Coordinate(tri.points[0].getX(), tri.points[0].getY()),
                new Coordinate(tri.points[1].getX(), tri.points[1].getY()),
                new Coordinate(tri.points[2].getX(), tri.points[2].getY()),
                new Coordinate(tri.points[0].getX(), tri.points[0].getY()) // Close the loop
            };
            Polygon triPoly = geoFactory.createPolygon(triCoords);
            polygonsToMerge.add(precisionReducer.reduce(triPoly).buffer(0));
        }

        // 3. Merge them all together into one single polygon
        GeometryCollection geometryCollection = geoFactory.createGeometryCollection(
            polygonsToMerge.toArray(new Geometry[0])
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

    private static org.poly2tri.geometry.polygon.Polygon convertToPoly2Tri(Polygon jtsPolygon) {
        // Extract Outer Boundary
        Coordinate[] outerCoords = jtsPolygon.getExteriorRing().getCoordinates();
        // Poly2Tri does NOT want the closed duplicate point at the end
        List<PolygonPoint> p2tOuter = new ArrayList<>();
        for (int i = 0; i < outerCoords.length - 1; i++) {
            p2tOuter.add(new PolygonPoint(outerCoords[i].x, outerCoords[i].y));
        }

        org.poly2tri.geometry.polygon.Polygon p2tPoly = new org.poly2tri.geometry.polygon.Polygon(p2tOuter);

        // Extract any Interior Holes (e.g., if the building was completely inside the cavity)
        for (int i = 0; i < jtsPolygon.getNumInteriorRing(); i++) {
            Coordinate[] holeCoords = jtsPolygon.getInteriorRingN(i).getCoordinates();
            List<PolygonPoint> p2tHole = new ArrayList<>();
            for (int j = 0; j < holeCoords.length - 1; j++) {
                p2tHole.add(new PolygonPoint(holeCoords[j].x, holeCoords[j].y));
            }
            p2tPoly.addHole(new org.poly2tri.geometry.polygon.Polygon(p2tHole));
        }

        return p2tPoly;
    }

    /**
     * Unions all intersected triangles to form a cavity, subtracts the building,
     * and returns clean Poly2Tri polygons.
     */
    public static List<org.poly2tri.geometry.polygon.Polygon> cutBuildingFromTriangles(
        com.badlogic.gdx.utils.Array<DelaunayTriangle> intersectedTriangles,
        float bX, float bY, float bW, float bH, Array<Vector2> protectedVertices) {

        List<Geometry> polygonsToMerge = new ArrayList<>();

        // 1. Make polygons from each triangle to form the cavity
        for (DelaunayTriangle tri : intersectedTriangles) {
            Coordinate[] triCoords = new Coordinate[] {
                new Coordinate(tri.points[0].getX(), tri.points[0].getY()),
                new Coordinate(tri.points[1].getX(), tri.points[1].getY()),
                new Coordinate(tri.points[2].getX(), tri.points[2].getY()),
                new Coordinate(tri.points[0].getX(), tri.points[0].getY()) // Close the loop
            };
            Polygon triPoly = geoFactory.createPolygon(triCoords);
            polygonsToMerge.add(precisionReducer.reduce(triPoly).buffer(0));
        }

        // 2. Union them all together to get the total cavity area
        GeometryCollection geometryCollection = geoFactory.createGeometryCollection(
            polygonsToMerge.toArray(new Geometry[0])
        );
        Geometry cavityGeometry = geometryCollection.union();
        cavityGeometry = precisionReducer.reduce(cavityGeometry).buffer(0);

        // 3. Make a polygon for the building footprint
        Coordinate[] buildingCoords = new Coordinate[] {
            new Coordinate(bX, bY),
            new Coordinate(bX + bW, bY),
            new Coordinate(bX + bW, bY + bH),
            new Coordinate(bX, bY + bH),
            new Coordinate(bX, bY) // Close the loop
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
    }
}
