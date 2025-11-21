package com.myrts.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.myrts.GameMain;
import com.myrts.map.NavMeshGeneratorOld;
import com.myrts.map.NavMeshGenerator;
import com.myrts.map.NavMeshRenderer;
import com.myrts.map.Triangle;
import com.myrts.systems.RenderSystem;
import com.myrts.input.RTSInputProcessor;
import com.myrts.map.MapManager;

public class GameScreen implements Screen {
    private final GameMain game;
    private Engine engine;
    private OrthographicCamera camera;
    private MapManager mapManager;
    private RTSInputProcessor inputProcessor;
    private Vector3 touchPos = new Vector3();

    private ShapeRenderer shapeRenderer;
    private com.badlogic.gdx.utils.Array<Triangle> navMesh;
    private NavMeshRenderer navMeshRenderer;

    public GameScreen(GameMain game) {
        this.game = game;
        this.engine = new Engine();

        // Set up camera
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        // Load map
        mapManager = new MapManager("simplestmap.tmx");
        // 2. Generate the navigation mesh from the map
        System.out.println("Generating NavMesh...");
        navMesh = NavMeshGenerator.generate(mapManager.getMap());



        // Set up input handling
        inputProcessor = new RTSInputProcessor(camera, mapManager, engine);
        Gdx.input.setInputProcessor(inputProcessor);

        // Initialize systems
        initializeSystems();

        // Create map entities
        //mapManager.createEntitiesFromMap(engine);
    }

    private void initializeSystems() {
        // Add render system
        engine.addSystem(new RenderSystem(game.batch));

        // Add other systems here
        // engine.addSystem(new MovementSystem());
        // engine.addSystem(new CombatSystem());
        // etc.
    }

    @Override
    public void render(float delta) {
        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Snap camera position to pixel grid
        camera.position.x = Math.round(camera.position.x);
        camera.position.y = Math.round(camera.position.y);

        // Update camera
        camera.update();

        // Render map
        mapManager.render(camera);
        shapeRenderer.setProjectionMatrix(camera.combined);
        NavMeshRenderer.draw(shapeRenderer, navMesh);

        // Update engine systems
        engine.update(delta);

        // Update input processing
        inputProcessor.update(delta);

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
    }
}
