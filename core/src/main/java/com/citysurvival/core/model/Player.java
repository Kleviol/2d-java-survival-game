package com.citysurvival.core.model;

public class Player extends Entity {
    private int hp;
    private final int maxHp;
    private final Inventory inventory = new Inventory();

    public Player(int x, int y, int maxHp) {
        super(x, y);
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public Inventory inventory() { return inventory; }

    public void damage(int amount) { hp = Math.max(0, hp - amount); }
    public void heal(int amount) { hp = Math.min(maxHp, hp + amount); }
    public boolean isDead() { return hp <= 0; }
}
