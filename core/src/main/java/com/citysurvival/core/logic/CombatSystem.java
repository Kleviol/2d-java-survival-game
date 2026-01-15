package com.citysurvival.core.logic;

import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.Player;
import com.citysurvival.core.model.items.Weapon;

public class CombatSystem {

    public enum CombatResult {
        PLAYER_WINS,
        ENEMY_WINS,
        NO_WEAPON
    }

    public CombatResult fight(Player player, Enemy enemy) {
        Weapon enemyW = enemy.weapon();

        if (player.inventory().equippedWeapon().isEmpty()) {
            return CombatResult.NO_WEAPON;
        }

        Weapon playerW = player.inventory().equippedWeapon().get();

        if (playerW.level() >= enemyW.level()) return CombatResult.PLAYER_WINS;
        return CombatResult.ENEMY_WINS;
    }
}
