package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;

public class PathComponent implements Component, Poolable {
    public Array<Vector2> waypoints = new Array<>();
    public int currentWaypointIndex = 0;

    @Override
    public void reset() {
        waypoints.clear();
        currentWaypointIndex = 0;
    }
}
