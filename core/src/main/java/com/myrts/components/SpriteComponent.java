package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool.Poolable;


public class SpriteComponent implements Component, Poolable {
    public TextureRegion region;
    public float zIndex = 0.0f;
    //Default to white (no tint, full opacity)
    public Color color = new Color(1, 1, 1, 1);

    @Override
    public void reset() {
        this.region = null;
        this.zIndex = 0f;
        this.color.set(Color.WHITE);
    }
}
