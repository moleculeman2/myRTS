package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;


public class ResourceComponent implements Component, Poolable {
    public int amount = 100;
    public String type = "gold"; // gold, wood, stone, etc.

    @Override
    public void reset() {
        this.amount = 0;
        this.type = null;
    }
}
