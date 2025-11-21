package com.myrts.map;

import com.badlogic.gdx.math.Vector2;

/**
 * Represents a single triangle in our navigation mesh.
 * It holds references to its three vertices.
 */
public class Triangle {
    public final Vector2 p1, p2, p3;

    public Triangle(Vector2 p1, Vector2 p2, Vector2 p3) {
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
    }
}
