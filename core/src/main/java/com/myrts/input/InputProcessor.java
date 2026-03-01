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

public class InputProcessor extends InputAdapter {
    private OrthographicCamera camera;
    private MapManager mapManager;
    private Engine engine;

    // Camera control variables
    private boolean panning = false;
    private float lastX, lastY;

    // Placement Mode variables
    private boolean placingMode = false;
    private boolean canBuild = false;
    private Vector3 mouseWorldPos = new Vector3();
    private Vector2 ghostPos = new Vector2(); // The bottom-left world position of the ghost

    // Hardcoded building size for now (3x3)
    private final int BUILDING_SIZE_TILES = 3;

    public InputProcessor(OrthographicCamera camera, MapManager mapManager, Engine engine) {
        this.camera = camera;
        this.mapManager = mapManager;
        this.engine = engine;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.B) {
            placingMode = !placingMode; // Toggle mode
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == 1) { // Right click
            if (placingMode) {
                placingMode = false; // Right click cancels placement
            } else {
                panning = true;
                lastX = screenX;
                lastY = screenY;
            }
            return true;
        }

        if (button == 0) { // Left click
            if (placingMode) {
                if (canBuild) {
                    placeBuilding();
                    placingMode = false; // Exit mode after placement (optional)
                }
                return true; // Consume input
            }

            // Normal selection logic here (omitted for brevity)
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
            float deltaX = (screenX - lastX) * camera.zoom;
            float deltaY = (lastY - screenY) * camera.zoom;

            camera.position.x -= deltaX;
            camera.position.y -= deltaY;
            camera.update();

            lastX = screenX;
            lastY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        camera.zoom += amountY * 0.1f;
        camera.zoom = Math.max(0.5f, Math.min(camera.zoom, 5f));
        camera.update();
        return true;
    }

    // Called every frame by GameScreen
    public void update(float delta) {
        if (placingMode) {
            updatePlacementLogic();
        }
    }

    private void updatePlacementLogic() {
        // 1. Get Mouse World Position
        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        float tileW = mapManager.getTileWidth();
        float tileH = mapManager.getTileHeight();
        float buildingWorldWidth = BUILDING_SIZE_TILES * tileW;
        float buildingWorldHeight = BUILDING_SIZE_TILES * tileH;

        // 2. Calculate Center-Aligned Position
        float rawX = mouseWorldPos.x - (buildingWorldWidth / 2f);
        float rawY = mouseWorldPos.y - (buildingWorldHeight / 2f);

        // 3. Snap to Grid
        int tileX = Math.round(rawX / tileW);
        int tileY = Math.round(rawY / tileH);

        // Calculate snapped world coordinates for rendering
        ghostPos.set(tileX * tileW, tileY * tileH);

        // 4. Check Validity using MapManager
        canBuild = mapManager.canPlaceBuilding(tileX, tileY, BUILDING_SIZE_TILES, BUILDING_SIZE_TILES);
    }

    private void placeBuilding() {
        float tileW = mapManager.getTileWidth();
        float tileH = mapManager.getTileHeight();
        float buildingWorldWidth = BUILDING_SIZE_TILES * tileW;
        float buildingWorldHeight = BUILDING_SIZE_TILES * tileH;

        System.out.println("Placing building at world: " + ghostPos.x + ", " + ghostPos.y);

        // 1. Create Entity in the Ashley ECS
        EntityFactory.createBuilding(engine, ghostPos.x, ghostPos.y, buildingWorldWidth, buildingWorldHeight);

        // 2. Delegate Map changes to MapManager
        mapManager.registerBuildingObstacle(ghostPos.x, ghostPos.y, buildingWorldWidth, buildingWorldHeight, BUILDING_SIZE_TILES, BUILDING_SIZE_TILES);
    }

    // Getters for GameScreen rendering
    public boolean isPlacingMode() { return placingMode; }
    public boolean isCanBuild() { return canBuild; }
    public Vector2 getGhostPos() { return ghostPos; }
    public float getGhostWidth() { return BUILDING_SIZE_TILES * mapManager.getTileWidth(); }
    public float getGhostHeight() { return BUILDING_SIZE_TILES * mapManager.getTileHeight(); }
}
