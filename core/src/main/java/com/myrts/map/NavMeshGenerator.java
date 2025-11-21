package com.myrts.map;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class NavMeshGenerator {

    public static Array<Triangle> generate(TiledMap map) {
        TiledMapTileLayer collisionLayer = (TiledMapTileLayer) map.getLayers().get("Collision");
        if (collisionLayer == null) {
            System.err.println("Error: TiledMap must have a layer named 'Collision'");
            return new Array<>();
        }

        int mapWidth = collisionLayer.getWidth();
        int mapHeight = collisionLayer.getHeight();
        float tileWidth = collisionLayer.getTileWidth();
        float tileHeight = collisionLayer.getTileHeight();

        // A grid to keep track of tiles we've already included in a rectangle
        boolean[][] visited = new boolean[mapWidth][mapHeight];
        Array<Rectangle> rectangles = new Array<>();

        // 1. Decompose the walkable area into the largest possible rectangles
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                // If the tile is walkable and we haven't processed it yet...
                if (collisionLayer.getCell(x, y) == null && !visited[x][y]) {
                    // ...it becomes the seed for a new rectangle.
                    rectangles.add(findLargestRectangle(collisionLayer, visited, x, y));
                }
            }
        }
        System.out.println("Decomposed walkable area into " + rectangles.size + " rectangles.");

        // 2. Triangulate each rectangle
        Array<Triangle> navMesh = new Array<>();
        for (Rectangle rect : rectangles) {
            // Get the world coordinates of the rectangle's corners
            float worldX = rect.x * tileWidth;
            float worldY = rect.y * tileHeight;
            float worldW = rect.width * tileWidth;
            float worldH = rect.height * tileHeight;

            Vector2 p1 = new Vector2(worldX, worldY);                 // Bottom-Left
            Vector2 p2 = new Vector2(worldX + worldW, worldY);        // Bottom-Right
            Vector2 p3 = new Vector2(worldX + worldW, worldY + worldH); // Top-Right
            Vector2 p4 = new Vector2(worldX, worldY + worldH);        // Top-Left

            // Split the rectangle into two triangles.
            // There are two ways to split it, both are valid.
            navMesh.add(new Triangle(p1, p2, p4));
            navMesh.add(new Triangle(p2, p3, p4));
        }

        System.out.println("Generated navmesh with " + navMesh.size + " triangles.");
        return navMesh;
    }

    /**
     * Finds the largest possible rectangle of walkable tiles starting from (startX, startY).
     */
    private static Rectangle findLargestRectangle(TiledMapTileLayer layer, boolean[][] visited, int startX, int startY) {
        int rectWidth = 0;
        int rectHeight = 0;

        // Step 1: Greedily expand to the right to find the max possible width
        for (int x = startX; x < layer.getWidth(); x++) {
            if (layer.getCell(x, startY) == null && !visited[x][startY]) {
                rectWidth++;
            } else {
                break; // Hit a wall or an already-visited tile
            }
        }

        // Step 2: Greedily expand downwards, checking the full width at each step
        for (int y = startY; y < layer.getHeight(); y++) {
            boolean rowIsClear = true;
            for (int x = startX; x < startX + rectWidth; x++) {
                if (layer.getCell(x, y) == null && !visited[x][y]) {
                    // This tile is clear
                } else {
                    rowIsClear = false; // This row is blocked
                    break;
                }
            }

            if (rowIsClear) {
                rectHeight++;
            } else {
                break;
            }
        }

        // Step 3: Mark all tiles within the found rectangle as visited
        for (int y = startY; y < startY + rectHeight; y++) {
            for (int x = startX; x < startX + rectWidth; x++) {
                visited[x][y] = true;
            }
        }

        return new Rectangle(startX, startY, rectWidth, rectHeight);
    }
}
