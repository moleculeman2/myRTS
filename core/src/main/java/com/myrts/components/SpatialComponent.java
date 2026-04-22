package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class SpatialComponent implements Component, Poolable {
    public int cellX = -1;
    public int cellY = -1;

    @Override
    public void reset() {
        cellX = -1;
        cellY = -1;
    }
}
