package com.myrts.components;

import com.badlogic.ashley.core.Component;

public class ResourceComponent implements Component {
    public int amount = 100;
    public String type = "gold"; // gold, wood, stone, etc.
}
