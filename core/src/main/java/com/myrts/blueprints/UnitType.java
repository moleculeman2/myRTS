package com.myrts.blueprints;

public enum UnitType {
    // Name(texturePath, health, speed, damage, range, width, height)
    INFANTRY("assets/unit.png", 100f, 6.0f, 10f, 1.0f, 32f, 32f),
    SCOUT("assets/unit.png", 60f, 5.0f, 5f, 2.0f, 20f, 20f), // Reusing unit.png for testing
    TANK("assets/unit.png", 400f, 1.5f, 40f, 4.0f, 48f, 48f);

    public final String texturePath;
    public final float maxHealth;
    public final float moveSpeed;
    public final float attackPower;
    public final float attackRange;
    public final float width;
    public final float height;

    UnitType(String texturePath, float maxHealth, float moveSpeed, float attackPower, float attackRange, float width, float height) {
        this.texturePath = texturePath;
        this.maxHealth = maxHealth;
        this.moveSpeed = moveSpeed;
        this.attackPower = attackPower;
        this.attackRange = attackRange;
        this.width = width;
        this.height = height;
    }
}
