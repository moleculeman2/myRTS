package com.myrts.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.myrts.GameMain;
import com.myrts.input.RTSInputProcessor;
import com.myrts.map.MapManager;
import com.myrts.map.NavMeshGenerator;
import com.myrts.map.NavMeshRenderer;
import com.myrts.map.Triangle;
import com.myrts.systems.RenderSystem;
import org.poly2tri.Poly2Tri;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import com.myrts.map.ContourTracer;
import java.util.List;
import com.badlogic.gdx.Input;
import com.myrts.entities.EntityFactory;

public class GameScreen2 implements Screen {
    private final GameMain game;
    private Engine engine;
    private OrthographicCamera camera;
    private MapManager mapManager;
    private RTSInputProcessor inputProcessor;
    private Vector3 touchPos = new Vector3();

    private ShapeRenderer shapeRenderer;
    private com.badlogic.gdx.utils.Array<Triangle> navMesh;
    private com.badlogic.gdx.utils.Array<DelaunayTriangle> navMeshTriangles;
    private NavMeshRenderer navMeshRenderer;

    public GameScreen2(GameMain game) {
        this.game = game;
        this.engine = new Engine();
        EntityFactory.initialize();
        // Set up camera
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        // Load map
        mapManager = new MapManager("simplestmap.tmx");
        // ---  NAVMESH GENERATION ---
        System.out.println("Tracing map contours...");
        List<Polygon> polygonsToTriangulate = ContourTracer.trace(mapManager);

        System.out.println("Found " + polygonsToTriangulate.size() + " walkable areas. Triangulating...");
        this.navMeshTriangles = new com.badlogic.gdx.utils.Array<>();

        for (Polygon polygon : polygonsToTriangulate) {
            Poly2Tri.triangulate(polygon);
            for (DelaunayTriangle triangle : polygon.getTriangles()) {
                navMeshTriangles.add(triangle);
            }
        }

        System.out.println("Generated navmesh with " + navMeshTriangles.size + " triangles.");
        // --- END OF NEW NAVMESH GENERATION ---

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
        NavMeshRenderer.drawDelaunay(shapeRenderer, navMeshTriangles);
        game.batch.setProjectionMatrix(camera.combined);
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
        EntityFactory.dispose();
    }
}
