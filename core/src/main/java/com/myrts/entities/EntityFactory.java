package com.myrts.entities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.myrts.components.BuildingComponent;
import com.myrts.components.CollisionComponent;
import com.myrts.components.SpriteComponent;
import com.myrts.components.TransformComponent;

/**
 * A factory class to create common game entities.
 */
public class EntityFactory {

    // We'll use a simple white square as a placeholder texture for now.
    private static TextureRegion buildingTextureRegion;

    public static void initialize() {
        // In a real game, you would load this from an AssetManager.
        // For now, we create a simple 32x32 texture programmatically.
        Texture buildingTexture = new Texture("assets/structure.png"); // Create a simple 32x32 png
        buildingTextureRegion = new TextureRegion(buildingTexture);
    }

    public static Entity createBuilding(Engine engine, Vector2 tilePosition, float tileWidth, float tileHeight) {
        Entity building = engine.createEntity();

        // --- Transform Component ---
        // Determines the building's position, rotation, and scale.
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(tilePosition.x * tileWidth, tilePosition.y * tileHeight);
        transform.width = tileWidth;   // Set the width
        transform.height = tileHeight; // Set the height
        building.add(transform);

        // --- Sprite Component ---
        // Handles rendering the building's visual.
        SpriteComponent sprite = engine.createComponent(SpriteComponent.class);
        sprite.region = buildingTextureRegion;
        sprite.zIndex = 1.0f;
        building.add(sprite);

        // --- Collision Component ---
        // Marks this entity as something that blocks pathfinding.
        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        building.add(collision);

        // --- Building Component ---
        // Identifies this entity as a building.
        BuildingComponent buildingComp = engine.createComponent(BuildingComponent.class);
        building.add(buildingComp);

        engine.addEntity(building);
        return building;
    }

    public static void dispose() {
        if (buildingTextureRegion != null) {
            buildingTextureRegion.getTexture().dispose();
        }
    }
}
