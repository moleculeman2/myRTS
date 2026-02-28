package com.myrts.map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.LineString;
import org.poly2tri.geometry.polygon.PolygonPoint;

import java.util.ArrayList;
import java.util.List;

public class NavMeshClipper {

    private static final GeometryFactory geoFactory = new GeometryFactory();

    private static final Coordinate[] reusableBuildingCoords = new Coordinate[] {
        new Coordinate(),
        new Coordinate(),
        new Coordinate(),
        new Coordinate(),
        new Coordinate()
    };

    /**
     * Subtracts the building footprint from the navmesh cavity and returns
     * Poly2Tri polygons ready for triangulation.
     */
    public static List<org.poly2tri.geometry.polygon.Polygon> subtractBuilding(
        List<ContourTracer.Point> cavityPath, float bX, float bY, float bW, float bH) {

        // --- NEW SAFETY CHECK ---
        if (cavityPath.size() < 3) {
            System.err.println("Warning: Invalid cavity path detected. Skipping triangulation.");
            return new ArrayList<>();
        }

        // 1. Convert our Cavity Path to JTS Coordinates
        // JTS requires the loop to be explicitly closed (last point == first point)
        Coordinate[] cavityCoords = new Coordinate[cavityPath.size() + 1];
        for (int i = 0; i < cavityPath.size(); i++) {
            cavityCoords[i] = new Coordinate(cavityPath.get(i).x, cavityPath.get(i).y);
        }
        cavityCoords[cavityPath.size()] = cavityCoords[0]; // Close the loop

        Polygon cavityPoly = geoFactory.createPolygon(cavityCoords);

        // --- NEW: The JTS Geometry Healer ---
        // Calling buffer(0) forces JTS to resolve any self-intersections
        // or duplicate segments before doing the difference math.
        //Geometry validCavity = cavityPoly.buffer(0);

        // 2. REUSE the pre-allocated building array by mutating the values directly
        reusableBuildingCoords[0].x = bX;
        reusableBuildingCoords[0].y = bY;

        reusableBuildingCoords[1].x = bX + bW;
        reusableBuildingCoords[1].y = bY;

        reusableBuildingCoords[2].x = bX + bW;
        reusableBuildingCoords[2].y = bY + bH;

        reusableBuildingCoords[3].x = bX;
        reusableBuildingCoords[3].y = bY + bH;

        reusableBuildingCoords[4].x = bX;
        reusableBuildingCoords[4].y = bY;

        Polygon buildingPoly = geoFactory.createPolygon(reusableBuildingCoords);

        // 3. THE MATH: Subtract the building from the cavity!
        Geometry result = cavityPoly.difference(buildingPoly);

        // 4. Parse the output back into Poly2Tri format
        List<org.poly2tri.geometry.polygon.Polygon> p2tPolygons = new ArrayList<>();

        if (result instanceof Polygon) {
            p2tPolygons.add(convertToPoly2Tri((Polygon) result));
        } else if (result instanceof MultiPolygon) {
            // The building cut the cavity completely in two!
            MultiPolygon mp = (MultiPolygon) result;
            for (int i = 0; i < mp.getNumGeometries(); i++) {
                p2tPolygons.add(convertToPoly2Tri((Polygon) mp.getGeometryN(i)));
            }
        }

        return p2tPolygons;
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
}
