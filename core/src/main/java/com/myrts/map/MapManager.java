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
import org.poly2tri.triangulation.TriangulationPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import com.badlogic.gdx.math.Intersector;
//import com.badlogic.gdx.math.Polygon;

import java.util.List;

public class MapManager {

    private TiledMap map;
    private TiledMapRenderer renderer;
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;
    private boolean[][] collisionMap;
    private Array<DelaunayTriangle> navMeshTriangles;
    public Array<DelaunayTriangle> dTriangles;
    public Array<Vector2[]> dEdges;

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
     * Finds and removes any navmesh triangles that intersect with the given bounding box.
     * @return An Array of the removed DelaunayTriangles.
     */
    public Array<DelaunayTriangle> removeBuildingIntersectingTriangles(float x, float y, float width, float height) {
        // 1. Get the intersecting triangles using our helper method
        // Large Search (Full size)
        Array<DelaunayTriangle> fullSearchSet = getIntersectingTriangles(x, y, width, height, 0f);
        // Small Search (Inset by 0.1f to shrink the box)
        Array<DelaunayTriangle> smallSearchSet = getIntersectingTriangles(x, y, width, height, -0.1f);

        Array<DelaunayTriangle> removedTriangles = new Array<>(smallSearchSet); // Everything strictly inside is removed
        Array<DelaunayTriangle> survivingTriangles = new Array<>();
        Array<DelaunayTriangle> borderCandidates = new Array<>();

        // 2. First Pass: Categorize all triangles based on the sets we fetched
        for (DelaunayTriangle tri : navMeshTriangles) {
            if (smallSearchSet.contains(tri, true)) {
                // Strictly inside/overlapping (already added to removedTriangles)
                continue;
            } else if (fullSearchSet.contains(tri, true)) {
                // Touching the outer border (Could be parallel edge OR armpit vertex)
                borderCandidates.add(tri);
            } else {
                // Completely outside and safe
                survivingTriangles.add(tri);
            }
        }

        // 3. Second Pass: Filter the border candidates topologically!
        for (DelaunayTriangle candidate : borderCandidates) {
            boolean sharesEdgeWithInterior = false;

            // Check if this candidate shares a mathematical edge with ANY triangle in the small search set
            for (int i = 0; i < 3; i++) {
                DelaunayTriangle neighbor = candidate.neighbors[i];
                // 'true' uses identity (==) check for speed since these are the exact same instances in memory
                if (neighbor != null && smallSearchSet.contains(neighbor, true)) {
                    sharesEdgeWithInterior = true;
                    break;
                }
            }

            if (sharesEdgeWithInterior) {
                // It shares an edge! It's a parallel-edge triangle, so we must re-triangulate it.
                removedTriangles.add(candidate);
            } else {
                // It's an armpit triangle (only shares a vertex) or a false positive. Keep it!
                survivingTriangles.add(candidate);
            }
        }

        // 4. Update the map's active navmesh list
        this.navMeshTriangles = survivingTriangles;

        System.out.println("Removed " + removedTriangles.size + " triangles. " + survivingTriangles.size + " remain.");

        return removedTriangles;
    }

    /**
     * Finds all navmesh triangles that intersect with a given rectangular footprint,
     * expanded or shrunk by a specific buffer amount.
     * * @param x The world x-coordinate of the footprint's bottom-left corner.
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

        // Apply the buffer to the footprint bounds
        float minX = x - buffer;
        float minY = y - buffer;
        float maxX = x + width + buffer;
        float maxY = y + height + buffer;

        // Create a LibGDX Polygon for the buffered footprint
        float[] footprintVertices = new float[] {
            minX, minY,
            maxX, minY,
            maxX, maxY,
            minX, maxY
        };
        com.badlogic.gdx.math.Polygon footprintPoly = new com.badlogic.gdx.math.Polygon(footprintVertices);

        // Pre-allocate a polygon to reuse for each triangle to avoid garbage collection overhead
        float[] triVertices = new float[6];
        com.badlogic.gdx.math.Polygon triPoly = new com.badlogic.gdx.math.Polygon(triVertices);

        // Loop through the navmesh and test for intersection
        for (DelaunayTriangle tri : navMeshTriangles) {
            triVertices[0] = tri.points[0].getXf();
            triVertices[1] = tri.points[0].getYf();
            triVertices[2] = tri.points[1].getXf();
            triVertices[3] = tri.points[1].getYf();
            triVertices[4] = tri.points[2].getXf();
            triVertices[5] = tri.points[2].getYf();
            triPoly.setVertices(triVertices);

            // Check if the footprint overlaps this specific triangle
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

    /**
     * Registers a new building obstacle, updating the collision grid and re-baking the local NavMesh.
     */
    public void registerBuildingObstacle(float worldX, float worldY, float worldWidth, float worldHeight, int tilesW, int tilesH) {
        // 1. Update Tile Collision Map
        int startTileX = (int) (worldX / getTileWidth());
        int startTileY = (int) (worldY / getTileHeight());

        for (int x = 0; x < tilesW; x++) {
            for (int y = 0; y < tilesH; y++) {
                setTileBlocked(startTileX + x, startTileY + y, true);
            }
        }

        // 2. Cut the hole in the NavMesh
        Array<DelaunayTriangle> removed = removeBuildingIntersectingTriangles(worldX, worldY, worldWidth, worldHeight);

        Array<DelaunayTriangle> survivingBorderTriangles = new Array<>();
        Array<Vector2[]> boundaryEdges = extractPerimeterEdges(removed, survivingBorderTriangles);

        // 3. Let ContourTracer stitch them into an ordered polygon
        List<List<ContourTracer.Point>> polygons = ContourTracer.assemblePolygons(boundaryEdges);

        if (polygons.size() > 0) {
            System.out.println("Successfully assembled " + polygons.size() + " perimeter polygons!");
        }

        // 4. Subtract building and Triangulate
        for (List<ContourTracer.Point> cavityPath : polygons) {
            List<Polygon> p2tPolygons = NavMeshClipper.subtractBuilding(
                cavityPath, worldX, worldY, worldWidth, worldHeight
            );

            Array<DelaunayTriangle> freshlyGeneratedTriangles = new Array<>();

            for (Polygon polyToTriangulate : p2tPolygons) {
                Poly2Tri.triangulate(polyToTriangulate);

                // Collect the new triangles
                for (DelaunayTriangle tri : polyToTriangulate.getTriangles()) {
                    for (int i = 0; i < 3; i++) {
                        if (tri.neighbors[i] != null && !tri.neighbors[i].isInterior()) {
                            tri.neighbors[i] = null;
                        }
                    }
                    freshlyGeneratedTriangles.add(tri);
                    getNavMeshTriangles().add(tri); // Add to main map list
                }
            }

            // 5. Stitch the graph back together
            stitchNewTriangles(freshlyGeneratedTriangles, survivingBorderTriangles);
        }
    }

