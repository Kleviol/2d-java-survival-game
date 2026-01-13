package com.citysurvival.core.model.items;

import java.util.UUID;

public class Food implements Item {
    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final int healAmount;

    public Food(String name, int healAmount) {
        this.name = name;
        this.healAmount = healAmount;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public ItemType type() { return ItemType.FOOD; }

    public int healAmount() { return healAmount; }
}
