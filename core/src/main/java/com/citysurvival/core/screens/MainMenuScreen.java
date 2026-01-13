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

    // Button bounds in HUD (screen) coordinates
    private float buttonX;
    private float buttonY;
    private float buttonW;
    private float buttonH;

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

        // Slightly bigger menu text.
        font.getData().setScale(1.8f);
        font.setColor(Color.BLACK);
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
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();

        buttonW = 260f;
        buttonH = 70f;
        buttonX = (sw - buttonW) / 2f;
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

        // HUD overlay + menu UI
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();

        // Semi-transparent dim over the map.
        Color prev = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(pixel, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setColor(prev);

        drawTitle();
        drawButton();

        batch.end();
    }

    private void drawTitle() {
        String title = "CITY SURVIVAL";
        GlyphLayout layout = new GlyphLayout(font, title);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        float y = Gdx.graphics.getHeight() - 90f;

        // Fake semibold
        font.setColor(Color.BLACK);
        font.draw(batch, title, x + 1, y);
        font.draw(batch, title, x, y);
    }

    private void drawButton() {
        // Button background
        Color prev = batch.getColor();
        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(pixel, buttonX, buttonY, buttonW, buttonH);

        // Simple border
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

        // Fake semibold
        font.setColor(Color.BLACK);
        font.draw(batch, label, tx + 1, ty);
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
