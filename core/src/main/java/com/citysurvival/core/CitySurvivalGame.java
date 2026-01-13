package com.citysurvival.core;

import com.badlogic.gdx.Game;
import com.citysurvival.core.screens.MainMenuScreen;

public class CitySurvivalGame extends Game {
    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }
}
