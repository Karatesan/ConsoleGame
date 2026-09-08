package com.archon.model;

/**
 * Encapsulates the spatial 2D grid of tiles, boundary validation, and tile mutation.
 */
public final class GameMap {
    public final int w, h;
    private final World.Tile[][] tiles;

    public GameMap(int w, int h) {
        this.w = w;
        this.h = h;
        this.tiles = new World.Tile[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                tiles[y][x] = new World.Tile();
            }
        }
    }

    public boolean inBounds(Vec2 p) {
        return p != null && p.x() >= 0 && p.y() >= 0 && p.x() < w && p.y() < h;
    }

    public World.Tile tile(Vec2 p) {
        return inBounds(p) ? tiles[p.y()][p.x()] : null;
    }

    public void wall(int x, int y) {
        if (x >= 0 && x < w && y >= 0 && y < h) {
            tiles[y][x].wall = true;
        }
    }
}
