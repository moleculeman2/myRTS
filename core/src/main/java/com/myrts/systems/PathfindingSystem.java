package com.myrts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.myrts.components.PathComponent;
import com.myrts.components.TargetDestinationComponent;
import com.myrts.components.TransformComponent;
import com.myrts.components.UnitComponent;
import com.myrts.map.FunnelSmoother;
import com.myrts.map.MapManager;
import com.myrts.map.TrianglePathfinder;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class PathfindingSystem extends IteratingSystem {

    private final MapManager mapManager;

    public PathfindingSystem(MapManager mapManager) {
        // Only process entities that have a Transform, a Unit definition, AND a Target Destination
        super(Family.all(TransformComponent.class, UnitComponent.class, TargetDestinationComponent.class).get());
        this.mapManager = mapManager;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        UnitComponent unit = entity.getComponent(UnitComponent.class);
        TargetDestinationComponent intent = entity.getComponent(TargetDestinationComponent.class);

        float unitRadius = unit.radius;
        float unitCenterX = transform.position.x + (transform.width / 2f);
        float unitCenterY = transform.position.y + (transform.height / 2f);

        DelaunayTriangle startTri = mapManager.getTriangleAt(unitCenterX, unitCenterY);
        DelaunayTriangle targetTri = mapManager.getTriangleAt(intent.target.x, intent.target.y);

        Vector2 startPos = new Vector2(unitCenterX, unitCenterY);
        Vector2 endPos = new Vector2(intent.target.x, intent.target.y);

        boolean clickedBlockedTile = false;
        if (targetTri == null) {
            targetTri = mapManager.getClosestTriangle(intent.target.x, intent.target.y);
            clickedBlockedTile = true;
        }

        if (startTri != null && targetTri != null) {
            if (clickedBlockedTile) {
                clampToTriangleCenter(endPos, targetTri);
            }

            Array<DelaunayTriangle> path = TrianglePathfinder.findPath(startTri, targetTri, startPos, endPos, unitRadius);

            if (path.size > 0) {
                if (path.peek() != targetTri) {
                    clampToTriangleCenter(endPos, path.peek());
                }

                PathComponent pathComp = entity.getComponent(PathComponent.class);
                if (pathComp == null) {
                    pathComp = getEngine().createComponent(PathComponent.class);
                    entity.add(pathComp);
                }

                pathComp.waypoints.clear();
                pathComp.currentWaypointIndex = 0;
                pathComp.waypoints = FunnelSmoother.stringPull(path, startPos, endPos, unitRadius);
            }
        } else {
            System.out.println("Cannot find path. Start is off the NavMesh.");
        }

        // CRITICAL: Remove the intent component so we don't recalculate the path next frame!
        entity.remove(TargetDestinationComponent.class);
    }

    private void clampToTriangleCenter(Vector2 pos, DelaunayTriangle tri) {
        float cx = (tri.points[0].getXf() + tri.points[1].getXf() + tri.points[2].getXf()) / 3f;
        float cy = (tri.points[0].getYf() + tri.points[1].getYf() + tri.points[2].getYf()) / 3f;
        pos.set(cx, cy);
    }
}
