package com.myrts.entities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.myrts.components.*;
import com.myrts.blueprints.BuildingType;

import java.util.HashMap;
import java.util.Map;

public class EntityFactory {

    private static Map<BuildingType, TextureRegion> buildingTextures = new HashMap<>();

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

    public static TextureRegion getBuildingTexture(BuildingType type) {
        if (type == null) return null;
        return buildingTextures.get(type);
    }

    public static void dispose() {
        for (TextureRegion region : buildingTextures.values()) {
            region.getTexture().dispose();
        }
    }
}
