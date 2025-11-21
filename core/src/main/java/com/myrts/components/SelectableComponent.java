package com.myrts.components;

import com.badlogic.ashley.core.Component;

public class SelectableComponent implements Component {
    public boolean selected = false;
    public float selectionRadius = 0.5f;
}