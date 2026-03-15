package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;


public class CollisionComponent implements Component, Poolable {
    public boolean collidable = true;

    @Override
    public void reset() {
        this.collidable = false;
    }
}