    /**
     * Unregisters a building obstacle, freeing up the collision grid.
     * (NavMesh re-stitching to be implemented later).
     */
    public void unregisterBuildingObstacle(float worldX, float worldY, float worldWidth, float worldHeight) {
        int startTileX = (int) (worldX / getTileWidth());
        int startTileY = (int) (worldY / getTileHeight());
        int tilesW = (int) (worldWidth / getTileWidth());
        int tilesH = (int) (worldHeight / getTileHeight());

        float tileW = getTileWidth();
        float tileH = getTileHeight();

        Array<Vector2[]> openEdges = new Array<>();
        Array<Vector2[]> blockedEdges = new Array<>();

        // 1. Identify the perimeter edges before freeing the tiles
        for (int x = 0; x < tilesW; x++) {
            for (int y = 0; y < tilesH; y++) {
                int currentTileX = startTileX + x;
                int currentTileY = startTileY + y;

                // Check Left Neighbor
                if (x == 0) {
                    int nx = currentTileX - 1;
                    int ny = currentTileY;
                    Vector2 p1 = new Vector2(currentTileX * tileW, currentTileY * tileH);
                    Vector2 p2 = new Vector2(currentTileX * tileW, (currentTileY + 1) * tileH);

                    if (isCollision(nx, ny)) blockedEdges.add(new Vector2[]{p1, p2});
                    else openEdges.add(new Vector2[]{p1, p2});
                }

                // Check Right Neighbor
                if (x == tilesW - 1) {
                    int nx = currentTileX + 1;
                    int ny = currentTileY;
                    Vector2 p1 = new Vector2((currentTileX + 1) * tileW, currentTileY * tileH);
                    Vector2 p2 = new Vector2((currentTileX + 1) * tileW, (currentTileY + 1) * tileH);

                    if (isCollision(nx, ny)) blockedEdges.add(new Vector2[]{p1, p2});
                    else openEdges.add(new Vector2[]{p1, p2});
                }

                // Check Bottom Neighbor
                if (y == 0) {
                    int nx = currentTileX;
                    int ny = currentTileY - 1;
                    Vector2 p1 = new Vector2(currentTileX * tileW, currentTileY * tileH);
                    Vector2 p2 = new Vector2((currentTileX + 1) * tileW, currentTileY * tileH);

                    if (isCollision(nx, ny)) blockedEdges.add(new Vector2[]{p1, p2});
                    else openEdges.add(new Vector2[]{p1, p2});
                }

                // Check Top Neighbor
                if (y == tilesH - 1) {
                    int nx = currentTileX;
                    int ny = currentTileY + 1;
                    Vector2 p1 = new Vector2(currentTileX * tileW, (currentTileY + 1) * tileH);
                    Vector2 p2 = new Vector2((currentTileX + 1) * tileW, (currentTileY + 1) * tileH);

                    if (isCollision(nx, ny)) blockedEdges.add(new Vector2[]{p1, p2});
                    else openEdges.add(new Vector2[]{p1, p2});
                }
            }
        }

        // 2. Free up the collision grid
        for (int x = 0; x < tilesW; x++) {
            for (int y = 0; y < tilesH; y++) {
                setTileBlocked(startTileX + x, startTileY + y, false);
            }
        }

        // 3. Merge collinear edges
        openEdges = mergeCollinearEdges(openEdges);
        blockedEdges = mergeCollinearEdges(blockedEdges);

        // 4. Find intersecting triangles and identify ones sharing blocked edges
        Array<DelaunayTriangle> intersectingTriangles = getIntersectingTriangles(worldX, worldY, worldWidth, worldHeight, 0.1f);
        Array<DelaunayTriangle> deletedTriangles = new Array<>();

        float epsilon = 0.1f;

        System.out.println("triangles found: " + intersectingTriangles.size);

        for (DelaunayTriangle tri : intersectingTriangles) {
            int matchCount = 0;

            // Loop through each vertex on that triangle
            for (int i = 0; i < 3; i++) {
                matchCount = 0;
                float tx = tri.points[i].getXf();
                float ty = tri.points[i].getYf();
                // Loop through blockedEdges to check for a vertex match
                for (Vector2[] edge : blockedEdges) {
                    if (edge[0].epsilonEquals(tx, ty, epsilon) || edge[1].epsilonEquals(tx, ty, epsilon)) {
                        matchCount++;
                        if (matchCount == 2) {
                            deletedTriangles.add(tri);
                        }
                    }
                }
            }
        }

        System.out.println("Identified " + deletedTriangles.size + " triangles to delete based on blocked edges.");
        intersectingTriangles.removeAll(deletedTriangles, false);

        this.dTriangles.addAll(deletedTriangles);

        Array<DelaunayTriangle> outBorderTriangles = new Array<>();
        Array<Vector2[]> boundaryEdges = extractPerimeterEdges(intersectingTriangles, outBorderTriangles);
        boundaryEdges.addAll(blockedEdges);
        boundaryEdges.removeAll(openEdges, false);
        Array<Vector2[]> toRemove = new Array<>();
        for (Vector2[] edge : boundaryEdges){
            for (Vector2[] oEdge : openEdges){
                if (edge[0].epsilonEquals(oEdge[0], 0.1f) && edge[1].epsilonEquals(oEdge[1], 0.1f)){
                    toRemove.add(edge);
                    System.out.println("found equal edge");
                }
                else if (edge[0].epsilonEquals(oEdge[1], 0.1f) && edge[1].epsilonEquals(oEdge[0], 0.1f)){
                    toRemove.add(edge);
                    System.out.println("found equal edge2");
                }
            }
        }
        boundaryEdges.removeAll(toRemove, false);
        this.dEdges.addAll(boundaryEdges);

        List<List<ContourTracer.Point>> polygons = ContourTracer.assemblePolygons(boundaryEdges);

        if (polygons.size() > 0) {
            System.out.println("Successfully assembled " + polygons.size() + " perimeter polygons!");
        }

        // TODO: Future NavMesh remeshing logic goes here
    }

