package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.myrts.blueprints.BuildingType;

public class BuildingComponent implements Component, Poolable {
    public BuildingType type;
    public float currentHealth;

    // You can add an initialization method to easily set it up
    public void init(BuildingType type) {
        this.type = type;
        this.currentHealth = type.maxHealth;
    }

    @Override
    public void reset() {
        this.type = null;
        this.currentHealth = 0;
    }
}
