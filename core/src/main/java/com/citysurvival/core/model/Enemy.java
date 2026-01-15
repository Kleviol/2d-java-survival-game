package com.citysurvival.core.model;

import com.citysurvival.core.model.items.Weapon;

public class Enemy extends Entity {
    private final Weapon weapon;
    private final int kind;

    public Enemy(int x, int y, Weapon weapon) {
        this(x, y, weapon, weapon != null ? weapon.level() : 1);
    }

    public Enemy(int x, int y, Weapon weapon, int kind) {
        super(x, y);
        this.weapon = weapon;
        this.kind = (kind >= 2) ? 2 : 1;
    }

    public Weapon weapon() { return weapon; }

    public int kind() { return kind; }
}
