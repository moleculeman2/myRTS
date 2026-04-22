package com.myrts.entities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.myrts.blueprints.UnitType;
import com.myrts.components.*;
import com.myrts.blueprints.BuildingType;


import java.util.HashMap;
import java.util.Map;

public class EntityFactory {

    private static Map<BuildingType, TextureRegion> buildingTextures = new HashMap<>();
    private static Map<UnitType, TextureRegion> unitTextures = new HashMap<>();

    public static void initialize() {
        // Load textures for all building types
        for (BuildingType type : BuildingType.values()) {
            try {
                Texture texture = new Texture(type.texturePath);
                buildingTextures.put(type, new TextureRegion(texture));
            } catch (Exception e) {
                // Fallback if you haven't created the image files yet!
                System.out.println("Warning: Could not load texture for " + type.name());
                Texture fallback = new Texture("assets/structure.png");
                buildingTextures.put(type, new TextureRegion(fallback));
            }
        }

        for (UnitType type : UnitType.values()) {
            try {
                Texture texture = new Texture(type.texturePath);
                unitTextures.put(type, new TextureRegion(texture));
            } catch (Exception e) {
                System.out.println("Warning: Could not load texture for unit " + type.name());
                // Fallback texture
                Texture fallback = new Texture("assets/badlogic.jpg");
                unitTextures.put(type, new TextureRegion(fallback));
            }
        }
    }

    public static Entity createBuilding(Engine engine, float worldX, float worldY, float width, float height, BuildingType type) {
        Entity building = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(worldX, worldY);
        transform.width = width;
        transform.height = height;
        building.add(transform);

        SpriteComponent sprite = engine.createComponent(SpriteComponent.class);
        sprite.region = buildingTextures.get(type);
        sprite.zIndex = 1.0f;
        building.add(sprite);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        building.add(collision);

        BuildingComponent buildingComp = engine.createComponent(BuildingComponent.class);
        buildingComp.init(type); // Set the health and type here!
        building.add(buildingComp);

        SpatialComponent spatial = engine.createComponent(SpatialComponent.class);
        // It defaults to -1, -1. The SpatialUpdateSystem will calculate its actual
        // starting cell and register it to the MapManager's grid on the first frame!
        building.add(spatial);

        engine.addEntity(building);
        return building;
    }

    public static Entity createGhostBuilding(Engine engine, BuildingType type) {
        Entity ghost = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        // Position/Size will be updated every frame by the InputProcessor
        ghost.add(transform);

        SpriteComponent sprite = engine.createComponent(SpriteComponent.class);
        sprite.region = getBuildingTexture(type);
        sprite.zIndex = 2.0f; // Draw above normal buildings
        sprite.color.a = 0.5f; // 50% transparent!
        ghost.add(sprite);

        GhostComponent ghostComp = engine.createComponent(GhostComponent.class);
        ghostComp.blueprint = type;
        ghost.add(ghostComp);

        engine.addEntity(ghost);
        return ghost;
    }

    public static Entity createUnit(Engine engine, float worldX, float worldY, UnitType type) {
        Entity unit = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(worldX, worldY);
        transform.width = type.width;   // Use Enum width
        transform.height = type.height; // Use Enum height
        unit.add(transform);

        SpriteComponent sprite = engine.createComponent(SpriteComponent.class);
        sprite.region = unitTextures.get(type); // Fetch from the new map
        sprite.zIndex = 1.5f;
        unit.add(sprite);

        UnitComponent unitComp = engine.createComponent(UnitComponent.class);
        unitComp.init(type);
        unit.add(unitComp);

        SelectableComponent selectable = engine.createComponent(SelectableComponent.class);
        selectable.selected = false;
        // Set selection radius based on unit size
        selectable.selectionRadius = Math.max(type.width, type.height) / 2f;
        unit.add(selectable);

        SpatialComponent spatial = engine.createComponent(SpatialComponent.class);
        // It defaults to -1, -1. The SpatialUpdateSystem will calculate its actual
        // starting cell and register it to the MapManager's grid on the first frame!
        unit.add(spatial);

        engine.addEntity(unit);
        return unit;
    }

    public static TextureRegion getBuildingTexture(BuildingType type) {
        if (type == null) return null;
        return buildingTextures.get(type);
    }

    public static void dispose() {
        for (TextureRegion region : buildingTextures.values()) {
            region.getTexture().dispose();
        }

        for (TextureRegion region : unitTextures.values()) {
            region.getTexture().dispose();
        }
    }
}
