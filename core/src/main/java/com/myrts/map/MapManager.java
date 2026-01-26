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
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

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
