package com.myrts.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.myrts.GameBase;
import com.myrts.blueprints.BuildingType;
import com.myrts.blueprints.UnitType;
import com.myrts.input.InputProcessor;
import com.myrts.map.MapManager;
import com.myrts.map.NavMeshRenderer;
import com.myrts.systems.*;
import com.myrts.entities.EntityFactory;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class GameScreen implements Screen {
    private final GameBase game;
    private PooledEngine engine;
    private OrthographicCamera camera;
    private MapManager mapManager;
    private InputProcessor inputProcessor;

    private ShapeRenderer shapeRenderer;

    public GameScreen(GameBase game) {
        this.game = game;
        this.engine = new PooledEngine();
        EntityFactory.initialize();
        EntityFactory.createUnit(engine, 100, 100, UnitType.TANK);
        EntityFactory.createUnit(engine, 150, 100, UnitType.INFANTRY);
        EntityFactory.createUnit(engine, 100, 150, UnitType.SCOUT);

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
        engine.addSystem(new BuildingDestructionSystem(mapManager));
        engine.addSystem(new MovementSystem(mapManager));
        engine.addSystem(new PathfindingSystem(mapManager));
        engine.addSystem(new PathDebugRenderSystem(shapeRenderer, camera));

        // Give the Reaper a high priority number so it runs LAST
        ReaperSystem reaper = new ReaperSystem();
        reaper.priority = 100;
        engine.addSystem(reaper);
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

        // Render Debug/NavMesh (Optional)
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        NavMeshRenderer.drawDelaunay(shapeRenderer, mapManager.getNavMeshTriangles(), Color.CYAN);
        NavMeshRenderer.drawNeighborTickMarks(shapeRenderer, mapManager.getNavMeshTriangles());
        //NavMeshRenderer.drawCentroidLinks(shapeRenderer, mapManager.getNavMeshTriangles());

        NavMeshRenderer.drawEdges(shapeRenderer, mapManager.dEdges);
        //NavMeshRenderer.drawDelaunay(shapeRenderer, mapManager.dTriangles, Color.RED);

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
