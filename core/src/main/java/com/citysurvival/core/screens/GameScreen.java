package com.citysurvival.core.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.citysurvival.core.io.SaveGameService;
import com.citysurvival.core.io.TmxMapLoaderService;
import com.citysurvival.core.logic.CombatSystem;
import com.citysurvival.core.logic.EnemyAISystem;
import com.citysurvival.core.model.*;
import com.citysurvival.core.model.items.Item;
import com.citysurvival.core.model.items.ItemType;
import com.citysurvival.core.model.items.Weapon;
import com.citysurvival.core.supabase.CloudSaveService;
import com.citysurvival.core.supabase.SupabaseClient;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class GameScreen extends ScreenAdapter {
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final OrthographicCamera camera = new OrthographicCamera();

    private final EnemyAISystem enemyAI = new EnemyAISystem();
    private final CombatSystem combat = new CombatSystem();
    private final SaveGameService saveGame = new SaveGameService();

    private Texture texPlayer, texEnemy, texFood, texW1, texW2;
    private boolean useTextures;

    private int tileSize = 32;
    private String tmxMapPath = "maps/city1.tmx";
    private String saveFile = "savegame.json";

    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;

    private TileType[][] collision;
    private Player player;
    private List<Enemy> enemies;
    private List<WorldObject> objects;

    private final GameStats stats = new GameStats();
    private boolean gameOver = false;

    // Supabase (optional)
    private CloudSaveService cloudSave;
    private String cloudPlayerId;
    private int cloudSlot = 1;

    @Override
    public void show() {
        loadGameProperties();
        loadAssets();
        loadNewGameFromTmx();

        camera.setToOrtho(false, 1200, 720);

        initSupabaseIfConfigured();
    }

    private void loadGameProperties() {
        try {
            Properties p = new Properties();
            p.load(Gdx.files.internal("config/game.properties").read());
            tileSize = Integer.parseInt(p.getProperty("tileSize", "32"));
            tmxMapPath = p.getProperty("tmxMap", "maps/city1.tmx");
            saveFile = p.getProperty("saveFile", "savegame.json");
        } catch (Exception ignored) {}
    }

    private void initSupabaseIfConfigured() {
        try {
            InputStream is = Gdx.files.classpath("supabase.properties").read();
            Properties p = new Properties();
            p.load(is);

            String url = p.getProperty("url");
            String key = p.getProperty("key");
            cloudPlayerId = p.getProperty("playerId");
            cloudSlot = Integer.parseInt(p.getProperty("slot", "1"));

            if (url != null && key != null && cloudPlayerId != null) {
                cloudSave = new CloudSaveService(new SupabaseClient(url, key));
            }
        } catch (Exception ignored) {
            cloudSave = null;
        }
    }

    private void loadAssets() {
        texPlayer = tryLoad("sprites/player.png");
        texEnemy = tryLoad("sprites/enemy.png");
        texFood = tryLoad("sprites/food.png");
        texW1 = tryLoad("sprites/weapon_lv1.png");
        texW2 = tryLoad("sprites/weapon_lv2.png");
        useTextures = texPlayer != null && texEnemy != null;
    }

    private Texture tryLoad(String internalPath) {
        try {
            if (!Gdx.files.internal(internalPath).exists()) return null;
            return new Texture(Gdx.files.internal(internalPath));
        } catch (Exception e) {
            return null;
        }
    }

    private void loadNewGameFromTmx() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null) tiledMap.dispose();

        TmxMapLoaderService.LoadedTmx loaded = new TmxMapLoaderService().load(tmxMapPath, tileSize);
        tiledMap = loaded.tiledMap;
        collision = loaded.collision;
        player = loaded.player;
        enemies = loaded.enemies;
        objects = loaded.objects;

        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, 1f);

        stats.steps = 0;
        stats.enemiesDefeated = 0;
        stats.itemsCollected = 0;
        gameOver = false;
    }

    @Override
    public void render(float delta) {
        handleInput();
        updateCamera();

        ScreenUtils.clear(0.07f, 0.07f, 0.09f, 1);

        mapRenderer.setView(camera);
        mapRenderer.render();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawObjects();
        drawEnemies();
        drawPlayer();
        batch.end();

        batch.setProjectionMatrix(new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()).combined);
        batch.begin();
        drawHud();
        batch.end();
    }

    private void handleInput() {
        if (gameOver) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) loadNewGameFromTmx();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            player.inventory().useFirstFood(player);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            player.inventory().equipWeaponLevel(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            player.inventory().equipWeaponLevel(2);
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) saveLocal();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) loadLocal();

        if (Gdx.input.isKeyJustPressed(Input.Keys.F6)) uploadCloud();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F10)) downloadCloud();

        Direction dir = null;
        if (Gdx.input.isKeyJustPressed(Input.Keys.W) || Gdx.input.isKeyJustPressed(Input.Keys.UP)) dir = Direction.UP;
        if (Gdx.input.isKeyJustPressed(Input.Keys.S) || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) dir = Direction.DOWN;
        if (Gdx.input.isKeyJustPressed(Input.Keys.A) || Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) dir = Direction.LEFT;
        if (Gdx.input.isKeyJustPressed(Input.Keys.D) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) dir = Direction.RIGHT;

        if (dir != null) attemptMovePlayer(dir);
    }

    private void attemptMovePlayer(Direction dir) {
        int nx = player.x() + dir.dx;
        int ny = player.y() + dir.dy;

        if (!inBounds(nx, ny)) return;
        if (!collision[nx][ny].walkable) return;

        player.setPos(nx, ny);
        stats.steps++;

        pickupObjectsIfAny(nx, ny);

        enemyAI.moveEnemiesAfterPlayer(collision, enemies);

        resolveCombatIfAny();
        if (player.isDead()) gameOver = true;
    }

    private void pickupObjectsIfAny(int x, int y) {
        Iterator<WorldObject> it = objects.iterator();
        while (it.hasNext()) {
            WorldObject obj = it.next();
            if (obj.x == x && obj.y == y) {
                player.inventory().add(obj.item);
                stats.itemsCollected++;
                it.remove();
            }
        }
    }

    private void resolveCombatIfAny() {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (e.x() == player.x() && e.y() == player.y()) {
                CombatSystem.CombatResult result = combat.fight(player, e);
                switch (result) {
                    case PLAYER_WINS -> {
                        it.remove();
                        stats.enemiesDefeated++;
                    }
                    case ENEMY_WINS -> player.damage(2);
                    case NO_WEAPON -> player.damage(3);
                }
            }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < collision.length && y < collision[0].length;
    }

    private void updateCamera() {
        float px = player.x() * tileSize + tileSize / 2f;
        float py = player.y() * tileSize + tileSize / 2f;
        camera.position.set(px, py, 0);
        camera.update();
    }

    private void drawObjects() {
        if (!useTextures) return;

        for (WorldObject obj : objects) {
            Texture t = null;
            if (obj.item.type() == ItemType.FOOD) t = texFood;
            if (obj.item.type() == ItemType.WEAPON) {
                Weapon w = (Weapon) obj.item;
                t = (w.level() == 1) ? texW1 : texW2;
            }
            if (t != null) batch.draw(t, obj.x * tileSize, obj.y * tileSize, tileSize, tileSize);
        }
    }

    private void drawEnemies() {
        if (!useTextures || texEnemy == null) return;
        for (Enemy e : enemies) {
            batch.draw(texEnemy, e.x() * tileSize, e.y() * tileSize, tileSize, tileSize);
        }
    }

    private void drawPlayer() {
        if (!useTextures || texPlayer == null) return;
        batch.draw(texPlayer, player.x() * tileSize, player.y() * tileSize, tileSize, tileSize);
    }

    private void drawHud() {
        font.setColor(Color.WHITE);

        String weaponText = player.inventory().equippedWeapon()
                .map(w -> w.name() + " (L" + w.level() + ")")
                .orElse("None");

        font.draw(batch, "HP: " + player.hp() + "/10", 10, Gdx.graphics.getHeight() - 10);
        font.draw(batch, "Equipped: " + weaponText, 10, Gdx.graphics.getHeight() - 30);

        font.draw(batch, "Steps: " + stats.steps, 10, Gdx.graphics.getHeight() - 60);
        font.draw(batch, "Enemies defeated: " + stats.enemiesDefeated, 10, Gdx.graphics.getHeight() - 80);
        font.draw(batch, "Items collected: " + stats.itemsCollected, 10, Gdx.graphics.getHeight() - 100);

        int x = Gdx.graphics.getWidth() - 320;
        int y = Gdx.graphics.getHeight() - 10;
        font.draw(batch, "Inventory:", x, y);
        y -= 20;

        int shown = 0;
        for (Item it : player.inventory().items()) {
            if (shown >= 12) {
                font.draw(batch, "...", x, y);
                break;
            }
            font.draw(batch, "- " + it.name(), x, y);
            y -= 18;
            shown++;
        }

        font.draw(batch, "Move=WASD/Arrows | F=use food | 1/2 equip | F5 save | F9 load", 10, 30);
        font.draw(batch, "Cloud: F6 upload | F10 download (needs supabase.properties)", 10, 12);

        if (gameOver) {
            font.setColor(Color.RED);
            font.draw(batch, "GAME OVER - Press R to restart", 420, 360);
        }
    }

    private void saveLocal() {
        SaveGameService.SaveState state = SaveGameService.buildState(player, enemies, objects, stats);
        saveGame.saveLocal(saveFile, state);
        Gdx.app.log("SAVE", "Saved to " + saveFile);
    }

    private void loadLocal() {
        SaveGameService.SaveState s = saveGame.loadLocal(saveFile);
        if (s == null) {
            Gdx.app.log("SAVE", "No local save found.");
            return;
        }

        player = new Player(s.playerX, s.playerY, 10);
        if (s.playerHp < 10) player.damage(10 - s.playerHp);

        if (s.inventory != null) {
            for (SaveGameService.SavedItem si : s.inventory) {
                if ("FOOD".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Food(si.name, si.healAmount));
                if ("WEAPON".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Weapon(si.name, si.weaponLevel));
            }
        }
        if (s.equippedWeaponLevel > 0) player.inventory().equipWeaponLevel(s.equippedWeaponLevel);

        enemies.clear();
        for (SaveGameService.SavedEnemy se : s.enemies) {
            enemies.add(new Enemy(se.x, se.y, new com.citysurvival.core.model.items.Weapon("Enemy Weapon L" + se.weaponLevel, se.weaponLevel)));
        }

        objects.clear();
        for (SaveGameService.SavedObject so : s.objects) {
            if ("FOOD".equals(so.type)) {
                objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Food(so.name, so.healAmount)));
            } else if ("WEAPON".equals(so.type)) {
                objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Weapon(so.name, so.weaponLevel)));
            }
        }

        stats.steps = s.steps;
        stats.enemiesDefeated = s.enemiesDefeated;
        stats.itemsCollected = s.itemsCollected;

        gameOver = player.isDead();
        Gdx.app.log("SAVE", "Loaded from " + saveFile);
    }

    private void uploadCloud() {
        if (cloudSave == null) {
            Gdx.app.log("CLOUD", "Supabase not configured. Add desktop/src/main/resources/supabase.properties");
            return;
        }
        try {
            SaveGameService.SaveState state = SaveGameService.buildState(player, enemies, objects, stats);
            String json = saveGame.toJson(state);
            cloudSave.uploadSave(cloudPlayerId, cloudSlot, json);
            Gdx.app.log("CLOUD", "Uploaded save to Supabase.");
        } catch (Exception e) {
            Gdx.app.error("CLOUD", "Upload failed: " + e.getMessage(), e);
        }
    }

    private void downloadCloud() {
        if (cloudSave == null) {
            Gdx.app.log("CLOUD", "Supabase not configured. Add desktop/src/main/resources/supabase.properties");
            return;
        }
        try {
            String json = cloudSave.downloadSaveJson(cloudPlayerId, cloudSlot);
            if (json == null) {
                Gdx.app.log("CLOUD", "No cloud save found.");
                return;
            }
            SaveGameService.SaveState s = saveGame.fromJson(json);

            player = new Player(s.playerX, s.playerY, 10);
            if (s.playerHp < 10) player.damage(10 - s.playerHp);

            if (s.inventory != null) {
                for (SaveGameService.SavedItem si : s.inventory) {
                    if ("FOOD".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Food(si.name, si.healAmount));
                    if ("WEAPON".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Weapon(si.name, si.weaponLevel));
                }
            }
            if (s.equippedWeaponLevel > 0) player.inventory().equipWeaponLevel(s.equippedWeaponLevel);

            enemies.clear();
            for (SaveGameService.SavedEnemy se : s.enemies) {
                enemies.add(new Enemy(se.x, se.y, new com.citysurvival.core.model.items.Weapon("Enemy Weapon L" + se.weaponLevel, se.weaponLevel)));
            }

            objects.clear();
            for (SaveGameService.SavedObject so : s.objects) {
                if ("FOOD".equals(so.type)) {
                    objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Food(so.name, so.healAmount)));
                } else if ("WEAPON".equals(so.type)) {
                    objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Weapon(so.name, so.weaponLevel)));
                }
            }

            stats.steps = s.steps;
            stats.enemiesDefeated = s.enemiesDefeated;
            stats.itemsCollected = s.itemsCollected;

            gameOver = player.isDead();
            Gdx.app.log("CLOUD", "Downloaded and loaded cloud save.");
        } catch (Exception e) {
            Gdx.app.error("CLOUD", "Download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null) tiledMap.dispose();
        if (texPlayer != null) texPlayer.dispose();
        if (texEnemy != null) texEnemy.dispose();
        if (texFood != null) texFood.dispose();
        if (texW1 != null) texW1.dispose();
        if (texW2 != null) texW2.dispose();
    }
}
