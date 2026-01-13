package com.citysurvival.core.model;

import com.citysurvival.core.model.items.Food;
import com.citysurvival.core.model.items.Item;
import com.citysurvival.core.model.items.ItemType;
import com.citysurvival.core.model.items.Weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Inventory {
    private final List<Item> items = new ArrayList<>();
    private Weapon equippedWeapon;

    public void add(Item item) {
        items.add(item);
        if (item.type() == ItemType.WEAPON) {
            equipIfStronger((Weapon) item);
        }
    }

    public List<Item> items() { return List.copyOf(items); }

    public Optional<Weapon> equippedWeapon() { return Optional.ofNullable(equippedWeapon); }

    public void equip(Weapon weapon) { this.equippedWeapon = weapon; }

    public void equipIfStronger(Weapon weapon) {
        if (equippedWeapon == null || weapon.level() > equippedWeapon.level()) {
            equippedWeapon = weapon;
        }
    }

    public boolean useFirstFood(Player player) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.type() == ItemType.FOOD) {
                Food f = (Food) it;
                player.heal(f.healAmount());
                items.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean equipWeaponLevel(int level) {
        for (Item it : items) {
            if (it.type() == ItemType.WEAPON) {
                Weapon w = (Weapon) it;
                if (w.level() == level) {
                    equip(w);
                    return true;
                }
            }
        }
        return false;
    }
}
