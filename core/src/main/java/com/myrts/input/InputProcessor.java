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
import com.badlogic.gdx.utils.Array;
import com.myrts.blueprints.BuildingType;
import com.myrts.components.*;
import com.myrts.entities.EntityFactory;
import com.myrts.map.FunnelSmoother;
import com.myrts.map.MapManager;
import com.myrts.map.TrianglePathfinder;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

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
    private Entity ghostEntity = null;
    private Vector3 mouseWorldPos = new Vector3();
    private Vector2 ghostPos = new Vector2(); // The bottom-left world position of the ghost

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

                Family family = Family.all(SelectableComponent.class, TransformComponent.class).get();
                boolean unitIsSelected = false;

                for (Entity entity : engine.getEntitiesFor(family)) {
                    if (entity.getComponent(SelectableComponent.class).selected) {
                        unitIsSelected = true;

                        // Give the unit its marching orders!
                        TargetDestinationComponent destComp = engine.createComponent(TargetDestinationComponent.class);
                        destComp.target.set(mouseWorldPos.x, mouseWorldPos.y);
                        entity.add(destComp);
                    }
                }

                // If no units are selected, behave normally (pan camera)
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
                return true; // Consume input
            }
            handleSelection();
            return true;
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
        if (currentBlueprint == null || ghostEntity == null) return;

        // 1. Get Mouse World Position
        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        float tileW = mapManager.getTileWidth();
        float tileH = mapManager.getTileHeight();
        float buildingWorldWidth = currentBlueprint.widthTiles * tileW;
        float buildingWorldHeight = currentBlueprint.heightTiles * tileH;

        // 2. Calculate Center-Aligned Position
        float rawX = mouseWorldPos.x - (buildingWorldWidth / 2f);
        float rawY = mouseWorldPos.y - (buildingWorldHeight / 2f);

        // 3. Snap to Grid
        int tileX = Math.round(rawX / tileW);
        int tileY = Math.round(rawY / tileH);

        float snappedX = tileX * tileW;
        float snappedY = tileY * tileH;

        // 4. Check Validity using MapManager
        canBuild = mapManager.canPlaceBuilding(tileX, tileY, currentBlueprint.widthTiles, currentBlueprint.heightTiles);

        // --- NEW: Update the Ghost Entity Components directly! ---

        // Update its position and size
        TransformComponent transform = ghostEntity.getComponent(TransformComponent.class);
        transform.position.set(snappedX, snappedY);
        transform.width = buildingWorldWidth;
        transform.height = buildingWorldHeight;

        // Update its color based on if it can be built
        SpriteComponent sprite = ghostEntity.getComponent(SpriteComponent.class);
        if (canBuild) {
            sprite.color.set(0f, 1f, 0f, 0.5f); // Semi-transparent white
        } else {
            sprite.color.set(1f, 0f, 0f, 0.5f); // Semi-transparent red
        }
    }

    private void placeBuilding() {
        if (!canBuild || currentBlueprint == null || ghostEntity == null) return;

        // Read the final snapped position directly from our ghost entity!
        TransformComponent transform = ghostEntity.getComponent(TransformComponent.class);

        System.out.println("Placing building at world: " + transform.position.x + ", " + transform.position.y);

        // 1. Create real Entity

        // 2. Delegate Map changes to MapManager
        Boolean buildingSuccess = mapManager.registerBuildingObstacle(transform.position.x, transform.position.y, transform.width, transform.height, currentBlueprint.widthTiles, currentBlueprint.heightTiles);
        if (buildingSuccess){
            EntityFactory.createBuilding(engine, transform.position.x, transform.position.y, transform.width, transform.height, currentBlueprint);
            cancelPlacement();
        }
        // 3. Exit placement mode and destroy the ghost

    }

    private void attemptBuildingDeletion() {
        // 1. Get current mouse world position
        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        // 2. Get all buildings from the engine
        Family family = Family.all(BuildingComponent.class, TransformComponent.class).get();
        ImmutableArray<Entity> buildings = engine.getEntitiesFor(family);

        // 3. Find if we are hovering over one
        for (Entity entity : buildings) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);

            // Simple AABB collision check point-to-box
            if (mouseWorldPos.x >= transform.position.x &&
                mouseWorldPos.x <= transform.position.x + transform.width &&
                mouseWorldPos.y >= transform.position.y &&
                mouseWorldPos.y <= transform.position.y + transform.height) {

                // We found the building under the mouse! Tag it for destruction.
                entity.add(new DestroyedComponent());
                System.out.println("Tagged building for deletion.");
                break; // Only destroy one at a time
            }
        }
    }

    public void beginPlacingBuilding(BuildingType type) {
        // If we are already placing something else, destroy the old ghost first!
        if (placingMode && ghostEntity != null) {
            engine.removeEntity(ghostEntity);
        }

        this.currentBlueprint = type;
        this.placingMode = true;

        // Ask the factory to spawn our new ECS ghost entity
        this.ghostEntity = EntityFactory.createGhostBuilding(engine, type);
    }

    private void cancelPlacement() {
        if (ghostEntity != null) {
            engine.removeEntity(ghostEntity); // Remove the ghost from the game world
            ghostEntity = null;
        }
        placingMode = false;
        currentBlueprint = null;
    }

    private void handleSelection() {
        // 1. Get Mouse World Position
        mouseWorldPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorldPos);

        // 2. Ask Ashley for all selectable entities
        Family family = Family.all(SelectableComponent.class, TransformComponent.class, SpriteComponent.class).get();
        ImmutableArray<Entity> selectables = engine.getEntitiesFor(family);

        boolean clickedOnEntity = false;

        // 3. Find if we clicked on any unit
        for (Entity entity : selectables) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            SelectableComponent selectable = entity.getComponent(SelectableComponent.class);
            SpriteComponent sprite = entity.getComponent(SpriteComponent.class);

            // Simple AABB collision check
            if (mouseWorldPos.x >= transform.position.x &&
                mouseWorldPos.x <= transform.position.x + transform.width &&
                mouseWorldPos.y >= transform.position.y &&
                mouseWorldPos.y <= transform.position.y + transform.height) {

                // We clicked the unit!
                selectable.selected = true;
                clickedOnEntity = true;

                // Tint it blue as a temporary visual indicator
                sprite.color.set(0.5f, 0.5f, 1f, 1f);
                System.out.println("Unit Selected!");

                // Optional: break here if you only want to select one unit at a time when clicking a cluster
            } else {
                // We didn't click this specific unit, deselect it
                selectable.selected = false;
                sprite.color.set(1f, 1f, 1f, 1f); // Reset tint
            }
        }

        // 4. If we clicked empty ground, deselect everything
        if (!clickedOnEntity) {
            for (Entity entity : selectables) {
                entity.getComponent(SelectableComponent.class).selected = false;
                entity.getComponent(SpriteComponent.class).color.set(1f, 1f, 1f, 1f); // Reset tint
            }
            System.out.println("Deselected all.");
        }
    }

    // Getters for GameScreen rendering
    public boolean isPlacingMode() { return placingMode; }
    public boolean isCanBuild() { return canBuild; }
    public Vector2 getGhostPos() { return ghostPos; }
    public float getGhostWidth() {
        return currentBlueprint != null ? currentBlueprint.widthTiles * mapManager.getTileWidth() : 0;
    }
    public float getGhostHeight() {
        return currentBlueprint != null ? currentBlueprint.heightTiles * mapManager.getTileHeight() : 0;
    }

    public BuildingType getCurrentBlueprint() {
        return currentBlueprint;
    }
}
