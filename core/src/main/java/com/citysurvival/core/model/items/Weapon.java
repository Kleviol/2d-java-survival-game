package com.citysurvival.core.model.items;

import java.util.UUID;

public class Weapon implements Item {
    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final int level;

    public Weapon(String name, int level) {
        this.name = name;
        this.level = level;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public ItemType type() { return ItemType.WEAPON; }

    public int level() { return level; }
}
