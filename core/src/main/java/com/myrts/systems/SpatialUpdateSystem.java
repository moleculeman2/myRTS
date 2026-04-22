package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.myrts.components.SpatialComponent;
import com.myrts.components.TransformComponent;
import com.myrts.map.SpatialPartitionGrid;

public class SpatialUpdateSystem extends IteratingSystem {

    private final SpatialPartitionGrid spatialGrid;

    public SpatialUpdateSystem(SpatialPartitionGrid spatialGrid) {
        // Only process entities that have both a Transform and a Spatial component
        super(Family.all(TransformComponent.class, SpatialComponent.class).get());
        this.spatialGrid = spatialGrid;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        SpatialComponent spatial = entity.getComponent(SpatialComponent.class);

        // Calculate the cell the unit is currently standing in
        int currentCellX = spatialGrid.getCellX(transform.position.x);
        int currentCellY = spatialGrid.getCellY(transform.position.y);

        // If the unit has moved into a new cell (or was just created)
        if (currentCellX != spatial.cellX || currentCellY != spatial.cellY) {

            // 1. Remove from old cell
            if (spatial.cellX != -1 && spatial.cellY != -1) {
                spatialGrid.removeEntity(entity, spatial.cellX, spatial.cellY);
            }

            // 2. Add to new cell
            spatialGrid.addEntity(entity, transform.position.x, transform.position.y);

            // 3. Update the component to remember the new cell
            spatial.cellX = currentCellX;
            spatial.cellY = currentCellY;
        }
    }
}
