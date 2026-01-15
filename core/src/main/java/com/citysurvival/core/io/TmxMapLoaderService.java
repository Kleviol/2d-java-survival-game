package com.citysurvival.core.io;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;
import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.Player;
import com.citysurvival.core.model.TileType;
import com.citysurvival.core.model.WorldObject;
import com.citysurvival.core.model.items.Food;
import com.citysurvival.core.model.items.Weapon;

public class TmxMapLoaderService {

    private static TiledMapTileLayer findTileLayer(TiledMap map, String... names) {
        for (String n : names) {
            MapLayer l = map.getLayers().get(n);
            if (l instanceof TiledMapTileLayer tl) return tl;
        }
        return null;
    }

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

        Integer mapTileWidth = map.getProperties().get("tilewidth", Integer.class);
        int mapTileSize = (mapTileWidth != null && mapTileWidth > 0) ? mapTileWidth : tileSize;

        int width = map.getProperties().get("width", Integer.class);
        int height = map.getProperties().get("height", Integer.class);

        Player player = null;
        List<Enemy> enemies = new ArrayList<>();
        List<WorldObject> objects = new ArrayList<>();

        MapLayer spawns = map.getLayers().get("Spawns");
        if (spawns == null) spawns = map.getLayers().get("Spawn");
        if (spawns == null) throw new IllegalStateException("TMX must contain an Object Layer named 'Spawns' (or 'Spawn')");

        for (MapObject obj : spawns.getObjects()) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle rect = rmo.getRectangle();

            int tx = (int) (rect.x / mapTileSize);
            int ty = (int) (rect.y / mapTileSize);

            String name = obj.getName() == null ? "" : obj.getName().trim().toLowerCase();

            switch (name) {
                case "player" -> player = new Player(tx, ty, 10);
                case "enemy", "enemy1" -> enemies.add(new Enemy(tx, ty, new Weapon("Enemy 1", 1), 1));
                case "enemy2" -> enemies.add(new Enemy(tx, ty, new Weapon("Enemy 2", 2), 2));
                case "food" -> objects.add(new WorldObject(tx, ty, new Food("Food", 1)));
                case "weapon1" -> objects.add(new WorldObject(tx, ty, new Weapon("Weapon L1", 1)));
                case "weapon2" -> objects.add(new WorldObject(tx, ty, new Weapon("Weapon L2", 2)));
                default -> {}
            }
        }

        if (player == null) throw new IllegalStateException("Spawns layer must contain an object named 'player'");

        TileType[][] collision = new TileType[width][height];

        TiledMapTileLayer collisionTl = findTileLayer(map, "Collision", "collision", "Collisions", "collisions");
        TiledMapTileLayer buildingsTl = findTileLayer(map, "Buildings", "buildings");

        boolean[][] rawBlocked = new boolean[width][height];
        int rawBlockedCount = 0;

        if (collisionTl != null) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (collisionTl.getCell(x, y) != null) rawBlocked[x][y] = true;
                }
            }
        }
        if (buildingsTl != null) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (buildingsTl.getCell(x, y) != null) rawBlocked[x][y] = true;
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (rawBlocked[x][y]) rawBlockedCount++;
            }
        }

        boolean anyRaw = rawBlockedCount > 0;
        boolean allRaw = rawBlockedCount == width * height;
        boolean spawnMarkedBlocked = anyRaw && player.x() >= 0 && player.y() >= 0 && player.x() < width && player.y() < height && rawBlocked[player.x()][player.y()];
        boolean invert = spawnMarkedBlocked && !allRaw;

        int finalBlockedCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean blocked = rawBlocked[x][y];
                if (invert) blocked = !blocked;
                collision[x][y] = blocked ? TileType.WALL : TileType.FLOOR;
                if (blocked) finalBlockedCount++;
            }
        }

        if (collisionTl == null && buildingsTl == null) {
            Gdx.app.log("TMX", "No collision/buildings tile layer found. Everything walkable.");
        } else {
            Gdx.app.log(
                    "TMX",
                    "Collision derived from tile layers: collision=" + (collisionTl != null) + ", buildings=" + (buildingsTl != null)
                            + ", rawBlocked=" + rawBlockedCount + "/" + (width * height)
                            + ", inverted=" + invert
                            + ", finalBlocked=" + finalBlockedCount + "/" + (width * height)
                            + ", playerSpawnTile=" + player.x() + "," + player.y()
                            + ", tileSizeUsed=" + mapTileSize);
        }

        return new LoadedTmx(map, collision, player, enemies, objects);
    }
}
