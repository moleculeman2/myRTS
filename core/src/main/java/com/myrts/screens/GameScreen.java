package com.myrts.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.myrts.GameBase;
import com.myrts.input.InputProcessor;
import com.myrts.map.MapManager;
import com.myrts.map.NavMeshRenderer;
import com.myrts.systems.RenderSystem;
import com.myrts.entities.EntityFactory;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class GameScreen implements Screen {
    private final GameBase game;
    private Engine engine;
    private OrthographicCamera camera;
    private MapManager mapManager;
    private InputProcessor inputProcessor;

    private ShapeRenderer shapeRenderer;

    public GameScreen(GameBase game) {
        this.game = game;
        this.engine = new Engine();
        EntityFactory.initialize();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        shapeRenderer = new ShapeRenderer();
        mapManager = new MapManager("smallmap.tmx");

        inputProcessor = new InputProcessor(camera, mapManager, engine);
        Gdx.input.setInputProcessor(inputProcessor);

        initializeSystems();
    }

    private void initializeSystems() {
        engine.addSystem(new RenderSystem(game.batch));
    }

    @Override
    public void render(float delta) {
        // Clear
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update Camera & Systems
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);

        // Render Map & Entities
        mapManager.render(camera);
        engine.update(delta);

        // Update Input (calculates ghost position)
        inputProcessor.update(delta);

        // --- NEW: Render Ghost Building ---
        if (inputProcessor.isPlacingMode()) {
            game.batch.begin();

            // Set Color based on validity (Green or Red) with 50% opacity
            if (inputProcessor.isCanBuild()) {
                game.batch.setColor(0f, 1f, 0f, 0.5f);
            } else {
                game.batch.setColor(1f, 0f, 0f, 0.5f);
            }

            game.batch.draw(
                EntityFactory.getBuildingTexture(),
                inputProcessor.getGhostPos().x,
                inputProcessor.getGhostPos().y,
                inputProcessor.getGhostWidth(),
                inputProcessor.getGhostHeight()
            );

            // Reset color to white so other things don't look weird
            game.batch.setColor(Color.WHITE);
            game.batch.end();
        }

        // Render Debug/NavMesh (Optional)
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        NavMeshRenderer.drawDelaunay(shapeRenderer, mapManager.getNavMeshTriangles());
        //NavMeshRenderer.drawCentroidLinks(shapeRenderer, mapManager.getNavMeshTriangles());
        NavMeshRenderer.drawNeighborLinks(shapeRenderer, mapManager.getNavMeshTriangles(), 6);
        for (DelaunayTriangle triangle : mapManager.getNavMeshTriangles()) {
            NavMeshRenderer.drawNeighborTickMarks(shapeRenderer, triangle);
        }
        shapeRenderer.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void show() {}

    @Override
    public void dispose() {
        mapManager.dispose();
        shapeRenderer.dispose();
        EntityFactory.dispose();
    }
}
