package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.myrts.blueprints.BuildingType;

public class GhostComponent implements Component {
    public BuildingType blueprint;
    public boolean canBuild = false;
}
