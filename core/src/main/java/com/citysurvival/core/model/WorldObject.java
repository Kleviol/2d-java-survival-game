package com.citysurvival.core.model;

import com.citysurvival.core.model.items.Item;

public class WorldObject {
    public final int x;
    public final int y;
    public final Item item;

    public WorldObject(int x, int y, Item item) {
        this.x = x;
        this.y = y;
        this.item = item;
    }
}
