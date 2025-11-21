package com.myrts.components;

import com.badlogic.ashley.core.Component;

/**
 * A simple marker component to identify entities that are buildings.
 * We can add properties like health, build time, etc. later.
 */
public class BuildingComponent implements Component {
    public float health = 500f;
}
