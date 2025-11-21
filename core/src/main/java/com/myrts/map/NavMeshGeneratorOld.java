package com.myrts.map;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.DelaunayTriangulator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

public class NavMeshGeneratorOld {

    /**
     * Generates a navigation mesh from a TiledMap.
     *
     * @param map The TiledMap to process.
     * @return A NavMesh object containing the vertices and triangle indices.
     */
    public static Array<Triangle> generate(TiledMap map) {
        // This array will hold the x,y coordinates of unique walkable corners
        FloatArray walkablePoints = new FloatArray();

        // 1. Get map properties
        TiledMapTileLayer collisionLayer = (TiledMapTileLayer) map.getLayers().get("Collision");
        if (collisionLayer == null) {
            System.err.println("Error: TiledMap must have a layer named 'Collision'");
            return new Array<>(); // Return empty list
        }

        int mapWidthInTiles = map.getProperties().get("width", Integer.class);
        int mapHeightInTiles = map.getProperties().get("height", Integer.class);
        float tileWidth = map.getProperties().get("tilewidth", Integer.class);
        float tileHeight = map.getProperties().get("tileheight", Integer.class);

        // 2. Loop through every VERTEX (corner) of the grid, not every tile.
        //    The vertex grid is one larger than the tile grid in both dimensions.
        for (int y = 0; y <= mapHeightInTiles; y++) {
            for (int x = 0; x <= mapWidthInTiles; x++) {
                // A vertex is defined by the 4 tiles surrounding it.
                int walkable = 0;
                boolean isEdgePoint = false;
                boolean topLeft = isWalkableAt(collisionLayer, x - 1, y, mapWidthInTiles, mapHeightInTiles);
                boolean topRight = isWalkableAt(collisionLayer, x, y, mapWidthInTiles, mapHeightInTiles);
                boolean botLeft = isWalkableAt(collisionLayer, x - 1, y - 1, mapWidthInTiles, mapHeightInTiles);
                boolean botRight = isWalkableAt(collisionLayer, x, y - 1, mapWidthInTiles, mapHeightInTiles);

                if (topLeft) walkable++;
                if (topRight) walkable++;
                if (botLeft) walkable++;
                if (botRight) walkable++;
                if (walkable == 1 || walkable == 3) isEdgePoint = true;
                if (walkable == 2){
                    if ((botRight && topLeft) || (botLeft && topRight)) isEdgePoint = true;
                    if ( (topLeft && topRight) || (topRight && botRight) ||
                        (botRight && botLeft) || (botLeft && topLeft) ) isEdgePoint = false;
                }

                if (isEdgePoint) {
                    // This vertex touches a walkable tile, so add its coordinates.
                    walkablePoints.add(x * tileWidth);
                    walkablePoints.add(y * tileHeight);
                }
            }
        }

        // 3. Perform Delaunay triangulation
        DelaunayTriangulator triangulator = new DelaunayTriangulator();
        ShortArray triangleIndices = triangulator.computeTriangles(walkablePoints, false);

        // --- NEW: Filter triangles to create constraints ---
        Array<Triangle> validTriangles = new Array<>();
        for (int i = 0; i < triangleIndices.size; i += 3) {
            // Get the three vertices of the triangle
            int p1_index = triangleIndices.get(i) * 2;
            int p2_index = triangleIndices.get(i + 1) * 2;
            int p3_index = triangleIndices.get(i + 2) * 2;

            Vector2 p1 = new Vector2(walkablePoints.get(p1_index), walkablePoints.get(p1_index + 1));
            Vector2 p2 = new Vector2(walkablePoints.get(p2_index), walkablePoints.get(p2_index + 1));
            Vector2 p3 = new Vector2(walkablePoints.get(p3_index), walkablePoints.get(p3_index + 1));

            // Calculate the centroid (center point) of the triangle
            float centroidX = (p1.x + p2.x + p3.x) / 3;
            float centroidY = (p1.y + p2.y + p3.y) / 3;

            // Convert centroid's world coordinates to tile coordinates
            int tileX = (int) (centroidX / tileWidth);
            int tileY = (int) (centroidY / tileHeight);

            // If the centroid is on a walkable tile, the triangle is valid
            if (isWalkableAt(collisionLayer, tileX, tileY)) {
                validTriangles.add(new Triangle(p1, p2, p3));
            }
        }

        System.out.println("NavMesh constrained to " + validTriangles.size + " triangles.");
        return validTriangles;
    }

    /**
     * Helper method to check if a specific tile coordinate is walkable.
     * It handles out-of-bounds checks and checks the collision layer.
     */
    private static boolean isWalkableAt(TiledMapTileLayer collisionLayer, int x, int y, int mapWidth, int mapHeight) {
        // Check if the coordinates are outside the map boundaries
        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) {
            return false;
        }

        // A cell is null if there is no tile on that layer, meaning it's walkable.
        return collisionLayer.getCell(x, y) == null;
    }

    /**
     * Helper method to check if a tile is walkable.
     * We remove map dimensions from parameters as the layer knows its own size.
     */
    private static boolean isWalkableAt(TiledMapTileLayer collisionLayer, int x, int y) {
        if (x < 0 || x >= collisionLayer.getWidth() || y < 0 || y >= collisionLayer.getHeight()) {
            return false;
        }
        return collisionLayer.getCell(x, y) == null;
    }
}

