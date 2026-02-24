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

public class EntityFactory {

    private static TextureRegion buildingTextureRegion;

    public static void initialize() {
        Texture buildingTexture = new Texture("assets/structure.png");
        buildingTextureRegion = new TextureRegion(buildingTexture);
    }

    public static TextureRegion getBuildingTexture() {
        return buildingTextureRegion;
    }

    public static Entity createBuilding(Engine engine, float worldX, float worldY, float width, float height) {
        Entity building = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(worldX, worldY);
        transform.width = width;
        transform.height = height;
        building.add(transform);

        SpriteComponent sprite = engine.createComponent(SpriteComponent.class);
        sprite.region = buildingTextureRegion;
        sprite.zIndex = 1.0f;
        building.add(sprite);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        building.add(collision);

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
