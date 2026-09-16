package com.archon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates the spatial 2D grid of tiles, boundary validation, and tile mutation.
 */
public final class GameMap {
    private final int width;
    private final int height;
    private final Tile[][] tiles;

    public GameMap(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Map dimensions must be positive");
        }

        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new Tile();
            }
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(Vec2 position) {
        return position != null
                && position.x() >= 0
                && position.y() >= 0
                && position.x() < width
                && position.y() < height;
    }

    public Tile tile(Vec2 position) {
        return inBounds(position) ? tiles[position.y()][position.x()] : null;
    }

    public void setWall(Vec2 position, boolean wall) {
        requireTile(position).wall = wall;
    }

    public void addTag(Vec2 position, Tag tag) {
        requireTile(position).tags.add(Objects.requireNonNull(tag, "tag"));
    }

    public boolean removeTag(Vec2 position, Tag tag) {
        return requireTile(position).tags.remove(Objects.requireNonNull(tag, "tag"));
    }

    public void addCeilingTag(Vec2 position, Tag tag) {
        requireTile(position).ceiling.add(Objects.requireNonNull(tag, "tag"));
    }

    public boolean removeCeilingTag(Vec2 position, Tag tag) {
        return requireTile(position).ceiling.remove(Objects.requireNonNull(tag, "tag"));
    }

    public void placeGroundItem(Vec2 position, Item item) {
        requireTile(position).ground.add(Objects.requireNonNull(item, "item"));
    }

    public boolean removeGroundItem(Vec2 position, Item item) {
        return requireTile(position).ground.remove(Objects.requireNonNull(item, "item"));
    }

    public Item removeFirstGroundItem(Vec2 position) {
        Tile tile = requireTile(position);
        return tile.ground.isEmpty() ? null : tile.ground.remove(0);
    }

    private Tile requireTile(Vec2 position) {
        if (!inBounds(position)) {
            throw new IllegalArgumentException("Position is outside the map: " + position);
        }
        return tiles[position.y()][position.x()];
    }

    public static final class Tile {
        private boolean wall;
        private final EnumSet<Tag> tags;
        private final EnumSet<Tag> ceiling;
        private final ArrayList<Item> ground;
        private final Set<Tag> tagsView;
        private final Set<Tag> ceilingView;
        private final List<Item> groundView;

        private Tile() {
            tags = EnumSet.noneOf(Tag.class);
            ceiling = EnumSet.noneOf(Tag.class);
            ground = new ArrayList<>();
            tagsView = Collections.unmodifiableSet(tags);
            ceilingView = Collections.unmodifiableSet(ceiling);
            groundView = Collections.unmodifiableList(ground);
        }

        public boolean isWall() {
            return wall;
        }

        public boolean has(Tag tag) {
            return tags.contains(Objects.requireNonNull(tag, "tag"));
        }

        public Set<Tag> tags() {
            return tagsView;
        }

        public Set<Tag> ceiling() {
            return ceilingView;
        }

        public List<Item> ground() {
            return groundView;
        }
    }
}