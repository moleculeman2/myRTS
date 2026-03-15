package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;


public class SelectableComponent implements Component, Poolable {
    public boolean selected = false;
    public float selectionRadius = 0.5f;

    @Override
    public void reset() {
        this.selected = false;
        this.selectionRadius = 0f;
    }
}
