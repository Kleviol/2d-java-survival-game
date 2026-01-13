package com.citysurvival.core.logic;

import com.citysurvival.core.model.Direction;
import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.TileType;

import java.util.List;
import java.util.Random;

public class EnemyAISystem {
    private final Random rng = new Random();

    public void moveEnemiesAfterPlayer(TileType[][] collision, List<Enemy> enemies) {
        for (Enemy e : enemies) {
            Direction dir = Direction.values()[rng.nextInt(Direction.values().length)];
            int nx = e.x() + dir.dx;
            int ny = e.y() + dir.dy;

            if (!inBounds(collision, nx, ny)) continue;
            if (!collision[nx][ny].walkable) continue;
            if (occupiedByEnemy(enemies, nx, ny, e)) continue;

            e.setPos(nx, ny);
        }
    }

    private boolean inBounds(TileType[][] tiles, int x, int y) {
        return x >= 0 && y >= 0 && x < tiles.length && y < tiles[0].length;
    }

    private boolean occupiedByEnemy(List<Enemy> enemies, int x, int y, Enemy self) {
        for (Enemy e : enemies) {
            if (e == self) continue;
            if (e.x() == x && e.y() == y) return true;
        }
        return false;
    }
}
