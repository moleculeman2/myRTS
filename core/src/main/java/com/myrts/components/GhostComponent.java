package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.myrts.blueprints.BuildingType;
import com.badlogic.gdx.utils.Pool.Poolable;


public class GhostComponent implements Component, Poolable {
    public BuildingType blueprint;
    public boolean canBuild = false;

    @Override
    public void reset() {
        this.blueprint = null;
        this.canBuild = false;
    }
}
