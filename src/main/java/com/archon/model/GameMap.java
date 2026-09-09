package com.archon.model;

/**
 * Encapsulates the spatial 2D grid of tiles, boundary validation, and tile mutation.
 */
public final class GameMap {
    public final int width, height;
    private final World.Tile[][] tiles;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new World.Tile[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new World.Tile();
            }
        }
    }

    public boolean inBounds(Vec2 p) {
        return p != null && p.x() >= 0 && p.y() >= 0 && p.x() < width && p.y() < height;
    }

    public World.Tile tile(Vec2 p) {
        return inBounds(p) ? tiles[p.y()][p.x()] : null;
    }

    public void wall(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            tiles[y][x].wall = true;
        }
    }
}
