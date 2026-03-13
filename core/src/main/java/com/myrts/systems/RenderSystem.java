package com.myrts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.myrts.components.SpriteComponent;
import com.myrts.components.TransformComponent;

import java.util.Comparator;

public class RenderSystem extends SortedIteratingSystem {
    private SpriteBatch batch;

    private ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private ComponentMapper<SpriteComponent> spriteMapper = ComponentMapper.getFor(SpriteComponent.class);

    public RenderSystem(SpriteBatch batch) {
        super(Family.all(TransformComponent.class, SpriteComponent.class).get(),
                new ZComparator());
        this.batch = batch;
    }

    @Override
    public void update(float deltaTime) {
        batch.begin();
        super.update(deltaTime);
        batch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        SpriteComponent sprite = spriteMapper.get(entity);

        // If width/height are not set in the component, default to the image size.
        float width = transform.width > 0 ? transform.width : sprite.region.getRegionWidth();
        float height = transform.height > 0 ? transform.height : sprite.region.getRegionHeight();

        batch.setColor(sprite.color);
        batch.draw(
            sprite.region,
            transform.position.x,
            transform.position.y,
            width,
            height
        );
        batch.setColor(Color.WHITE);
    }

    private static class ZComparator implements Comparator<Entity> {
        private ComponentMapper<SpriteComponent> spriteMapper = ComponentMapper.getFor(SpriteComponent.class);

        @Override
        public int compare(Entity entityA, Entity entityB) {
            float zA = spriteMapper.get(entityA).zIndex;
            float zB = spriteMapper.get(entityB).zIndex;
            return Float.compare(zA, zB);
        }
    }
}
