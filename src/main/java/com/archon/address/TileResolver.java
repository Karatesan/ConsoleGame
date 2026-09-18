package com.archon.address;

import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TileResolver {
    private static final Pattern COORDINATES =
            Pattern.compile("([+-]?\\d+)\\s*,\\s*([+-]?\\d+)");

    private static final Pattern RELATIVE =
            Pattern.compile("(ne|nw|se|sw|n|s|e|w)(\\d+)?", Pattern.CASE_INSENSITIVE);

    private TileResolver() { }

    static Resolved resolve(Address.TileAddr address, World world) {
        String spec = address.spec();

        try {
            if (spec.contains(",")) {
                Matcher coordinates = COORDINATES.matcher(spec.trim());
                if (!coordinates.matches()) {
                    return invalid("invalid coordinates \"" + spec + "\"; use @x,y");
                }

                return bounded(
                        Integer.parseInt(coordinates.group(1)),
                        Integer.parseInt(coordinates.group(2)),
                        address.layer(),
                        world
                );
            }

            Matcher relative = RELATIVE.matcher(spec);
            if (relative.matches()) {
                Vec2 direction = Vec2.dir(relative.group(1));
                int distance = relative.group(2) == null
                        ? 1
                        : Integer.parseInt(relative.group(2));
                Vec2 origin = world.thrall().pos();

                return bounded(
                        origin.x() + (long) direction.x() * distance,
                        origin.y() + (long) direction.y() * distance,
                        address.layer(),
                        world
                );
            }
        } catch (NumberFormatException exception) {
            return invalid("tile coordinate or distance is too large in \"" + spec + "\"");
        }

        Entity anchor = spec.equalsIgnoreCase("self")
                ? world.thrall()
                : world.get(spec);

        if (anchor == null) {
            return new Resolved.Failure(
                    Resolved.Problem.UNKNOWN_ENTITY,
                    "unknown tile reference \"" + spec
                            + "\"; use @x,y, @self, @e2, or @entityId"
            );
        }
        if (!anchor.alive()) {
            return new Resolved.Failure(
                    Resolved.Problem.DEAD_ENTITY,
                    anchor.id() + " is no longer alive"
            );
        }

        return bounded(
                anchor.pos().x(),
                anchor.pos().y(),
                address.layer(),
                world
        );
    }

    private static Resolved bounded(long x, long y, Address.Layer layer, World world) {
        if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) {
            return new Resolved.Failure(
                    Resolved.Problem.OUT_OF_BOUNDS,
                    "tile (" + x + "," + y + ") is outside the map"
            );
        }

        return new Resolved.OnTile(new Vec2((int) x, (int) y), layer);
    }

    private static Resolved invalid(String reason) {
        return new Resolved.Failure(Resolved.Problem.INVALID_TILE, reason);
    }
}