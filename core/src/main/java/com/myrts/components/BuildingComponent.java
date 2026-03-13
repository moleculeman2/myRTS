package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.myrts.blueprints.BuildingType;

public class BuildingComponent implements Component {
    public BuildingType type;
    public float currentHealth;

    // You can add an initialization method to easily set it up
    public void init(BuildingType type) {
        this.type = type;
        this.currentHealth = type.maxHealth;
    }
}
