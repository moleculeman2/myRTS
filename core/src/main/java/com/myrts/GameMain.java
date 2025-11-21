package com.myrts;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.myrts.screens.MainMenuScreen;

public class GameMain extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public AssetManager assets;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        assets = new AssetManager();

        // Load assets here if needed
        // assets.load("textures/units.png", Texture.class);

        // Wait for assets to finish loading
        assets.finishLoading();

        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        assets.dispose();
    }
}
