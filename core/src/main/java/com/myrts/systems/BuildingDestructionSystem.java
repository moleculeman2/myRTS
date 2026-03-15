package com.myrts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.myrts.components.BuildingComponent;
import com.myrts.components.DestroyedComponent;
import com.myrts.components.TransformComponent;
import com.myrts.map.MapManager;

public class BuildingDestructionSystem extends IteratingSystem {
    private final MapManager mapManager;
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    public BuildingDestructionSystem(MapManager mapManager) {
        // Only process entities that are BOTH Buildings AND Destroyed
        super(Family.all(BuildingComponent.class, DestroyedComponent.class).get());
        this.mapManager = mapManager;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = tm.get(entity);

        mapManager.unregisterBuildingObstacle(
            transform.position.x,
            transform.position.y,
            transform.width,
            transform.height
        );

        // Remove the BuildingComponent so this system doesn't process it again next frame
        // in case the Reaper system hasn't cleaned it up yet.
        //entity.remove(BuildingComponent.class);
    }
}
