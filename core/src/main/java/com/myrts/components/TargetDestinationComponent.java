package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;

public class TargetDestinationComponent implements Component, Poolable {
    public final Vector2 target = new Vector2();
    public boolean isAttackMove = false;

    @Override
    public void reset() {
        target.set(0, 0);
        isAttackMove = false;
    }
}
