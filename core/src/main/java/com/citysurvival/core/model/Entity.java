package com.citysurvival.core.model;

public abstract class Entity {
    protected int x;
    protected int y;

    protected Entity(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }
    public void setPos(int x, int y) { this.x = x; this.y = y; }
}
