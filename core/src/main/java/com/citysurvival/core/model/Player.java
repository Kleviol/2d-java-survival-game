package com.citysurvival.core.model;

public class Player extends Entity {
    private int hp;
    private final int maxHp;
    private final Inventory inventory = new Inventory();

    private int width = 16;
    private int height = 16;

    public Player(int x, int y, int maxHp) {
        super(x, y);
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public Inventory inventory() { return inventory; }

    public int width() { return width; }
    public int height() { return height; }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void damage(int amount) { hp = Math.max(0, hp - amount); }
    public void heal(int amount) { hp = Math.min(maxHp, hp + amount); }
    public boolean isDead() { return hp <= 0; }
}
