package com.citysurvival.core.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import com.citysurvival.core.model.Direction;
import com.citysurvival.core.model.Enemy;
import com.citysurvival.core.model.TileType;

public class EnemyAISystem {
    private final Random rng = new Random();

    public void moveEnemiesAfterPlayer(TileType[][] collision, List<Enemy> enemies) {
        for (Enemy e : enemies) {
            tryMoveRandom(collision, enemies, e);
        }
    }

    public void moveEnemiesAfterPlayer(TileType[][] collision, List<Enemy> enemies, int playerX, int playerY) {
        final int followRange = 6;

        for (Enemy e : enemies) {
            int dist = Math.abs(playerX - e.x()) + Math.abs(playerY - e.y());

            if (dist <= followRange) {
                Direction next = findNextStepTowardPlayer(collision, enemies, e, playerX, playerY);
                if (next != null && tryMove(collision, enemies, e, next, playerX, playerY)) {
                    continue;
                }
                tryMoveRandom(collision, enemies, e, playerX, playerY);
            } else {
                tryMoveRandom(collision, enemies, e, playerX, playerY);
            }
        }
    }

    private boolean tryMove(TileType[][] collision, List<Enemy> enemies, Enemy e, Direction dir, int playerX, int playerY) {
        int nx = e.x() + dir.dx;
        int ny = e.y() + dir.dy;

        if (nx == playerX && ny == playerY) return false;

        if (!inBounds(collision, nx, ny)) return false;
        if (!collision[nx][ny].walkable) return false;
        if (occupiedByEnemy(enemies, nx, ny, e)) return false;

        e.setPos(nx, ny);
        return true;
    }

    private void tryMoveRandom(TileType[][] collision, List<Enemy> enemies, Enemy e) {
        Direction dir = Direction.values()[rng.nextInt(Direction.values().length)];
        tryMove(collision, enemies, e, dir);
    }

    private void tryMoveRandom(TileType[][] collision, List<Enemy> enemies, Enemy e, int playerX, int playerY) {
        Direction dir = Direction.values()[rng.nextInt(Direction.values().length)];
        tryMove(collision, enemies, e, dir, playerX, playerY);
    }

    private Direction findNextStepTowardPlayer(TileType[][] collision, List<Enemy> enemies, Enemy enemy, int playerX, int playerY) {
        int width = collision.length;
        int height = collision[0].length;

        List<int[]> targets = new ArrayList<>(4);
        for (Direction d : Direction.values()) {
            int tx = playerX + d.dx;
            int ty = playerY + d.dy;
            if (!inBounds(collision, tx, ty)) continue;
            if (!collision[tx][ty].walkable) continue;
            if (occupiedByEnemy(enemies, tx, ty, enemy)) continue;
            targets.add(new int[] { tx, ty });
        }
        if (targets.isEmpty()) return null;

        boolean[][] visited = new boolean[width][height];
        int[][] prevX = new int[width][height];
        int[][] prevY = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                prevX[x][y] = -1;
                prevY[x][y] = -1;
            }
        }

        int sx = enemy.x();
        int sy = enemy.y();

        Deque<int[]> q = new ArrayDeque<>();
        visited[sx][sy] = true;
        q.addLast(new int[] { sx, sy });

        int foundX = -1;
        int foundY = -1;

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cx = cur[0];
            int cy = cur[1];

            for (int[] t : targets) {
                if (t[0] == cx && t[1] == cy) {
                    foundX = cx;
                    foundY = cy;
                    q.clear();
                    break;
                }
            }
            if (foundX != -1) break;

            for (Direction d : Direction.values()) {
                int nx = cx + d.dx;
                int ny = cy + d.dy;

                if (!inBounds(collision, nx, ny)) continue;
                if (visited[nx][ny]) continue;
                if (!collision[nx][ny].walkable) continue;
                if (nx == playerX && ny == playerY) continue;
                if (occupiedByEnemy(enemies, nx, ny, enemy)) continue;

                visited[nx][ny] = true;
                prevX[nx][ny] = cx;
                prevY[nx][ny] = cy;
                q.addLast(new int[] { nx, ny });
            }
        }

        if (foundX == -1) return null;

        int cx = foundX;
        int cy = foundY;
        while (true) {
            int px = prevX[cx][cy];
            int py = prevY[cx][cy];
            if (px == -1 || py == -1) break;
            if (px == sx && py == sy) break;
            cx = px;
            cy = py;
        }

        int dx = cx - sx;
        int dy = cy - sy;
        for (Direction d : Direction.values()) {
            if (d.dx == dx && d.dy == dy) return d;
        }
        return null;
    }

    private boolean tryMove(TileType[][] collision, List<Enemy> enemies, Enemy e, Direction dir) {
        int nx = e.x() + dir.dx;
        int ny = e.y() + dir.dy;

        if (!inBounds(collision, nx, ny)) return false;
        if (!collision[nx][ny].walkable) return false;
        if (occupiedByEnemy(enemies, nx, ny, e)) return false;

        e.setPos(nx, ny);
        return true;
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
