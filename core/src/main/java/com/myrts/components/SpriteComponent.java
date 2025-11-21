package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class SpriteComponent implements Component {

    public TextureRegion region;
    public float zIndex = 0.0f;
}
