package com.citysurvival.core.screens;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.citysurvival.core.io.SaveGameService;
import com.citysurvival.core.io.TmxMapLoaderService;
import com.citysurvival.core.logic.CombatSystem;
import com.citysurvival.core.logic.EnemyAISystem;
import com.citysurvival.core.model.Direction;
import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.GameStats;
import com.citysurvival.core.model.Player;
import com.citysurvival.core.model.TileType;
import com.citysurvival.core.model.WorldObject;
import com.citysurvival.core.model.items.Item;
import com.citysurvival.core.model.items.ItemType;
import com.citysurvival.core.model.items.Weapon;
import com.citysurvival.core.supabase.CloudSaveService;
import com.citysurvival.core.supabase.SupabaseClient;

public class GameScreen extends ScreenAdapter {
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final OrthographicCamera hudCamera = new OrthographicCamera();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    private final EnemyAISystem enemyAI = new EnemyAISystem();
    private final CombatSystem combat = new CombatSystem();
    private final SaveGameService saveGame = new SaveGameService();

    private Texture texPlayer, texEnemy, texFood, texW1, texW2;
    private Texture heroSheet;
    private Texture debugPixel;
    private TextureRegion playerRegion;
    private Animation<TextureRegion>[] heroWalk;
    private float heroAnimTime = 0f;
    private boolean movedThisFrame = false;
    private Direction facing = Direction.DOWN;
    private boolean useTextures;

    private boolean debugCollision = false;
    private int lastBlockedX = -1;
    private int lastBlockedY = -1;

    private int tileSize = 32;
    private String tmxMapPath = "maps/city1.tmx";
    private String saveFile = "savegame.json";

    private float cameraZoom = 0.5f;

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

        ensureDebugPixel();

        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.zoom = cameraZoom;
        camera.update();

        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();

