package com.myrts.input;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.myrts.blueprints.BuildingType;
import com.myrts.components.*;
import com.myrts.entities.EntityFactory;
import com.myrts.map.MapManager;
import com.myrts.map.SpatialPartitionGrid;

public class InputProcessor extends InputAdapter {
    private OrthographicCamera camera;
    private MapManager mapManager;
    private Engine engine;

    // Camera control variables
    private boolean panning = false;
    private float lastX, lastY;

    // Multi-Selection variables
    private boolean multiSelecting = false;
    private Vector2 selectionStart = new Vector2();
    private Vector2 selectionEnd = new Vector2();

    // Placement Mode variables
    private boolean placingMode = false;
    private boolean canBuild = false;
    private Entity ghostEntity = null;
    private Vector3 mouseWorldPos = new Vector3();
    private Vector2 ghostPos = new Vector2();
    private long lastCommandTime = 0;
    private Vector2 lastCommandPos = new Vector2();

    private BuildingType currentBlueprint = null;

    public InputProcessor(OrthographicCamera camera, MapManager mapManager, Engine engine) {
        this.camera = camera;
        this.mapManager = mapManager;
        this.engine = engine;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.B) {
            if (placingMode) cancelPlacement();
            beginPlacingBuilding(BuildingType.BARRACKS);
            return true;
        }
        if (keycode == Input.Keys.G) {
            if (placingMode) cancelPlacement();
            beginPlacingBuilding(BuildingType.GENERATOR);
            return true;
        }
        if (keycode == Input.Keys.H) {
            if (placingMode) cancelPlacement();
            beginPlacingBuilding(BuildingType.HEADQUARTERS);
            return true;
        }
        if (keycode == Input.Keys.T) {
            if (placingMode) cancelPlacement();
            beginPlacingBuilding(BuildingType.TURRET);
            return true;
        }
        if (keycode == Input.Keys.D) {
            attemptBuildingDeletion();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == 1) { // Right click
            if (placingMode) {
                cancelPlacement();
            } else {
                mouseWorldPos.set(screenX, screenY, 0);
                camera.unproject(mouseWorldPos);

                long currentTime = com.badlogic.gdx.utils.TimeUtils.millis();
                Vector2 currentClickPos = new Vector2(mouseWorldPos.x, mouseWorldPos.y);

                if (currentTime - lastCommandTime < 3000 && lastCommandPos.dst(currentClickPos) < 1.5f) {
                    return true;
                }

                lastCommandTime = currentTime;
                lastCommandPos.set(currentClickPos);

                Family family = Family.all(SelectableComponent.class, TransformComponent.class).get();
                boolean unitIsSelected = false;

                for (Entity entity : engine.getEntitiesFor(family)) {
                    if (entity.getComponent(SelectableComponent.class).selected) {
                        unitIsSelected = true;
                        TargetDestinationComponent destComp = engine.createComponent(TargetDestinationComponent.class);
                        destComp.target.set(currentClickPos);
                        entity.add(destComp);
                    }
                }

                if (!unitIsSelected) {
                    panning = true;
                    lastX = screenX;
                    lastY = screenY;
                }
            }
            return true;
        }

        if (button == 0) { // Left click
            if (placingMode) {
                if (canBuild) {
                    placeBuilding();
                }
                return true;
            }

            // Start multi-selection drag
            mouseWorldPos.set(screenX, screenY, 0);
            camera.unproject(mouseWorldPos);
            selectionStart.set(mouseWorldPos.x, mouseWorldPos.y);
            selectionEnd.set(mouseWorldPos.x, mouseWorldPos.y);
            multiSelecting = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) { // Right Click
            panning = false;
            return true;
        }
        if (button == 0) { // Left Click
            if (multiSelecting) {
                executeSelection();
                multiSelecting = false;
                return true;
            }
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

        if (multiSelecting) {
            mouseWorldPos.set(screenX, screenY, 0);
            camera.unproject(mouseWorldPos);
            selectionEnd.set(mouseWorldPos.x, mouseWorldPos.y);
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

    public void update(float delta) {
        if (placingMode) {
            updatePlacementLogic();
        }
    }

    private void updatePlacementLogic() {
        if (currentBlueprint == null || ghostEntity == null) return;

        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        float tileW = mapManager.getTileWidth();
        float tileH = mapManager.getTileHeight();
        float buildingWorldWidth = currentBlueprint.widthTiles * tileW;
        float buildingWorldHeight = currentBlueprint.heightTiles * tileH;

        float rawX = mouseWorldPos.x - (buildingWorldWidth / 2f);
        float rawY = mouseWorldPos.y - (buildingWorldHeight / 2f);

        int tileX = Math.round(rawX / tileW);
        int tileY = Math.round(rawY / tileH);

        float snappedX = tileX * tileW;
        float snappedY = tileY * tileH;

        canBuild = mapManager.canPlaceBuilding(tileX, tileY, currentBlueprint.widthTiles, currentBlueprint.heightTiles);

        TransformComponent transform = ghostEntity.getComponent(TransformComponent.class);
        transform.position.set(snappedX, snappedY);
        transform.width = buildingWorldWidth;
        transform.height = buildingWorldHeight;

        SpriteComponent sprite = ghostEntity.getComponent(SpriteComponent.class);
        if (canBuild) {
            sprite.color.set(0f, 1f, 0f, 0.5f);
        } else {
            sprite.color.set(1f, 0f, 0f, 0.5f);
        }
    }

    private void placeBuilding() {
        if (!canBuild || currentBlueprint == null || ghostEntity == null) return;

        TransformComponent transform = ghostEntity.getComponent(TransformComponent.class);

        Boolean buildingSuccess = mapManager.registerBuildingObstacle(transform.position.x, transform.position.y, transform.width, transform.height, currentBlueprint.widthTiles, currentBlueprint.heightTiles);
        if (buildingSuccess){
            EntityFactory.createBuilding(engine, transform.position.x, transform.position.y, transform.width, transform.height, currentBlueprint);
            cancelPlacement();
        }
    }

    private void attemptBuildingDeletion() {
        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        Family family = Family.all(BuildingComponent.class, TransformComponent.class).get();
        ImmutableArray<Entity> buildings = engine.getEntitiesFor(family);

        for (Entity entity : buildings) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);

            if (mouseWorldPos.x >= transform.position.x &&
                mouseWorldPos.x <= transform.position.x + transform.width &&
                mouseWorldPos.y >= transform.position.y &&
                mouseWorldPos.y <= transform.position.y + transform.height) {

                entity.add(new DestroyedComponent());
                System.out.println("Tagged building for deletion.");
                break;
            }
        }
    }

