package com.citysurvival.core.model;

public enum TileType {
    FLOOR(true),
    WALL(false);

    public final boolean walkable;
    TileType(boolean walkable) { this.walkable = walkable; }
}
