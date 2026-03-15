package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;


public class TransformComponent implements Component, Poolable {
    public Vector2 position = new Vector2();
    public Vector2 scale = new Vector2(1, 1);
    public float rotation = 0.0f;
    public float width = 0.0f;
    public float height = 0.0f;

    @Override
    public void reset() {
        this.position.x = 0;
        this.position.y = 0;
        this.scale.x = 1;
        this.scale.y= 1;
        this.rotation = 0f;
        this.width = 0f;
        this.height = 0f;
    }
}