    public void beginPlacingBuilding(BuildingType type) {
        if (placingMode && ghostEntity != null) {
            engine.removeEntity(ghostEntity);
        }

        this.currentBlueprint = type;
        this.placingMode = true;
        this.ghostEntity = EntityFactory.createGhostBuilding(engine, type);
    }

    private void cancelPlacement() {
        if (ghostEntity != null) {
            engine.removeEntity(ghostEntity);
            ghostEntity = null;
        }
        placingMode = false;
        currentBlueprint = null;
    }

    private void executeSelection() {
        // Calculate the bounding box of the drag
        float minX = Math.min(selectionStart.x, selectionEnd.x);
        float maxX = Math.max(selectionStart.x, selectionEnd.x);
        float minY = Math.min(selectionStart.y, selectionEnd.y);
        float maxY = Math.max(selectionStart.y, selectionEnd.y);

        // Account for small mouse jitters (treat as a single click)
        boolean isSingleClick = (maxX - minX < 5f) && (maxY - minY < 5f);

        // --- 1. CLEAR ALL CURRENT SELECTIONS ---
        Family family = Family.all(SelectableComponent.class, TransformComponent.class, SpriteComponent.class).get();
        ImmutableArray<Entity> selectables = engine.getEntitiesFor(family);

        for (Entity entity : selectables) {
            SelectableComponent selectable = entity.getComponent(SelectableComponent.class);
            SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
            selectable.selected = false;
            sprite.color.set(1f, 1f, 1f, 1f); // Reset color
        }

        // --- 2. FIND GRID CELLS (WITH A 1-CELL BUFFER FOR NEIGHBORS) ---
        SpatialPartitionGrid grid = mapManager.spatialGrid;

        // By subtracting/adding 1, we grab the cells we overlap PLUS the immediate neighbors
        int startCellX = grid.getCellX(minX) - 1;
        int startCellY = grid.getCellY(minY) - 1;
        int endCellX = grid.getCellX(maxX) + 1;
        int endCellY = grid.getCellY(maxY) + 1;

        // --- 3. QUERY THE EXPANDED AREA ---
        for (int cx = startCellX; cx <= endCellX; cx++) {
            for (int cy = startCellY; cy <= endCellY; cy++) {

                // getCellAt handles out-of-bounds checks for us and returns null if invalid
                SpatialPartitionGrid.SpatialCell cell = grid.getCellAt(cx * grid.getCellWidth(), cy * grid.getCellHeight());

                if (cell != null) {
                    for (Entity entity : cell.entities) {

                        SelectableComponent selectable = entity.getComponent(SelectableComponent.class);
                        if (selectable == null) continue;

                        TransformComponent transform = entity.getComponent(TransformComponent.class);
                        SpriteComponent sprite = entity.getComponent(SpriteComponent.class);

                        float entityMinX = transform.position.x;
                        float entityMaxX = transform.position.x + transform.width;
                        float entityMinY = transform.position.y;
                        float entityMaxY = transform.position.y + transform.height;

                        boolean inBounds = false;

                        if (isSingleClick) {
                            // Point Collision (Did we click inside the unit?)
                            if (selectionStart.x >= entityMinX && selectionStart.x <= entityMaxX &&
                                selectionStart.y >= entityMinY && selectionStart.y <= entityMaxY) {
                                inBounds = true;
                            }
                        } else {
                            // AABB Overlap (Did the box touch the unit's bounds?)
                            if (minX < entityMaxX && maxX > entityMinX &&
                                minY < entityMaxY && maxY > entityMinY) {
                                inBounds = true;
                            }
                        }

                        if (inBounds) {
                            selectable.selected = true;
                            sprite.color.set(0.5f, 0.5f, 1f, 1f); // Tint blue
                        }
                    }
                }
            }
        }
    }    // Getters
    public boolean isPlacingMode() { return placingMode; }
    public boolean isCanBuild() { return canBuild; }
    public Vector2 getGhostPos() { return ghostPos; }
    public BuildingType getCurrentBlueprint() { return currentBlueprint; }

    public boolean isMultiSelecting() { return multiSelecting; }
    public Vector2 getSelectionStart() { return selectionStart; }
    public Vector2 getSelectionEnd() { return selectionEnd; }
}
