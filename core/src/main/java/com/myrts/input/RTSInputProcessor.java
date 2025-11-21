package com.myrts.input;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.myrts.entities.EntityFactory;
import com.myrts.map.MapManager;

public class RTSInputProcessor extends InputAdapter {
    private OrthographicCamera camera;
    private MapManager mapManager;
    private Engine engine;
    private Vector3 touchPos = new Vector3();
    private boolean panning = false;
    private float startX, startY;
    private float lastX, lastY;
    private Vector3 mousePos = new Vector3();

    public RTSInputProcessor(OrthographicCamera camera, MapManager mapManager, Engine engine) {
        this.camera = camera;
        this.mapManager = mapManager;
        this.engine = engine;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        startX = screenX;
        startY = screenY;
        lastX = screenX;
        lastY = screenY;

        // Right mouse button for panning
        if (button == 1) {
            panning = true;
            return true;
        }

        // Left mouse button for selection
        if (button == 0) {
            // Convert screen coordinates to world coordinates
            touchPos.set(screenX, screenY, 0);
            camera.unproject(touchPos);

            // Check if we clicked on a tile
            Vector2 tilePos = mapManager.worldToTile(touchPos.x, touchPos.y);
            System.out.println("Clicked on tile: " + tilePos.x + ", " + tilePos.y);

            // Check if this tile is blocked
            boolean isBlocked = mapManager.isCollision((int)tilePos.x, (int)tilePos.y);
            System.out.println("Tile is " + (isBlocked ? "blocked" : "passable"));

            return true;
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) {
            panning = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (panning) {
            float deltaX = (screenX - lastX);
            float deltaY = (lastY - screenY);

            // Convert screen delta to world delta based on zoom
            deltaX *= camera.zoom;
            deltaY *= camera.zoom;

            // Move camera
            camera.position.x -= deltaX;
            camera.position.y -= deltaY;

            // Ensure camera position is aligned to pixels
            camera.position.x = Math.round(camera.position.x);
            camera.position.y = Math.round(camera.position.y);

            // Rest of your camera bounding code...

            lastX = screenX;
            lastY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        // Zoom with scroll wheel
        camera.position.x = Math.round(camera.position.x);
        camera.position.y = Math.round(camera.position.y);
        camera.zoom += amountY *0.1f;
        System.out.println("1: " + camera.zoom);
        // Limit zoom
        camera.zoom = Math.max(0.5f, Math.min(camera.zoom, 5f));

        System.out.println("2: " +camera.zoom);
        camera.update();
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.B) {
            // Get the current mouse position in world coordinates
            mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mousePos);

            // Convert world coordinates to tile coordinates
            Vector2 tilePos = mapManager.worldToTile(mousePos.x, mousePos.y);
            int tileX = (int) tilePos.x;
            int tileY = (int) tilePos.y;

            // --- Placement Logic ---
            // Check if the tile is currently walkable (not blocked)
            if (!mapManager.isCollision(tileX, tileY)) {
                System.out.println("Placing building at tile: " + tileX + ", " + tileY);

                // 1. Create the building entity
                EntityFactory.createBuilding(engine, tilePos, mapManager.getTileWidth(), mapManager.getTileHeight());

                // 2. Mark the tile as blocked in the collision map
                mapManager.setTileBlocked(tileX, tileY, true);

                // 3. (Future) Trigger the local navmesh update here!

                return true; // Input was handled
            } else {
                System.out.println("Cannot build at tile: " + tileX + ", " + tileY + ". It's blocked.");
                return false;
            }
        }
        return false;
    }

    public void update(float delta) {
        // Could add keyboard controls for camera movement here
    }
}
