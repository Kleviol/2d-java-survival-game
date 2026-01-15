package com.citysurvival.core.io;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.GameStats;
import com.citysurvival.core.model.Player;
import com.citysurvival.core.model.WorldObject;
import com.citysurvival.core.model.items.Food;
import com.citysurvival.core.model.items.Item;
import com.citysurvival.core.model.items.ItemType;
import com.citysurvival.core.model.items.Weapon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SaveGameService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void saveLocal(String fileName, SaveState state) {
        FileHandle fh = Gdx.files.local(fileName);
        fh.writeString(gson.toJson(state), false);
    }

    public SaveState loadLocal(String fileName) {
        FileHandle fh = Gdx.files.local(fileName);
        if (!fh.exists()) return null;
        return gson.fromJson(fh.readString(), SaveState.class);
    }

    public String toJson(SaveState state) { return gson.toJson(state); }

    public SaveState fromJson(String json) { return gson.fromJson(json, SaveState.class); }

    public static SaveState buildState(Player player, List<Enemy> enemies, List<WorldObject> objects, GameStats stats) {
        return buildState(null, player, enemies, objects, stats);
    }

    public static SaveState buildState(String mapPath, Player player, List<Enemy> enemies, List<WorldObject> objects, GameStats stats) {
        SaveState s = new SaveState();
        s.mapPath = mapPath;
        s.playerX = player.x();
        s.playerY = player.y();
        s.playerHp = player.hp();

        s.inventory = new ArrayList<>();
        for (Item it : player.inventory().items()) {
            s.inventory.add(SavedItem.from(it));
        }
        s.equippedWeaponLevel = player.inventory().equippedWeapon().map(Weapon::level).orElse(0);

        s.enemies = new ArrayList<>();
        for (Enemy e : enemies) {
            SavedEnemy se = new SavedEnemy();
            se.x = e.x();
            se.y = e.y();
            se.weaponLevel = e.weapon().level();
            s.enemies.add(se);
        }

        s.objects = new ArrayList<>();
        for (WorldObject o : objects) {
            SavedObject so = new SavedObject();
            so.x = o.x;
            so.y = o.y;
            so.type = o.item.type().name();
            so.name = o.item.name();
            if (o.item.type() == ItemType.WEAPON) so.weaponLevel = ((Weapon) o.item).level();
            if (o.item.type() == ItemType.FOOD) so.healAmount = ((Food) o.item).healAmount();
            s.objects.add(so);
        }

        s.steps = stats.steps;
        s.enemiesDefeated = stats.enemiesDefeated;
        s.itemsCollected = stats.itemsCollected;

        return s;
    }

    public static class SaveState {
        public String mapPath;

        public int playerX;
        public int playerY;
        public int playerHp;

        public List<SavedItem> inventory;
        public int equippedWeaponLevel;

        public List<SavedEnemy> enemies;
        public List<SavedObject> objects;

        public int steps;
        public int enemiesDefeated;
        public int itemsCollected;
    }

    public static class SavedEnemy {
        public int x;
        public int y;
        public int weaponLevel;
    }

    public static class SavedObject {
        public int x;
        public int y;
        public String type;
        public String name;
        public int weaponLevel;
        public int healAmount;
    }

    public static class SavedItem {
        public String type;
        public String name;
        public int weaponLevel;
        public int healAmount;

        public static SavedItem from(Item it) {
            SavedItem si = new SavedItem();
            si.type = it.type().name();
            si.name = it.name();
            if (it.type() == ItemType.WEAPON) si.weaponLevel = ((Weapon) it).level();
            if (it.type() == ItemType.FOOD) si.healAmount = ((Food) it).healAmount();
            return si;
        }
    }
}
