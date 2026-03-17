package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.myrts.components.PathComponent;
import com.myrts.components.TransformComponent;
import com.myrts.components.UnitComponent;

public class MovementSystem extends IteratingSystem {
    private Vector2 tempDirection = new Vector2();

    public MovementSystem() {
        // Only process entities that have ALL THREE of these components
        super(Family.all(TransformComponent.class, UnitComponent.class, PathComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        UnitComponent unit = entity.getComponent(UnitComponent.class);
        PathComponent path = entity.getComponent(PathComponent.class);

        // Have we reached the end of the path?
        if (path.currentWaypointIndex >= path.waypoints.size) {
            entity.remove(PathComponent.class); // Destination reached, remove the GPS!
            return;
        }

        Vector2 target = path.waypoints.get(path.currentWaypointIndex);

        // Calculate the center point of our unit
        float centerX = transform.position.x + (transform.width / 2f);
        float centerY = transform.position.y + (transform.height / 2f);

        // Calculate the distance and direction to the next waypoint
        tempDirection.set(target.x - centerX, target.y - centerY);
        float distanceToTarget = tempDirection.len();

        // If we are within a tiny radius of the waypoint, snap to the next one
        if (distanceToTarget < 2.0f) {
            path.currentWaypointIndex++;
            return;
        }

        // Normalize the vector (turns it into a pure direction with length 1)
        tempDirection.nor();

        // Assuming moveSpeed is in "tiles per second", multiply by your tile size (e.g. 32 pixels)
        // to get pixel speed. If your moveSpeed is already pixels, remove the * 32f.
        float pixelSpeed = unit.moveSpeed * 32f;

        // Apply velocity based on the frame rate (deltaTime)
        transform.position.x += tempDirection.x * pixelSpeed * deltaTime;
        transform.position.y += tempDirection.y * pixelSpeed * deltaTime;
    }
}
