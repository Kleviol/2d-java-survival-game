package com.citysurvival.core.screens;

import java.io.IOException;
import java.util.Properties;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.citysurvival.core.CitySurvivalGame;

public class MainMenuScreen extends ScreenAdapter {
    private final CitySurvivalGame game;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final OrthographicCamera hudCamera = new OrthographicCamera();

    private Texture pixel;

    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;

    private String tmxMapPath = "maps/city1.tmx";
    private float cameraZoom = 0.5f;

    private float buttonX;
    private float buttonY;
    private float buttonW;
    private float buttonH;

    private final float leftMargin = 70f;

    public MainMenuScreen(CitySurvivalGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        loadGameProperties();
        ensurePixel();

        try {
            tiledMap = new TmxMapLoader().load(tmxMapPath);
            mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, 1f);
        } catch (GdxRuntimeException e) {
            tiledMap = null;
            mapRenderer = null;
        }

        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.zoom = cameraZoom;
        centerCameraOnMap();
        camera.update();

        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();

        layoutButton();

        font.getData().setScale(2.2f);
        font.setColor(Color.WHITE);
    }

    private void loadGameProperties() {
        try {
            Properties p = new Properties();
            p.load(Gdx.files.internal("config/game.properties").read());
            tmxMapPath = p.getProperty("tmxMap", tmxMapPath);
            cameraZoom = Float.parseFloat(p.getProperty("cameraZoom", Float.toString(cameraZoom)));
        } catch (IOException | NumberFormatException | GdxRuntimeException ignored) {
        }
    }

    private void ensurePixel() {
        if (pixel != null) return;
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    private void centerCameraOnMap() {
        if (tiledMap == null) {
            camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0f);
            return;
        }

        Integer tileW = tiledMap.getProperties().get("tilewidth", Integer.class);
        Integer tileH = tiledMap.getProperties().get("tileheight", Integer.class);
        Integer wTiles = tiledMap.getProperties().get("width", Integer.class);
        Integer hTiles = tiledMap.getProperties().get("height", Integer.class);

        if (tileW == null || tileH == null || wTiles == null || hTiles == null) {
            camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0f);
            return;
        }

        float mapPixelW = wTiles * tileW;
        float mapPixelH = hTiles * tileH;
        camera.position.set(mapPixelW / 2f, mapPixelH / 2f, 0f);
    }

    private void layoutButton() {
        float sh = Gdx.graphics.getHeight();

        buttonW = 260f;
        buttonH = 70f;
        buttonX = leftMargin;
        buttonY = sh / 2f - buttonH / 2f;
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.zoom = cameraZoom;
        centerCameraOnMap();
        camera.update();

        hudCamera.setToOrtho(false, width, height);
        hudCamera.update();

        layoutButton();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.07f, 0.07f, 0.09f, 1f);

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }

        if (Gdx.input.justTouched()) {
            float x = Gdx.input.getX();
            float y = Gdx.graphics.getHeight() - Gdx.input.getY();
            if (x >= buttonX && x <= buttonX + buttonW && y >= buttonY && y <= buttonY + buttonH) {
                game.setScreen(new GameScreen());
                return;
            }
        }

        if (mapRenderer != null && tiledMap != null) {
            mapRenderer.setView(camera);
            mapRenderer.render();
        }

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);

        Color prev = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(pixel, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setColor(prev);

        drawTitle();
        drawButton();
        drawControls();

        batch.end();
    }

    private void drawControls() {
        float prevScaleX = font.getData().scaleX;
        float prevScaleY = font.getData().scaleY;
        font.getData().setScale(1.25f);
        font.setColor(Color.WHITE);

        float x = leftMargin;
        float y = buttonY - 30f;
        float gap = 30f;

        String[] lines = new String[] {
                "Controls:",
                "Move: WASD / Arrow Keys",
                "Use Food: Enter",
                "Equip Weapon: 1 / 2",
                "Save: F5    Load: F9",
                "Upload: F6  Download: F10",
                "Collision Debug: F3",
                "Quit: ESC",
        };

        for (int i = 0; i < lines.length; i++) {
            font.setColor(0f, 0f, 0f, 0.75f);
            font.draw(batch, lines[i], x + 2f, (y - i * gap) - 2f);
            font.setColor(Color.WHITE);
            font.draw(batch, lines[i], x + 1f, y - i * gap);
            font.draw(batch, lines[i], x, y - i * gap);
        }

        font.getData().setScale(prevScaleX, prevScaleY);
    }

    private void drawTitle() {
        String title = "CITY SURVIVAL";
        float x = leftMargin;
        float y = Gdx.graphics.getHeight() - 90f;

        font.setColor(0f, 0f, 0f, 0.75f);
        font.draw(batch, title, x + 2f, y - 2f);
        font.setColor(Color.WHITE);
        font.draw(batch, title, x + 1f, y);
        font.draw(batch, title, x, y);
    }

    private void drawButton() {
        Color prev = batch.getColor();
        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(pixel, buttonX, buttonY, buttonW, buttonH);

        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(pixel, buttonX, buttonY, buttonW, 2f);
        batch.draw(pixel, buttonX, buttonY + buttonH - 2f, buttonW, 2f);
        batch.draw(pixel, buttonX, buttonY, 2f, buttonH);
        batch.draw(pixel, buttonX + buttonW - 2f, buttonY, 2f, buttonH);
        batch.setColor(prev);

        String label = "NEW GAME";
        GlyphLayout layout = new GlyphLayout(font, label);
        float tx = buttonX + (buttonW - layout.width) / 2f;
        float ty = buttonY + (buttonH + layout.height) / 2f + 6f;

        font.setColor(0f, 0f, 0f, 0.85f);
        font.draw(batch, label, tx + 2f, ty - 2f);
        font.setColor(Color.BLACK);
        font.draw(batch, label, tx + 1f, ty);
        font.draw(batch, label, tx, ty);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        if (pixel != null) pixel.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null) tiledMap.dispose();
    }
}
