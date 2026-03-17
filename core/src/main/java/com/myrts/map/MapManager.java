package com.myrts.map;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.myrts.components.ResourceComponent;
import com.myrts.components.TransformComponent;
import org.poly2tri.Poly2Tri;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;
import org.poly2tri.triangulation.TriangulationPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import com.badlogic.gdx.math.Intersector;
//import com.badlogic.gdx.math.Polygon;

import java.util.ArrayList;
import java.util.List;

import static com.myrts.map.ContourTracer.simplifyPolygon;
import static com.myrts.map.NavMeshClipper.mergeTrianglesAndBuilding;

public class MapManager {

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;
    private boolean[][] collisionMap;
    private Array<DelaunayTriangle> navMeshTriangles;
    public Array<DelaunayTriangle> dTriangles;
    public Array<Vector2[]> dEdges;
    // Pre-allocate the arrays and polygons to prevent GC allocation during runtime
    private final float[] footprintVertices = new float[8];
    private final com.badlogic.gdx.math.Polygon footprintPoly = new com.badlogic.gdx.math.Polygon();

    private final float[] triVertices = new float[6];
    private final com.badlogic.gdx.math.Polygon triPoly = new com.badlogic.gdx.math.Polygon();

    public MapManager(String mapFilePath) {
        loadMap(mapFilePath);
        this.dTriangles = new Array<>();
        this.dEdges = new Array<>();
    }

    public void loadMap(String mapFilePath) {
        // Load the map
        map = new TmxMapLoader().load(mapFilePath);

        // Get map dimensions
        MapProperties props = map.getProperties();
        mapWidth = props.get("width", Integer.class);
        mapHeight = props.get("height", Integer.class);
        tileWidth = props.get("tilewidth", Integer.class);
        tileHeight = props.get("tileheight", Integer.class);

        // Initialize collision map
        collisionMap = new boolean[mapWidth][mapHeight];
        initializeCollisionMap();

        // After collision is ready, generate the NavMesh automatically
        generateNavMesh();

        // Create renderer
        renderer = new OrthogonalTiledMapRenderer(map);
    }

    private void generateNavMesh() {
        System.out.println("Tracing map contours...");
        List<Polygon> polygonsToTriangulate = ContourTracer.trace(this);

        System.out.println("Found " + polygonsToTriangulate.size() + " walkable areas. Triangulating...");
        this.navMeshTriangles = new Array<>();

        for (Polygon polygon : polygonsToTriangulate) {
            Poly2Tri.triangulate(polygon);
            for (DelaunayTriangle triangle : polygon.getTriangles()) {
                for (int i = 0; i < 3; i++) {
                    if (triangle.neighbors[i] != null && !triangle.neighbors[i].isInterior()) {
                        triangle.neighbors[i] = null; // Sever the tie to the dead triangle
                    }
                }
                navMeshTriangles.add(triangle);
            }
        }
        //linkNavMeshNeighbors();
        System.out.println("Generated navmesh with " + navMeshTriangles.size + " triangles.");
    }

    public Array<DelaunayTriangle> getNavMeshTriangles() {
        return navMeshTriangles;
    }

