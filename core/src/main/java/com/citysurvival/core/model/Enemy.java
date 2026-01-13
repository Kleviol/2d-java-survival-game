package com.citysurvival.core.model;

import com.citysurvival.core.model.items.Weapon;

public class Enemy extends Entity {
    private final Weapon weapon;

    public Enemy(int x, int y, Weapon weapon) {
        super(x, y);
        this.weapon = weapon;
    }

    public Weapon weapon() { return weapon; }
}
