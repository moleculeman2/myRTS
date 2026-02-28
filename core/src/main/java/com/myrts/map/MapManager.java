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

    public MapManager(String mapFilePath) {
        loadMap(mapFilePath);
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
                navMeshTriangles.add(triangle);
            }
        }
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
    public Array<DelaunayTriangle> removeIntersectingTriangles(float x, float y, float width, float height) {
        // 1. Create a LibGDX Polygon for the building's bounding box
        float[] rectVertices = new float[] {
            x, y,
            x + width, y,
            x + width, y + height,
            x, y + height
        };
        com.badlogic.gdx.math.Polygon buildingPoly = new com.badlogic.gdx.math.Polygon(rectVertices);

        Array<DelaunayTriangle> removedTriangles = new Array<>();
        Array<DelaunayTriangle> survivingTriangles = new Array<>();

        float[] triVertices = new float[6];
        com.badlogic.gdx.math.Polygon triPoly = new com.badlogic.gdx.math.Polygon(triVertices);

        // 2. Check each triangle for an intersection
        for (DelaunayTriangle tri : navMeshTriangles) {
            // Convert Poly2Tri triangle to a LibGDX Polygon for the Intersector
            triVertices[0] = tri.points[0].getXf();
            triVertices[1] = tri.points[0].getYf();
            triVertices[2] = tri.points[1].getXf();
            triVertices[3] = tri.points[1].getYf();
            triVertices[4] = tri.points[2].getXf();
            triVertices[5] = tri.points[2].getYf();
            triPoly.setVertices(triVertices);
            // overlapConvexPolygons perfectly checks if two convex shapes intersect
            if (Intersector.overlapConvexPolygons(buildingPoly, triPoly)) {
                removedTriangles.add(tri);
            } else {
                survivingTriangles.add(tri);
            }
        }

        // 3. Update the map's active navmesh list to only include the survivors
        this.navMeshTriangles = survivingTriangles;

        System.out.println("Removed " + removedTriangles.size + " triangles. " + survivingTriangles.size + " remain.");

        return removedTriangles;
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
    public void stitchNewTriangles(Array<DelaunayTriangle> newTriangles, Array<DelaunayTriangle> borderTriangles) {
        float epsilon = 0.1f;

        for (DelaunayTriangle newTri : newTriangles) {
            for (int i = 0; i < 3; i++) {
                if (newTri.neighbors[i] == null) {
                    float nx1 = newTri.points[(i + 1) % 3].getXf();
                    float ny1 = newTri.points[(i + 1) % 3].getYf();
                    float nx2 = newTri.points[(i + 2) % 3].getXf();
                    float ny2 = newTri.points[(i + 2) % 3].getYf();

                    // Search ONLY the handful of border triangles instead of the whole map
                    for (DelaunayTriangle oldTri : borderTriangles) {

                        // (You can remove the "if (newTriangles.contains(oldTri...)" check now,
                        // because we know borderTriangles only contains old, surviving mesh triangles!)

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
                                    newTri.neighbors[i] = oldTri;
                                    oldTri.neighbors[j] = newTri;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Finished stitching new triangles to existing navmesh.");
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
