package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.myrts.components.PathComponent;
import com.myrts.components.TransformComponent;

public class PathDebugRenderSystem extends IteratingSystem {
    private final ShapeRenderer shapeRenderer;
    private final OrthographicCamera camera;

    public PathDebugRenderSystem(ShapeRenderer shapeRenderer, OrthographicCamera camera) {
        // Only process units that actually have a path to walk
        super(Family.all(PathComponent.class, TransformComponent.class).get());
        this.shapeRenderer = shapeRenderer;
        this.camera = camera;
    }

    @Override
    public void update(float deltaTime) {
        // We set up the ShapeRenderer ONCE per frame before looping through the units.
        // This is crucial for keeping your FPS high!
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        super.update(deltaTime); // This automatically calls processEntity for each unit

        shapeRenderer.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PathComponent path = entity.getComponent(PathComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        // Safety check
        if (path.waypoints.isEmpty() || path.currentWaypointIndex >= path.waypoints.size) {
            return;
        }

        // Set the path line color (Bright Green is classic RTS debug color)
        shapeRenderer.setColor(Color.GREEN);

        // 1. Draw a line from the unit's exact center to its CURRENT target waypoint
        float unitCenterX = transform.position.x + (transform.width / 2f);
        float unitCenterY = transform.position.y + (transform.height / 2f);
        Vector2 currentTarget = path.waypoints.get(path.currentWaypointIndex);

        shapeRenderer.line(unitCenterX, unitCenterY, currentTarget.x, currentTarget.y);

        // 2. Draw lines connecting the rest of the remaining waypoints
        for (int i = path.currentWaypointIndex; i < path.waypoints.size - 1; i++) {
            Vector2 p1 = path.waypoints.get(i);
            Vector2 p2 = path.waypoints.get(i + 1);
            shapeRenderer.line(p1.x, p1.y, p2.x, p2.y);
        }

        // 3. Optional Polish: Draw a tiny circle at the final destination
        Vector2 finalDest = path.waypoints.peek();
        shapeRenderer.circle(finalDest.x, finalDest.y, 3f);
    }
}
