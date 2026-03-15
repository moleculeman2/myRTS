package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;


/**
 * Marker component indicating this entity should be removed from the game.
 */
public class DestroyedComponent implements Component, Poolable {
    @Override
    public void reset() {

    }
}
