package com.citysurvival.core;

import com.badlogic.gdx.Game;
import com.citysurvival.core.screens.GameScreen;

public class CitySurvivalGame extends Game {
    @Override
    public void create() {
        setScreen(new GameScreen());
    }
}