    /**
     * Takes an Array of line segments and continuously merges any segments that
     * share an endpoint and travel in the same direction (collinear).
     */
    private Array<Vector2[]> mergeCollinearEdges(Array<Vector2[]> edges) {
        boolean mergedAny = true;
        float epsilon = 0.001f; // Account for floating point inaccuracies

        while (mergedAny) {
            mergedAny = false;

            for (int i = 0; i < edges.size; i++) {
                for (int j = i + 1; j < edges.size; j++) {
                    Vector2[] e1 = edges.get(i);
                    Vector2[] e2 = edges.get(j);

                    Vector2 sharedPoint = null;
                    Vector2 p1 = null;
                    Vector2 p2 = null;

                    // Find if they share an exact endpoint
                    if (e1[0].epsilonEquals(e2[0], epsilon)) {
                        sharedPoint = e1[0]; p1 = e1[1]; p2 = e2[1];
                    } else if (e1[0].epsilonEquals(e2[1], epsilon)) {
                        sharedPoint = e1[0]; p1 = e1[1]; p2 = e2[0];
                    } else if (e1[1].epsilonEquals(e2[0], epsilon)) {
                        sharedPoint = e1[1]; p1 = e1[0]; p2 = e2[1];
                    } else if (e1[1].epsilonEquals(e2[1], epsilon)) {
                        sharedPoint = e1[1]; p1 = e1[0]; p2 = e2[0];
                    }

                    // If they touch, check if they form a straight line
                    if (sharedPoint != null) {
                        // Calculate cross product to check collinearity
                        float dx1 = sharedPoint.x - p1.x;
                        float dy1 = sharedPoint.y - p1.y;
                        float dx2 = p2.x - sharedPoint.x;
                        float dy2 = p2.y - sharedPoint.y;

                        float crossProduct = (dx1 * dy2) - (dy1 * dx2);

                        if (Math.abs(crossProduct) < epsilon) {
                            // They are collinear! Remove the two small segments and add the merged one
                            edges.removeIndex(j); // Remove highest index first so 'i' doesn't shift unexpectedly
                            edges.removeIndex(i);
                            edges.add(new Vector2[]{p1, p2});

                            mergedAny = true;
                            break; // Break inner loop and restart to prevent index out of bounds
                        }
                    }
                }
                if (mergedAny) break; // Break outer loop to restart from the beginning
            }
        }
        return edges;
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
    }

}
