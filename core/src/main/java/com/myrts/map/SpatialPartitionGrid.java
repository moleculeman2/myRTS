// com.myrts.map.SpatialPartitionGrid.java
package com.myrts.map;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class SpatialPartitionGrid {

    // The dimensions of our grid cells in pixels
    private final float cellWidth;
    private final float cellHeight;

    private final int cols;
    private final int rows;

    private final SpatialCell[][] grid;

    public class SpatialCell {
        public Array<Entity> entities = new Array<>(false, 16);
        public Array<DelaunayTriangle> triangles = new Array<>(false, 16);
    }

    public SpatialPartitionGrid(int mapTileWidth, int mapTileHeight, float tilePixelWidth, float tilePixelHeight) {
        // 10x10 tiles per cell
        this.cellWidth = tilePixelWidth * 10f;
        this.cellHeight = tilePixelHeight * 10f;

        // Calculate how many cells we need based on map size
        this.cols = (int) Math.ceil((double) mapTileWidth / 10.0);
        this.rows = (int) Math.ceil((double) mapTileHeight / 10.0);

        this.grid = new SpatialCell[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                grid[x][y] = new SpatialCell();
            }
        }
    }

    // --- ENTITY MANAGEMENT (Dynamic) ---

    public void addEntity(Entity entity, float worldX, float worldY) {
        int cx = getCellX(worldX);
        int cy = getCellY(worldY);
        if (isValidCell(cx, cy)) {
            grid[cx][cy].entities.add(entity);
        }
    }

    public void removeEntity(Entity entity, int cellX, int cellY) {
        if (isValidCell(cellX, cellY)) {
            grid[cellX][cellY].entities.removeValue(entity, true);
        }
    }

    // --- TRIANGLE MANAGEMENT (Static) ---

    /**
     * Registers a triangle to ALL grid cells it overlaps.
     */
    public void addTriangle(DelaunayTriangle tri) {
        // 1. Find the Bounding Box of the Triangle
        float minX = Math.min(tri.points[0].getXf(), Math.min(tri.points[1].getXf(), tri.points[2].getXf()));
        float minY = Math.min(tri.points[0].getYf(), Math.min(tri.points[1].getYf(), tri.points[2].getYf()));
        float maxX = Math.max(tri.points[0].getXf(), Math.max(tri.points[1].getXf(), tri.points[2].getXf()));
        float maxY = Math.max(tri.points[0].getYf(), Math.max(tri.points[1].getYf(), tri.points[2].getYf()));

        // 2. Convert World bounds to Grid Cell indices
        int startX = Math.max(0, getCellX(minX));
        int startY = Math.max(0, getCellY(minY));
        int endX = Math.min(cols - 1, getCellX(maxX));
        int endY = Math.min(rows - 1, getCellY(maxY));

        // 3. Add the triangle to all overlapped cells
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                if (!grid[x][y].triangles.contains(tri, true)) {
                    grid[x][y].triangles.add(tri);
                }
            }
        }
    }

    public void clearAllTriangles() {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                grid[x][y].triangles.clear();
            }
        }
    }

    // --- QUERYING ---

    public SpatialCell getCellAt(float worldX, float worldY) {
        int cx = getCellX(worldX);
        int cy = getCellY(worldY);
        if (isValidCell(cx, cy)) return grid[cx][cy];
        return null;
    }

    public int getCellX(float worldX) { return (int) (worldX / cellWidth); }
    public int getCellY(float worldY) { return (int) (worldY / cellHeight); }
    public float getCellWidth() { return cellWidth; }
    public float getCellHeight() { return cellHeight; }

    private boolean isValidCell(int cx, int cy) {
        return cx >= 0 && cx < cols && cy >= 0 && cy < rows;
    }
}