        initSupabaseIfConfigured();
    }

    private void ensureDebugPixel() {
        if (debugPixel != null) return;
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        debugPixel = new Texture(pm);
        pm.dispose();
    }

    private void loadGameProperties() {
        try {
            Properties p = new Properties();
            p.load(Gdx.files.internal("config/game.properties").read());
            tileSize = Integer.parseInt(p.getProperty("tileSize", "32"));
            tmxMapPath = p.getProperty("tmxMap", "maps/city1.tmx");
            saveFile = p.getProperty("saveFile", "savegame.json");
            cameraZoom = Float.parseFloat(p.getProperty("cameraZoom", "0.5"));
        } catch (IOException | NumberFormatException | GdxRuntimeException ignored) {
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.zoom = cameraZoom;
        camera.update();

        hudCamera.setToOrtho(false, width, height);
        hudCamera.update();
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
        } catch (IOException | NumberFormatException | GdxRuntimeException ignored) {
            cloudSave = null;
        }
    }

    private void loadAssets() {
        // Player can be either a single texture or a sprite sheet.
        String heroPath = "sprites/hero/hero.png";
        heroSheet = tryLoad(heroPath);
        if (heroSheet != null) {
            initHeroAnimations(heroPath, heroSheet);
        }

        String playerPath = "sprites/player.png";
        texPlayer = tryLoad(playerPath);
        if (texPlayer != null) {
            playerRegion = trimWholeTexture(playerPath, texPlayer);
        }
        texEnemy = tryLoad("sprites/enemy.png");
        texFood = tryLoad("sprites/food.png");
        texW1 = tryLoad("sprites/weapon_lv1.png");
        texW2 = tryLoad("sprites/weapon_lv2.png");

        // Only controls whether we attempt to draw textures at all.
        useTextures = heroSheet != null || texPlayer != null || texEnemy != null || texFood != null || texW1 != null || texW2 != null;
    }

    @SuppressWarnings("unchecked")
    private void initHeroAnimations(String sheetPath, Texture sheet) {
        // Default sheet layout based on the provided hero.png: 6 columns x 4 rows.
        int cols = 6;
        int rows = 4;
        int frameW = sheet.getWidth() / cols;
        int frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return;

        TextureRegion[][] grid = TextureRegion.split(sheet, frameW, frameH);

        // Trim transparent padding inside each frame so the character fills the drawn area.
        // This fixes cases where the sprite sheet cells include big empty margins.
        try {
            Pixmap pm = new Pixmap(Gdx.files.internal(sheetPath));
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    grid[r][c] = trimRegion(pm, sheet, grid[r][c]);
                }
            }
            pm.dispose();
        } catch (GdxRuntimeException ignored) {
        }

        heroWalk = (Animation<TextureRegion>[]) new Animation[4];
        heroWalk[dirIndex(Direction.DOWN)] = new Animation<>(0.10f, grid[0]);
        heroWalk[dirIndex(Direction.LEFT)] = new Animation<>(0.10f, grid[1]);
        heroWalk[dirIndex(Direction.RIGHT)] = new Animation<>(0.10f, grid[2]);
        heroWalk[dirIndex(Direction.UP)] = new Animation<>(0.10f, grid[3]);

        for (Animation<TextureRegion> a : heroWalk) {
            if (a != null) a.setPlayMode(Animation.PlayMode.LOOP);
        }
    }

    private int dirIndex(Direction d) {
        return switch (d) {
            case DOWN -> 0;
            case LEFT -> 1;
            case RIGHT -> 2;
            case UP -> 3;
        };
    }

    private Texture tryLoad(String internalPath) {
        try {
            if (!Gdx.files.internal(internalPath).exists()) return null;
            return new Texture(Gdx.files.internal(internalPath));
        } catch (GdxRuntimeException e) {
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

        // Keep our grid/render scale consistent with the TMX file.
        // This prevents “collision looks inverted/offset” issues when config fails to load or is stale.
        Integer mapTileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        if (mapTileWidth != null && mapTileWidth > 0) {
            tileSize = mapTileWidth;
        }

        // Render the player as exactly one tile (tileSize x tileSize).
        // (Movement/collision is still tile-based; this only changes rendering size.)
        if (player != null) {
            player.setSize(tileSize, tileSize);
        }

        stats.steps = 0;
        stats.enemiesDefeated = 0;
        stats.itemsCollected = 0;
        gameOver = false;
    }

    @Override
    public void render(float delta) {
        movedThisFrame = false;
        handleInput();
        updateCamera();

        if (heroSheet != null && heroWalk != null) {
            heroAnimTime = movedThisFrame ? (heroAnimTime + delta) : 0f;
        }

        ScreenUtils.clear(0.07f, 0.07f, 0.09f, 1);

        mapRenderer.setView(camera);
        mapRenderer.render();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (debugCollision) drawCollisionOverlay();
        drawObjects();
        drawEnemies();
        drawPlayer();
        batch.end();

        batch.setProjectionMatrix(hudCamera.combined);
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

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugCollision = !debugCollision;
            lastBlockedX = -1;
            lastBlockedY = -1;
        }

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
        if (!collision[nx][ny].walkable) {
            lastBlockedX = nx;
            lastBlockedY = ny;
            return;
        }

        player.setPos(nx, ny);
        facing = dir;
        movedThisFrame = true;
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
        if (!useTextures) return;

        float px = player.x() * tileSize;
        float py = player.y() * tileSize;

        int pw = player.width();
        int ph = player.height();

        if (heroSheet != null && heroWalk != null) {
            Animation<TextureRegion> anim = heroWalk[dirIndex(facing)];
            if (anim != null) {
                TextureRegion frame = anim.getKeyFrame(heroAnimTime);
                batch.draw(frame, px, py, pw, ph);
                return;
            }
        }

        if (playerRegion != null) {
            batch.draw(playerRegion, px, py, pw, ph);
        } else if (texPlayer != null) {
            batch.draw(texPlayer, px, py, pw, ph);
        }
    }

    private TextureRegion trimWholeTexture(String internalPath, Texture texture) {
        try {
            Pixmap pm = new Pixmap(Gdx.files.internal(internalPath));
            TextureRegion full = new TextureRegion(texture, 0, 0, pm.getWidth(), pm.getHeight());
            TextureRegion trimmed = trimRegion(pm, texture, full);
            pm.dispose();
            return trimmed;
        } catch (GdxRuntimeException e) {
            return new TextureRegion(texture);
        }
    }

    private TextureRegion trimRegion(Pixmap pm, Texture texture, TextureRegion base) {
        int srcX = base.getRegionX();
        int srcY = base.getRegionY();
        int srcW = base.getRegionWidth();
        int srcH = base.getRegionHeight();

        int minX = srcX + srcW;
        int minY = srcY + srcH;
        int maxX = srcX - 1;
        int maxY = srcY - 1;

        for (int y = srcY; y < srcY + srcH; y++) {
            for (int x = srcX; x < srcX + srcW; x++) {
                int pixel = pm.getPixel(x, y);
                int alpha = pixel & 0xFF;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) return base;

        int w = (maxX - minX) + 1;
        int h = (maxY - minY) + 1;
        return new TextureRegion(texture, minX, minY, w, h);
    }

    private void drawCollisionOverlay() {
        if (debugPixel == null || collision == null) return;

        Color prev = batch.getColor();
        batch.setColor(1f, 0f, 0f, 0.20f);
        for (int x = 0; x < collision.length; x++) {
            for (int y = 0; y < collision[0].length; y++) {
                if (!collision[x][y].walkable) {
                    batch.draw(debugPixel, x * tileSize, y * tileSize, tileSize, tileSize);
                }
            }
        }
        batch.setColor(prev);
    }

    private void drawHud() {
        // Black, larger, semi-bold HUD text in a top-right column.
        font.getData().setScale(1.4f);
        font.setColor(Color.BLACK);

        String weaponText = player.inventory().equippedWeapon()
                .map(w -> w.name() + " (L" + w.level() + ")")
                .orElse("None");

        String[] lines = new String[] {
                "HP: " + player.hp() + "/10",
                "Equipped: " + weaponText,
                "Steps: " + stats.steps,
                "Enemies defeated: " + stats.enemiesDefeated,
                "Items collected: " + stats.itemsCollected,
        };

        float padding = 18f;
        float lineGap = 10f;

        float maxWidth = 0f;
        for (String s : lines) {
            glyphLayout.setText(font, s);
            maxWidth = Math.max(maxWidth, glyphLayout.width);
        }

        float startX = Gdx.graphics.getWidth() - padding - maxWidth;
        float startY = Gdx.graphics.getHeight() - padding;

        float y = startY;
        for (String s : lines) {
            drawSemibold(font, s, startX, y);
            y -= (font.getLineHeight() + lineGap);
        }

        // Keep game-over message visible.
        if (gameOver) {
            font.getData().setScale(1.6f);
            font.setColor(Color.RED);
            drawSemibold(font, "GAME OVER - Press R to restart", 220, 360);
        }
    }

    private void drawSemibold(BitmapFont font, String text, float x, float y) {
        // Simple double-draw to simulate semi-bold.
        font.draw(batch, text, x + 1f, y);
        font.draw(batch, text, x, y);
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
        } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
            Gdx.app.error("CLOUD", "Download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null) tiledMap.dispose();
        if (debugPixel != null) debugPixel.dispose();
        if (texPlayer != null) texPlayer.dispose();
        if (heroSheet != null) heroSheet.dispose();
        if (texEnemy != null) texEnemy.dispose();
        if (texFood != null) texFood.dispose();
        if (texW1 != null) texW1.dispose();
        if (texW2 != null) texW2.dispose();
    }
}
