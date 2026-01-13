package com.citysurvival.core.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;
import com.citysurvival.core.model.*;
import com.citysurvival.core.model.items.Food;
import com.citysurvival.core.model.items.Weapon;

import java.util.ArrayList;
import java.util.List;

public class TmxMapLoaderService {

    public static class LoadedTmx {
        public final TiledMap tiledMap;
        public final TileType[][] collision;
        public final Player player;
        public final List<Enemy> enemies;
        public final List<WorldObject> objects;

        public LoadedTmx(TiledMap tiledMap, TileType[][] collision, Player player, List<Enemy> enemies, List<WorldObject> objects) {
            this.tiledMap = tiledMap;
            this.collision = collision;
            this.player = player;
            this.enemies = enemies;
            this.objects = objects;
        }
    }

    public LoadedTmx load(String tmxInternalPath, int tileSize) {
        TiledMap map = new TmxMapLoader().load(tmxInternalPath);

        int width = map.getProperties().get("width", Integer.class);
        int height = map.getProperties().get("height", Integer.class);

        TileType[][] collision = new TileType[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                collision[x][y] = TileType.FLOOR;

        MapLayer collisionLayer = map.getLayers().get("Collision");
        if (collisionLayer != null && collisionLayer instanceof com.badlogic.gdx.maps.tiled.TiledMapTileLayer tl) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (tl.getCell(x, y) != null) collision[x][y] = TileType.WALL;
                }
            }
        } else {
            Gdx.app.log("TMX", "No Collision layer found. Everything walkable.");
        }

        Player player = null;
        List<Enemy> enemies = new ArrayList<>();
        List<WorldObject> objects = new ArrayList<>();

        MapLayer spawns = map.getLayers().get("Spawns");
        if (spawns == null) throw new IllegalStateException("TMX must contain an Object Layer named 'Spawns'");

        for (MapObject obj : spawns.getObjects()) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle rect = rmo.getRectangle();

            int tx = (int) (rect.x / tileSize);
            int ty = (int) (rect.y / tileSize);

            String name = obj.getName() == null ? "" : obj.getName().trim().toLowerCase();

            switch (name) {
                case "player" -> player = new Player(tx, ty, 10);
                case "enemy" -> enemies.add(new Enemy(tx, ty, new Weapon("Enemy Weapon L1", 1)));
                case "food" -> objects.add(new WorldObject(tx, ty, new Food("Food", 2)));
                case "weapon1" -> objects.add(new WorldObject(tx, ty, new Weapon("Weapon L1", 1)));
                case "weapon2" -> objects.add(new WorldObject(tx, ty, new Weapon("Weapon L2", 2)));
                default -> {}
            }
        }

        if (player == null) throw new IllegalStateException("Spawns layer must contain an object named 'player'");

        return new LoadedTmx(map, collision, player, enemies, objects);
    }
}