    private void initializeCollisionMap() {
        // Initialize all tiles as non-blocking
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                collisionMap[x][y] = false;
            }
        }

        // Find collision layer
        TiledMapTileLayer collisionLayer = (TiledMapTileLayer) map.getLayers().get("Collision");
        if (collisionLayer != null) {
            for (int x = 0; x < mapWidth; x++) {
                for (int y = 0; y < mapHeight; y++) {
                    TiledMapTileLayer.Cell cell = collisionLayer.getCell(x, y);
                    if (cell != null) {
                        collisionMap[x][y] = true;
                    }
                }
            }
        }
    }

    /**
     * Finds all navmesh triangles that intersect with a given rectangular footprint,
     * expanded or shrunk by a specific buffer amount.
     * @param x The world x-coordinate of the footprint's bottom-left corner.
     * @param y The world y-coordinate of the footprint's bottom-left corner.
     * @param width The width of the footprint.
     * @param height The height of the footprint.
     * @param buffer The amount to expand (positive) or shrink (negative) the footprint.
     * @return An Array containing all DelaunayTriangles that intersect the buffered footprint.
     */
    public Array<DelaunayTriangle> getIntersectingTriangles(float x, float y, float width, float height, float buffer) {
        Array<DelaunayTriangle> intersectingTriangles = new Array<>();

        if (navMeshTriangles == null || navMeshTriangles.isEmpty()) {
            return intersectingTriangles;
        }

        float minX = x - buffer;
        float minY = y - buffer;
        float maxX = x + width + buffer;
        float maxY = y + height + buffer;

        // Mutate the pre-allocated array
        footprintVertices[0] = minX;
        footprintVertices[1] = minY;
        footprintVertices[2] = maxX;
        footprintVertices[3] = minY;
        footprintVertices[4] = maxX;
        footprintVertices[5] = maxY;
        footprintVertices[6] = minX;
        footprintVertices[7] = maxY;

        // Update the polygon (this recalculates bounding boxes internally without allocating memory)
        footprintPoly.setVertices(footprintVertices);

        for (DelaunayTriangle tri : navMeshTriangles) {
            triVertices[0] = tri.points[0].getXf();
            triVertices[1] = tri.points[0].getYf();
            triVertices[2] = tri.points[1].getXf();
            triVertices[3] = tri.points[1].getYf();
            triVertices[4] = tri.points[2].getXf();
            triVertices[5] = tri.points[2].getYf();

            triPoly.setVertices(triVertices);

            if (Intersector.overlapConvexPolygons(footprintPoly, triPoly)) {
                intersectingTriangles.add(tri);
            }
        }

        return intersectingTriangles;
    }

    /**
     * Finds the outer perimeter edges of a group of triangles.
     * Also disconnects these triangles from the surviving navmesh graph.
     */
    public Array<Vector2[]> extractPerimeterEdges(Array<DelaunayTriangle> removedTriangles, Array<DelaunayTriangle> outBorderTriangles) {
        Array<Vector2[]> boundaryEdges = new Array<>();

        for (DelaunayTriangle tri : removedTriangles) {
            for (int i = 0; i < 3; i++) {
                DelaunayTriangle neighbor = tri.neighbors[i];

                if (neighbor == null || !removedTriangles.contains(neighbor, true)) {
                    TriangulationPoint p1 = tri.points[(i + 1) % 3];
                    TriangulationPoint p2 = tri.points[(i + 2) % 3];

                    Vector2 v1 = new Vector2(p1.getXf(), p1.getYf());
                    Vector2 v2 = new Vector2(p2.getXf(), p2.getYf());

                    boundaryEdges.add(new Vector2[]{v1, v2});

                    if (neighbor != null) {
                        neighbor.clearNeighbor(tri);
                        // Save this surviving neighbor to our localized list
                        if (!outBorderTriangles.contains(neighbor, true)) {
                            outBorderTriangles.add(neighbor);
                        }
                    }
                }
            }
        }
        System.out.println("Extracted " + boundaryEdges.size + " boundary edges and " + outBorderTriangles.size + " border triangles.");
        return boundaryEdges;
    }

    /**
     * Reconnects the newly generated triangles to the surviving navmesh graph.
     */
    /**
     * Reconnects the newly generated triangles to the surviving navmesh graph.
     */
    public void stitchNewTriangles(Array<DelaunayTriangle> newTriangles, Array<DelaunayTriangle> borderTriangles) {
        // 1. INCREASE EPSILON: Account for JTS PrecisionModel(10.0) rounding errors.
        float epsilon = 1.0f;

        for (DelaunayTriangle newTri : newTriangles) {
            for (int i = 0; i < 3; i++) {
                if (newTri.neighbors[i] == null) {
                    float nx1 = newTri.points[(i + 1) % 3].getXf();
                    float ny1 = newTri.points[(i + 1) % 3].getYf();
                    float nx2 = newTri.points[(i + 2) % 3].getXf();
                    float ny2 = newTri.points[(i + 2) % 3].getYf();

                    boolean matched = false;

                    // Search ONLY the handful of border triangles instead of the whole map
                    for (DelaunayTriangle oldTri : borderTriangles) {

                        for (int j = 0; j < 3; j++) {
                            if (oldTri.neighbors[j] == null) {
                                float ox1 = oldTri.points[(j + 1) % 3].getXf();
                                float oy1 = oldTri.points[(j + 1) % 3].getYf();
                                float ox2 = oldTri.points[(j + 2) % 3].getXf();
                                float oy2 = oldTri.points[(j + 2) % 3].getYf();

                                boolean match1 = (Math.abs(nx1 - ox2) < epsilon && Math.abs(ny1 - oy2) < epsilon) &&
                                    (Math.abs(nx2 - ox1) < epsilon && Math.abs(ny2 - oy1) < epsilon);
                                boolean match2 = (Math.abs(nx1 - ox1) < epsilon && Math.abs(ny1 - oy1) < epsilon) &&
                                    (Math.abs(nx2 - ox2) < epsilon && Math.abs(ny2 - oy2) < epsilon);

                                if (match1 || match2) {
                                    // Topologically link them
                                    newTri.neighbors[i] = oldTri;
                                    oldTri.neighbors[j] = newTri;

                                    // 2. CLEAR CONSTRAINED EDGES: Tell Poly2Tri/Pathfinding this is no longer a boundary wall
                                    newTri.cEdge[i] = false;
                                    oldTri.cEdge[j] = false;

                                    matched = true;
                                    break;
                                }
                            }
                        }

                        // 3. BREAK OUTER LOOP: Stop checking other border triangles once we find our match
                        if (matched) {
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("Finished stitching new triangles to existing navmesh.");
    }

    /**
     * Checks if a building of a specific tile dimension can be placed at the target tile coordinates.
     */
    public boolean canPlaceBuilding(int tileX, int tileY, int widthTiles, int heightTiles) {
        for (int x = 0; x < widthTiles; x++) {
            for (int y = 0; y < heightTiles; y++) {
                if (isCollision(tileX + x, tileY + y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean rebakeNavMeshRegion(float worldX, float worldY, float worldWidth, float worldHeight, boolean isPlacingBuilding) {
        // 1. Grab ALL overlapping triangles using the simple buffer
        Array<DelaunayTriangle> intersectedTriangles = getIntersectingTriangles(worldX, worldY, worldWidth, worldHeight, 0.1f);

        if (intersectedTriangles.isEmpty()) {
            if (isPlacingBuilding) {
                // We are placing a building in a spot that already has no NavMesh.
                // Nothing to cut. We can safely return.
                return true;
            }
            // If we are UNREGISTERING a building, we CANNOT return early!
            // We need to flow down to NavMeshClipper so it can turn this
            // empty void back into walkable triangles.
        }

        // 2. Sever them from the active map
        this.navMeshTriangles.removeAll(intersectedTriangles, false);

        // 3. Extract the border and perimeter edges
        Array<DelaunayTriangle> outBorderTriangles = new Array<>();
        Array<Vector2[]> extractedEdges = extractPerimeterEdges(intersectedTriangles, outBorderTriangles);

        if (!isPlacingBuilding) {
            this.dEdges = extractedEdges;
        }

        // 4. Collect protected T-junction vertices
        Array<Vector2> protectedVertices = new Array<>();
        for (DelaunayTriangle survivingTri : outBorderTriangles) {
            for (int i = 0; i < 3; i++) {
                if (survivingTri.neighbors[i] == null) {
                    float p1x = survivingTri.points[(i + 1) % 3].getXf();
                    float p1y = survivingTri.points[(i + 1) % 3].getYf();
                    float p2x = survivingTri.points[(i + 2) % 3].getXf();
                    float p2y = survivingTri.points[(i + 2) % 3].getYf();

                    protectedVertices.add(new Vector2(p1x, p1y));
                    protectedVertices.add(new Vector2(p2x, p2y));
                }
            }
        }

        // 5. Use JTS to hollow out or fill the cavity
        List<Polygon> p2tPolygons;
        if (isPlacingBuilding) {
            p2tPolygons = NavMeshClipper.cutBuildingFromTriangles(
                intersectedTriangles, worldX, worldY, worldWidth, worldHeight, protectedVertices
            );
        } else {
            p2tPolygons = NavMeshClipper.mergeTrianglesAndBuilding(
                intersectedTriangles, worldX, worldY, worldWidth, worldHeight, protectedVertices
            );
        }

        Array<DelaunayTriangle> freshlyGeneratedTriangles = new Array<>();

        // 6. Triangulate each piece JTS gave us
        for (Polygon polyToTriangulate : p2tPolygons) {
            try {
                Poly2Tri.triangulate(polyToTriangulate);

                for (DelaunayTriangle tri : polyToTriangulate.getTriangles()) {
                    for (int i = 0; i < 3; i++) {
                        if (tri.neighbors[i] != null && !tri.neighbors[i].isInterior()) {
                            tri.neighbors[i] = null;
                        }
                    }
                    freshlyGeneratedTriangles.add(tri);
                    this.navMeshTriangles.add(tri); // Add to main mesh
                }
            } catch (RuntimeException e) {
                System.err.println("CRITICAL: Triangulation failed during rebake - " + e.getMessage());

                // --- THE FALLBACK LOGIC ---

                // A. Remove any partial new triangles that might have been added before the crash
                for (DelaunayTriangle partialTri : freshlyGeneratedTriangles) {
                    this.navMeshTriangles.removeValue(partialTri, true);
                }

                // B. Restore the original triangles we severed in Step 2!
                // This heals the "hole" in the navmesh perfectly.
                this.navMeshTriangles.addAll(intersectedTriangles);

                // C. Re-link the original triangles to the border triangles so pathfinding doesn't break
                stitchNewTriangles(intersectedTriangles, outBorderTriangles);

                // D. Tell the caller the operation failed
                return false;
            }
        }

        // 7. If we made it here, triangulation succeeded! Stitch the newly baked space.
        if (freshlyGeneratedTriangles.size > 0 && outBorderTriangles.size > 0) {
            stitchNewTriangles(freshlyGeneratedTriangles, outBorderTriangles);
        }

        return true; // Success!
    }

    public boolean registerBuildingObstacle(float worldX, float worldY, float worldWidth, float worldHeight, int tilesW, int tilesH) {
        int startTileX = (int) (worldX / getTileWidth());
        int startTileY = (int) (worldY / getTileHeight());

        // 1. Temporarily block the tiles
        for (int x = 0; x < tilesW; x++) {
            for (int y = 0; y < tilesH; y++) {
                setTileBlocked(startTileX + x, startTileY + y, true);
            }
        }

        // 2. Attempt to rebake the NavMesh
        boolean navMeshSuccess = rebakeNavMeshRegion(worldX, worldY, worldWidth, worldHeight, true);

        // 3. If Poly2Tri crashed, revert the collision grid so the game state isn't corrupted!
        if (!navMeshSuccess) {
            System.err.println("Building placement rejected: NavMesh generation failed.");
            for (int x = 0; x < tilesW; x++) {
                for (int y = 0; y < tilesH; y++) {
                    setTileBlocked(startTileX + x, startTileY + y, false);
                }
            }
            return false; // Tell your ECS system or placement logic to cancel the building
        }

        return true; // The building is fully registered in both the Grid and NavMesh
    }

    public void unregisterBuildingObstacle(float worldX, float worldY, float worldWidth, float worldHeight) {
        int startTileX = (int) (worldX / getTileWidth());
        int startTileY = (int) (worldY / getTileHeight());
        int tilesW = (int) (worldWidth / getTileWidth());
        int tilesH = (int) (worldHeight / getTileHeight());

        // 1. Free up the collision grid immediately
        for (int x = 0; x < tilesW; x++) {
            for (int y = 0; y < tilesH; y++) {
                setTileBlocked(startTileX + x, startTileY + y, false);
            }
        }

        // 2. Delegate to the reusable rebake method (isPlacingBuilding = false)
        rebakeNavMeshRegion(worldX, worldY, worldWidth, worldHeight, false);
    }

    /**
     * Finds which NavMesh triangle contains the given world coordinates.
     * @return The DelaunayTriangle containing the point, or null if the point is off the mesh (in a wall/void).
     */
    public DelaunayTriangle getTriangleAt(float worldX, float worldY) {
        if (navMeshTriangles == null) return null;

        for (DelaunayTriangle tri : navMeshTriangles) {
            if (isPointInTriangle(worldX, worldY, tri)) {
                return tri;
            }
        }
        return null; // The player clicked on a blocked tile or outside the map!
    }

    /**
     * Mathematical check to see if a 2D point lies within a 3D triangle's 2D projection.
     */
    private boolean isPointInTriangle(float px, float py, DelaunayTriangle tri) {
        float p0x = tri.points[0].getXf();
        float p0y = tri.points[0].getYf();
        float p1x = tri.points[1].getXf();
        float p1y = tri.points[1].getYf();
        float p2x = tri.points[2].getXf();
        float p2y = tri.points[2].getYf();

        // Barycentric coordinate math
        float area = 0.5f * (-p1y * p2x + p0y * (-p1x + p2x) + p0x * (p1y - p2y) + p1x * p2y);

        // If the area is extremely close to 0, it's a degenerate triangle.
        if (area <= 0.0001f && area >= -0.0001f) return false;

        float s = 1 / (2 * area) * (p0y * p2x - p0x * p2y + (p2y - p0y) * px + (p0x - p2x) * py);
        float t = 1 / (2 * area) * (p0x * p1y - p0y * p1x + (p0y - p1y) * px + (p1x - p0x) * py);

        // If s, t, and 1-s-t are all >= 0, the point is inside!
        return s >= 0 && t >= 0 && (1 - s - t) >= 0;
    }

    public void createEntitiesFromMap(Engine engine) {
        // Process object layers to create entities
        MapLayer objectLayer = map.getLayers().get("Objects");
        if (objectLayer != null) {
            MapObjects objects = objectLayer.getObjects();
            for (MapObject object : objects) {
                float x = object.getProperties().get("x", Float.class);
                float y = object.getProperties().get("y", Float.class);
                String name = object.getName();
                MapProperties properties = object.getProperties();
                if (properties.containsKey("resourceType")) {
                    System.out.println("there is shit");
                }

                if (name != null) {
                    //Will need system to parse name and change things accordingly
                    if (name.equals("gold")) {
                        // Create resource entity
                        Entity resourceEntity = new Entity();

                        TransformComponent transform = new TransformComponent();
                        transform.position.set(x, y);

                        ResourceComponent resource = new ResourceComponent();
                        resource.amount = Integer.valueOf(object.getProperties().get("type", String.class));
                        resource.type = name;
                        resourceEntity.add(transform);
                        resourceEntity.add(resource);

                        engine.addEntity(resourceEntity);
                    }
                    // Add more entity types as needed
                }
            }
        }
    }

    public void render(OrthographicCamera camera) {
        renderer.setView(camera);
        renderer.render();
    }

    public boolean isCollision(int tileX, int tileY) {
        if (tileX < 0 || tileX >= mapWidth || tileY < 0 || tileY >= mapHeight) {
            return true; // Out of bounds is considered collision
        }
        return collisionMap[tileX][tileY];
    }

    public boolean isCollision(float worldX, float worldY) {
        int tileX = (int) (worldX / tileWidth);
        int tileY = (int) (worldY / tileHeight);
        return isCollision(tileX, tileY);
    }

    public Vector2 worldToTile(float worldX, float worldY) {
        return new Vector2((int) (worldX / tileWidth), (int) (worldY / tileHeight));
    }

    public void setTileBlocked(int tileX, int tileY, boolean isBlocked) {
        if (tileX < 0 || tileX >= mapWidth || tileY < 0 || tileY >= mapHeight) {
            return; // Out of bounds
        }
        collisionMap[tileX][tileY] = isBlocked;
    }

    public Vector2 tileToWorld(int tileX, int tileY) {
        return new Vector2(tileX * tileWidth, tileY * tileHeight);
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public int getPixelWidth() {
        return mapWidth * tileWidth;
    }

    public int getPixelHeight() {
        return mapHeight * tileHeight;
    }

    public TiledMap getMap() { return map; }

    public boolean[][] getCollisionMap() { return collisionMap; }

    public void dispose() {
        map.dispose();
        renderer.dispose();
    }
}
