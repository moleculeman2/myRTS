package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.myrts.blueprints.UnitType;

public class UnitComponent implements Component, Poolable{
    public UnitType type;
    public float health = 100.0f;
    public float attackPower = 10.0f;
    public float attackRange = 1.0f;
    public float moveSpeed = 2.0f;
    public int playerOwner = 0; // Which player owns this unit
    public float radius = 0.5f;

    public void init(UnitType type) {
        this.type = type;
        this.health = type.maxHealth;
        this.attackPower = type.attackPower;
        this.attackRange = type.attackRange;
        this.moveSpeed = type.moveSpeed;
        this.radius = type.radius;
    }

    @Override
    public void reset() {
        this.type = null;
        this.health = 0f;
        this.attackPower = 0f;
        this.attackRange = 0f;
        this.moveSpeed = 0f;
        this.playerOwner = 0;
        this.radius = 0f;
    }
}
