package com.myrts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;



public class UnitComponent implements Component, Poolable{
    public float health = 100.0f;
    public float attackPower = 10.0f;
    public float attackRange = 1.0f;
    public float moveSpeed = 2.0f;
    public int playerOwner = 0; // Which player owns this unit

    @Override
    public void reset() {
        this.health = 0f;
        this.attackPower = 0f;
        this.attackRange = 0f;
        this.moveSpeed = 0f;
        this.playerOwner = 0;
    }
}
