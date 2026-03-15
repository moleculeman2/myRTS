package com.myrts.blueprints;

public enum BuildingType {
    HEADQUARTERS(4, 4, 2000f, "assets/headquarters.png"), // Reusing your existing asset
    BARRACKS(3, 2, 1000f, "assets/barracks.png"),
    GENERATOR(2, 2, 500f, "assets/generator.png"),
    TURRET(1, 1, 300f, "assets/turret.png");

    public final int widthTiles;
    public final int heightTiles;
    public final float maxHealth;
    public final String texturePath;

    BuildingType(int widthTiles, int heightTiles, float maxHealth, String texturePath) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.maxHealth = maxHealth;
        this.texturePath = texturePath;
    }
}
