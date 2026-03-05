package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.myrts.components.DestroyedComponent;

public class ReaperSystem extends IteratingSystem {

    public ReaperSystem() {
        super(Family.all(DestroyedComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // The entity's affairs are in order. Send it to the void.
        getEngine().removeEntity(entity);
    }
}
