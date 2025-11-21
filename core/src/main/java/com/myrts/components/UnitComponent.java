package com.myrts.components;

import com.badlogic.ashley.core.Component;

public class UnitComponent implements Component {
    public float health = 100.0f;
    public float attackPower = 10.0f;
    public float attackRange = 1.0f;
    public float moveSpeed = 2.0f;
    public int playerOwner = 0; // Which player owns this unit
}