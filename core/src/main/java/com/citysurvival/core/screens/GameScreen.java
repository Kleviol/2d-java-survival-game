package com.citysurvival.core.screens;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
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

    private Texture texPlayer, texEnemy, texEnemy1, texEnemy2, texFood, texW1, texW2;
    private Texture heroSheet;
    private Texture debugPixel;
    private TextureRegion playerRegion;
    private TextureRegion enemy1Region;
    private TextureRegion enemy2Region;
    private TextureRegion enemyRegion;
    private Animation<TextureRegion>[] heroWalk;
    private float heroAnimTime = 0f;
    private boolean movedThisFrame = false;
    private Direction facing = Direction.DOWN;
    private boolean useTextures;

    private boolean debugCollision = false;

    private int tileSize = 32;
    private String tmxMapPath = "maps/city1.tmx";
    private String saveFile = "savegame.json";

    private boolean musicEnabled = true;
    private boolean sfxEnabled = true;
    private String musicPath = "audio/bgm.ogg";
    private String hitSfxPath = "audio/hit.wav";
    private String attackSfxPath = "audio/attack.wav";
    private float musicVolume = 0.55f;
    private float sfxVolume = 0.85f;
    private float attackSfxVolume = 0.85f;
    private Music bgm;
    private Sound hitSfx;
    private Sound attackSfx;

    private float cameraZoom = 0.5f;

    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;

    private TileType[][] collision;
    private Player player;
    private List<Enemy> enemies;
    private List<WorldObject> objects;

    private final GameStats stats = new GameStats();
    private boolean gameOver = false;

    private CloudSaveService cloudSave;
    private String cloudPlayerId;
    private int cloudSlot = 1;

    @Override
    public void show() {
        loadGameProperties();
        loadAssets();
        loadAudio();
        loadNewGameFromTmx();

        ensureDebugPixel();

        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.zoom = cameraZoom;
        camera.update();

        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();

        initSupabaseIfConfigured();

        startBackgroundMusicIfEnabled();
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

            musicEnabled = Boolean.parseBoolean(p.getProperty("musicEnabled", "true"));
            sfxEnabled = Boolean.parseBoolean(p.getProperty("sfxEnabled", "true"));
            musicPath = p.getProperty("musicPath", "audio/bgm.ogg");
            hitSfxPath = p.getProperty("hitSfxPath", "audio/hit.wav");
            attackSfxPath = p.getProperty("attackSfxPath", "audio/attack.wav");
            musicVolume = clamp01(parseFloatSafe(p.getProperty("musicVolume", "0.55"), 0.55f));
            sfxVolume = clamp01(parseFloatSafe(p.getProperty("sfxVolume", "0.85"), 0.85f));
            attackSfxVolume = clamp01(parseFloatSafe(p.getProperty("attackSfxVolume", "0.85"), 0.85f));
        } catch (IOException | NumberFormatException | GdxRuntimeException ignored) {
        }
    }

    private float parseFloatSafe(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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
        texEnemy1 = tryLoad("sprites/enemy/enemy1.png");
        if (texEnemy1 != null) {
            enemy1Region = pickEnemyFrameAndTrim("sprites/enemy/enemy1.png", texEnemy1);
        }
        texEnemy2 = tryLoad("sprites/enemy/enemy2.png");
        if (texEnemy2 != null) {
            enemy2Region = pickEnemyFrameAndTrim("sprites/enemy/enemy2.png", texEnemy2);
        }
        texEnemy = tryLoad("sprites/enemy.png");
        if (texEnemy != null) {
            enemyRegion = pickEnemyFrameAndTrim("sprites/enemy.png", texEnemy);
        }

        texFood = tryLoad("sprites/food/food.png");
        if (texFood == null) texFood = tryLoad("sprites/food.png");
        texW1 = tryLoad("sprites/weapons/weapon1.png");
        texW2 = tryLoad("sprites/weapons/weapon2.png");

        if (texW1 == null) texW1 = tryLoad("sprites/weapon_lv1.png");
        if (texW2 == null) texW2 = tryLoad("sprites/weapon_lv2.png");

        useTextures = heroSheet != null || texPlayer != null || texEnemy != null || texEnemy1 != null || texEnemy2 != null || texFood != null || texW1 != null || texW2 != null;
    }

    private TextureRegion pickEnemyFrameAndTrim(String internalPath, Texture texture) {
        try {
            Pixmap pm = new Pixmap(Gdx.files.internal(internalPath));

            int w = pm.getWidth();
            int h = pm.getHeight();

            TextureRegion base;
            if (w % 6 == 0 && h % 4 == 0) {
                base = new TextureRegion(texture, 0, 0, w / 6, h / 4);
            } else if (w % 3 == 0 && h % 4 == 0) {
                base = new TextureRegion(texture, 0, 0, w / 3, h / 4);
            } else if (w % 4 == 0 && h % 4 == 0) {
                base = new TextureRegion(texture, 0, 0, w / 4, h / 4);
            } else {
                base = new TextureRegion(texture);
            }

            TextureRegion trimmed = trimRegion(pm, texture, base);
            pm.dispose();
            return trimmed;
        } catch (GdxRuntimeException e) {
            return new TextureRegion(texture);
        }
    }

    @SuppressWarnings("unchecked")
    private void initHeroAnimations(String sheetPath, Texture sheet) {
        int cols = 6;
        int rows = 4;
        int frameW = sheet.getWidth() / cols;
        int frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return;

        TextureRegion[][] grid = TextureRegion.split(sheet, frameW, frameH);

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
            Texture t = new Texture(Gdx.files.internal(internalPath));
            t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return t;
        } catch (GdxRuntimeException e) {
            return null;
        }
    }

    private void loadAudio() {
        disposeAudio();

        if (musicEnabled) {
            bgm = tryLoadMusic(musicPath);
            if (bgm != null) {
                bgm.setLooping(true);
                bgm.setVolume(musicVolume);
            }
        }

        if (sfxEnabled) {
            hitSfx = tryLoadSound(hitSfxPath);
            attackSfx = tryLoadSound(attackSfxPath);
        }
    }

    private void startBackgroundMusicIfEnabled() {
        if (!musicEnabled || bgm == null) return;
        try {
            if (!bgm.isPlaying()) {
                bgm.play();
            }
        } catch (GdxRuntimeException ignored) {
        }
    }

    private void disposeAudio() {
        if (bgm != null) {
            try {
                bgm.stop();
            } catch (RuntimeException ignored) {
            }
            bgm.dispose();
            bgm = null;
        }
        if (hitSfx != null) {
            hitSfx.dispose();
            hitSfx = null;
        }
        if (attackSfx != null) {
            attackSfx.dispose();
            attackSfx = null;
        }
    }

    private Music tryLoadMusic(String internalPath) {
        try {
            if (internalPath == null || internalPath.isBlank()) return null;
            if (!Gdx.files.internal(internalPath).exists()) return null;
            return Gdx.audio.newMusic(Gdx.files.internal(internalPath));
        } catch (GdxRuntimeException e) {
            return null;
        }
    }

    private Sound tryLoadSound(String internalPath) {
        try {
            if (internalPath == null || internalPath.isBlank()) return null;
            if (!Gdx.files.internal(internalPath).exists()) return null;
            return Gdx.audio.newSound(Gdx.files.internal(internalPath));
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

        Integer mapTileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        if (mapTileWidth != null && mapTileWidth > 0) {
            tileSize = mapTileWidth;
        }

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
        batch.setColor(Color.WHITE);
        if (debugCollision) drawCollisionOverlay();
        drawObjects();
        drawEnemies();
        drawPlayer();
        batch.end();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        drawHud();
        batch.end();
    }

    private void handleInput() {
        if (gameOver) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) loadNewGameFromTmx();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
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
            facing = dir;
            stats.steps++;
            endTurn(false, true);
            if (player.isDead()) gameOver = true;
            return;
        }

        Enemy enemyAtTarget = findEnemyAt(nx, ny);
        if (enemyAtTarget != null) {
            playAttackSfx();
            CombatSystem.CombatResult result = combat.fight(player, enemyAtTarget);
            switch (result) {
                case PLAYER_WINS -> {
                    enemies.remove(enemyAtTarget);
                    stats.enemiesDefeated++;
                }
                case ENEMY_WINS, NO_WEAPON -> {
                    applyEnemyHit(3);
                    stats.steps++;
                    endTurn(true, false);

                    if (player.isDead()) gameOver = true;
                    return;
                }
            }
        }

        player.setPos(nx, ny);
        facing = dir;
        movedThisFrame = true;
        stats.steps++;

        pickupObjectsIfAny(nx, ny);
        endTurn(false, false);
        if (player.isDead()) gameOver = true;
    }

    private void endTurn(boolean alreadyDamagedThisTurn, boolean allowAdjacentAttackThisTurn) {
        enemyAI.moveEnemiesAfterPlayer(collision, enemies, player.x(), player.y());

        if (allowAdjacentAttackThisTurn && !alreadyDamagedThisTurn) {
            resolveAdjacentEnemyAttacks();
        }
        resolveCombatIfAny();
    }

    private void resolveAdjacentEnemyAttacks() {
        for (Enemy e : enemies) {
            int dist = Math.abs(e.x() - player.x()) + Math.abs(e.y() - player.y());
            if (dist != 1) continue;

            CombatSystem.CombatResult r = combat.fight(player, e);
            if (r != CombatSystem.CombatResult.PLAYER_WINS) {
                applyEnemyHit(3);
            }
            break;
        }
    }

    private void applyEnemyHit(int amount) {
        if (amount <= 0) return;
        player.damage(amount);
        if (!sfxEnabled || hitSfx == null) return;
        try {
            hitSfx.play(sfxVolume);
        } catch (GdxRuntimeException ignored) {
        }
    }

    private void playAttackSfx() {
        if (!sfxEnabled || attackSfx == null) return;
        try {
            attackSfx.play(attackSfxVolume);
        } catch (GdxRuntimeException ignored) {
        }
    }

    private Enemy findEnemyAt(int x, int y) {
        for (Enemy e : enemies) {
            if (e.x() == x && e.y() == y) return e;
        }
        return null;
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
                playAttackSfx();
                CombatSystem.CombatResult result = combat.fight(player, e);
                switch (result) {
                    case PLAYER_WINS -> {
                        it.remove();
                        stats.enemiesDefeated++;
                    }
                    case ENEMY_WINS -> applyEnemyHit(3);
                    case NO_WEAPON -> applyEnemyHit(3);
                }
                break;
            }
        }
    }

    @Override
    public void hide() {
        if (bgm != null) {
            try {
                bgm.stop();
            } catch (RuntimeException ignored) {
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
        if (!useTextures) return;
        for (Enemy e : enemies) {
            int kind = e.kind();

            batch.setColor(1f, 1f, 1f, 1f);

            TextureRegion r = (kind >= 2) ? enemy2Region : enemy1Region;
            if (r == null) r = enemyRegion;

            if (r != null) {
                batch.draw(r, e.x() * tileSize, e.y() * tileSize, tileSize, tileSize);
                continue;
            }

            Texture t = (kind >= 2) ? texEnemy2 : texEnemy1;
            if (t == null) t = texEnemy;
            if (t != null) batch.draw(t, e.x() * tileSize, e.y() * tileSize, tileSize, tileSize);
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
                int alphaLo = pixel & 0xFF;
                int alphaHi = (pixel >>> 24) & 0xFF;
                int alpha = Math.max(alphaLo, alphaHi);
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

        if (debugPixel != null) {
            float lineH = font.getLineHeight();
            float totalH = (lines.length * lineH) + ((lines.length - 1) * lineGap);
            float panelPadX = 14f;
            float panelPadY = 10f;
            float panelX = startX - panelPadX;
            float panelY = (startY - totalH) - panelPadY;
            float panelW = maxWidth + (panelPadX * 2f);
            float panelH = totalH + (panelPadY * 2f);

            Color prev = batch.getColor();
            batch.setColor(1f, 1f, 1f, 0.70f);
            batch.draw(debugPixel, panelX, panelY, panelW, panelH);

            batch.setColor(0f, 0f, 0f, 0.55f);
            float b = 2f;
            batch.draw(debugPixel, panelX, panelY, panelW, b);
            batch.draw(debugPixel, panelX, panelY + panelH - b, panelW, b);
            batch.draw(debugPixel, panelX, panelY, b, panelH);
            batch.draw(debugPixel, panelX + panelW - b, panelY, b, panelH);
            batch.setColor(prev);
        }

        float y = startY;
        for (String s : lines) {
            drawSemibold(font, s, startX, y);
            y -= (font.getLineHeight() + lineGap);
        }

        drawInventoryPanel();
        if (gameOver) {
            font.getData().setScale(1.6f);
            font.setColor(Color.RED);
            drawSemibold(font, "GAME OVER - Press R to restart", 220, 360);
        }
    }

    private void drawInventoryPanel() {
        if (debugPixel == null) return;

        float sw = Gdx.graphics.getWidth();
        float panelW = 360f;
        float panelH = 90f;
        float panelX = (sw - panelW) / 2f;
        float panelY = 18f;

        Color prev = batch.getColor();

        batch.setColor(1f, 1f, 1f, 0.70f);
        batch.draw(debugPixel, panelX, panelY, panelW, panelH);

        float slot = 64f;
        float gap = 18f;
        float slotsW = (slot * 3f) + (gap * 2f);
        float slotY = panelY + (panelH - slot) / 2f;
        float firstSlotX = panelX + (panelW - slotsW) / 2f;

        int foodCount = 0;
        for (com.citysurvival.core.model.items.Item it : player.inventory().items()) {
            if (it.type() == ItemType.FOOD) foodCount++;
        }

        boolean hasWeapon = player.inventory().equippedWeapon().isPresent();
        int foodStartSlot = hasWeapon ? 1 : 0;
        int remainingFood = foodCount;

        float prevScaleX = font.getData().scaleX;
        float prevScaleY = font.getData().scaleY;

        for (int i = 0; i < 3; i++) {
            float slotX = firstSlotX + i * (slot + gap);

            batch.setColor(0f, 0f, 0f, 0.75f);
            batch.draw(debugPixel, slotX, slotY, slot, 2f);
            batch.draw(debugPixel, slotX, slotY + slot - 2f, slot, 2f);
            batch.draw(debugPixel, slotX, slotY, 2f, slot);
            batch.draw(debugPixel, slotX + slot - 2f, slotY, 2f, slot);

            if (i == 0 && hasWeapon) {
                player.inventory().equippedWeapon().ifPresent(w -> {
                    Texture t = (w.level() == 1) ? texW1 : texW2;
                    if (t != null) {
                        batch.setColor(1f, 1f, 1f, 1f);
                        float pad = 6f;
                        batch.draw(t, slotX + pad, slotY + pad, slot - 2 * pad, slot - 2 * pad);
                    }
                });
            } else if (i >= foodStartSlot && remainingFood > 0) {
                int inThisSlot = Math.min(3, remainingFood);
                remainingFood -= inThisSlot;

                if (texFood != null) {
                    batch.setColor(1f, 1f, 1f, 1f);
                    float pad = 6f;
                    batch.draw(texFood, slotX + pad, slotY + pad, slot - 2 * pad, slot - 2 * pad);
                }

                font.getData().setScale(1.25f);
                String countText = "x" + inThisSlot;
                glyphLayout.setText(font, countText);
                float inset = 8f;
                float tx = slotX + slot - inset - glyphLayout.width;
                float ty = slotY + 20f;
                drawOutlined(font, countText, tx, ty, Color.WHITE, Color.BLACK);
            }
        }

        font.getData().setScale(prevScaleX, prevScaleY);

        batch.setColor(prev);
    }

    private void drawOutlined(BitmapFont font, String text, float x, float y, Color fill, Color outline) {
        Color prev = font.getColor();

        font.setColor(outline);
        font.draw(batch, text, x - 1f, y);
        font.draw(batch, text, x + 1f, y);
        font.draw(batch, text, x, y - 1f);
        font.draw(batch, text, x, y + 1f);
        font.draw(batch, text, x - 1f, y - 1f);
        font.draw(batch, text, x + 1f, y - 1f);
        font.draw(batch, text, x - 1f, y + 1f);
        font.draw(batch, text, x + 1f, y + 1f);

        font.setColor(fill);
        font.draw(batch, text, x + 1f, y);
        font.draw(batch, text, x, y);

        font.setColor(prev);
    }

    private void drawSemibold(BitmapFont font, String text, float x, float y) {
        font.draw(batch, text, x + 1f, y);
        font.draw(batch, text, x, y);
    }

    private void saveLocal() {
        SaveGameService.SaveState state = SaveGameService.buildState(tmxMapPath, player, enemies, objects, stats);
        saveGame.saveLocal(saveFile, state);
        Gdx.app.log("SAVE", "Saved to " + saveFile);
    }

    private void applyLoadedState(SaveGameService.SaveState s) {
        if (s == null) return;

        if (s.mapPath != null && !s.mapPath.isBlank() && !s.mapPath.equals(tmxMapPath)) {
            tmxMapPath = s.mapPath;
            loadNewGameFromTmx();
        }

        player = new Player(s.playerX, s.playerY, 10);
        if (s.playerHp < 10) player.damage(10 - s.playerHp);
        player.setSize(tileSize, tileSize);

        if (s.inventory != null) {
            for (SaveGameService.SavedItem si : s.inventory) {
                if ("FOOD".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Food(si.name, si.healAmount));
                if ("WEAPON".equals(si.type)) player.inventory().add(new com.citysurvival.core.model.items.Weapon(si.name, si.weaponLevel));
            }
        }
        if (s.equippedWeaponLevel > 0) player.inventory().equipWeaponLevel(s.equippedWeaponLevel);

        if (enemies == null) enemies = new java.util.ArrayList<>();
        enemies.clear();
        if (s.enemies != null) {
            for (SaveGameService.SavedEnemy se : s.enemies) {
                int kind = (se.weaponLevel >= 2) ? 2 : 1;
                enemies.add(new Enemy(se.x, se.y, new com.citysurvival.core.model.items.Weapon("Enemy Weapon L" + se.weaponLevel, se.weaponLevel), kind));
            }
        }

        if (objects == null) objects = new java.util.ArrayList<>();
        objects.clear();
        if (s.objects != null) {
            for (SaveGameService.SavedObject so : s.objects) {
                if ("FOOD".equals(so.type)) {
                    objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Food(so.name, so.healAmount)));
                } else if ("WEAPON".equals(so.type)) {
                    objects.add(new WorldObject(so.x, so.y, new com.citysurvival.core.model.items.Weapon(so.name, so.weaponLevel)));
                }
            }
        }

        stats.steps = s.steps;
        stats.enemiesDefeated = s.enemiesDefeated;
        stats.itemsCollected = s.itemsCollected;

        gameOver = player.isDead();
    }

    private void loadLocal() {
        SaveGameService.SaveState s = saveGame.loadLocal(saveFile);
        if (s == null) {
            Gdx.app.log("SAVE", "No local save found.");
            return;
        }

        applyLoadedState(s);
        Gdx.app.log("SAVE", "Loaded from " + saveFile);
    }

    private void uploadCloud() {
        if (cloudSave == null) {
            Gdx.app.log("CLOUD", "Supabase not configured. Add desktop/src/main/resources/supabase.properties");
            return;
        }
        try {
            SaveGameService.SaveState state = SaveGameService.buildState(tmxMapPath, player, enemies, objects, stats);
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

            applyLoadedState(s);
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
        disposeAudio();
        if (texPlayer != null) texPlayer.dispose();
        if (heroSheet != null) heroSheet.dispose();
        if (texEnemy != null) texEnemy.dispose();
        if (texEnemy1 != null) texEnemy1.dispose();
        if (texEnemy2 != null) texEnemy2.dispose();
        if (texFood != null) texFood.dispose();
        if (texW1 != null) texW1.dispose();
        if (texW2 != null) texW2.dispose();
    }
}
