package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.myrts.components.PathComponent;
import com.myrts.components.TransformComponent;
import com.myrts.components.UnitComponent;
import com.myrts.map.MapManager;

public class MovementSystem extends IteratingSystem {
    private final Vector2 tempDirection = new Vector2();
    private final float tileSize;

    // --- UPGRADE: Pass MapManager into the constructor ---
    public MovementSystem(MapManager mapManager) {
        super(Family.all(TransformComponent.class, UnitComponent.class, PathComponent.class).get());

        // Cache the tile size dynamically when the system is created!
        this.tileSize = mapManager.getTileWidth();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        UnitComponent unit = entity.getComponent(UnitComponent.class);
        PathComponent path = entity.getComponent(PathComponent.class);

        if (path.currentWaypointIndex >= path.waypoints.size) {
            entity.remove(PathComponent.class);
            return;
        }

        Vector2 target = path.waypoints.get(path.currentWaypointIndex);

        float centerX = transform.position.x + (transform.width / 2f);
        float centerY = transform.position.y + (transform.height / 2f);

        tempDirection.set(target.x - centerX, target.y - centerY);
        float distanceToTarget = tempDirection.len();

        // Use the dynamic tile size here!
        float pixelSpeed = unit.moveSpeed * tileSize;
        float moveDistanceThisFrame = pixelSpeed * deltaTime;

        // --- UPGRADE: The Anti-Jitter Overshoot Check ---
        // If the distance to the waypoint is less than the distance we are about to move,
        // we snap exactly to the waypoint and queue up the next one.
        if (distanceToTarget <= moveDistanceThisFrame) {
            // Snap to the exact waypoint to prevent drifting
            transform.position.x = target.x - (transform.width / 2f);
            transform.position.y = target.y - (transform.height / 2f);

            path.currentWaypointIndex++;
            return;
        }

        tempDirection.nor();

        // --- UPGRADE: Update the unit's rotation to face the movement direction ---
        // (Assuming 0 degrees is pointing right. Adjust the math if your sprite points up by default)
        transform.rotation = tempDirection.angleDeg();

        // Apply velocity
        transform.position.x += tempDirection.x * moveDistanceThisFrame;
        transform.position.y += tempDirection.y * moveDistanceThisFrame;
    }
}
