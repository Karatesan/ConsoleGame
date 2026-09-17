
package com.archon.address;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.archon.model.Actor;
import com.archon.model.BodyPart;
import com.archon.model.Entity;
import com.archon.model.EquipmentSlot;
import com.archon.model.Item;
import com.archon.model.Prop;
import com.archon.model.Vec2;
import com.archon.model.World;

public sealed interface Resolved permits Resolved.EntityTarget, Resolved.BodyTarget,
    Resolved.PackRoot, Resolved.PackedItem, Resolved.EquippedItem,
            Resolved.EmptyEquipmentSlot, Resolved.PropContents,
            Resolved.EmptyPropContents, Resolved.TileTarget {

  Pattern COORDINATE_PATTERN = Pattern.compile("^([+-]?\\d+)\\s*,\\s*([+-]?\\d+)$");
  Pattern DIRECTION_PATTERN = Pattern.compile(
                  "^([A-Za-z]+)(?:(?:\\s+|\\s*[/,:]\\s*)(\\S+))?$");

  record EntityTarget(Entity entity) implements Resolved {
    public EntityTarget {
      entity = Objects.requireNonNull(entity, "entity");
    }
  }

  record BodyTarget(Entity entity, BodyPart part) implements Resolved {
    public BodyTarget {
      entity = Objects.requireNonNull(entity, "entity");
      part = Objects.requireNonNull(part, "part");
    }
  }

  record PackRoot(Actor actor) implements Resolved {
    public PackRoot {
      actor = Objects.requireNonNull(actor, "actor");
    }
  }

  record PackedItem(Actor actor, Item item) implements Resolved {
    public PackedItem {
      actor = Objects.requireNonNull(actor, "actor");
      item = Objects.requireNonNull(item, "item");
    }
  }

  record EquippedItem(Actor actor, EquipmentSlot slot, Item item) implements Resolved {
    public EquippedItem {
      actor = Objects.requireNonNull(actor, "actor");
      slot = Objects.requireNonNull(slot, "slot");
      item = Objects.requireNonNull(item, "item");
    }
  }

  record EmptyEquipmentSlot(Actor actor, EquipmentSlot slot) implements Resolved {
    public EmptyEquipmentSlot {
      actor = Objects.requireNonNull(actor, "actor");
      slot = Objects.requireNonNull(slot, "slot");
    }
  }

  record PropContents(Prop prop, Item item) implements Resolved {
    public PropContents {
      prop = Objects.requireNonNull(prop, "prop");
      item = Objects.requireNonNull(item, "item");
    }
  }

  record EmptyPropContents(Prop prop) implements Resolved {
    public EmptyPropContents {
      prop = Objects.requireNonNull(prop, "prop");
    }
  }

  record TileTarget(Vec2 pos, Address.Layer layer) implements Resolved {
    public TileTarget {
      pos = Objects.requireNonNull(pos, "pos");
      layer = Objects.requireNonNull(layer, "layer");
    }
  }

  sealed interface Resolution permits Resolution.Success, Resolution.Failure {
    record Success(Resolved target) implements Resolution {
      public Success {
        target = Objects.requireNonNull(target, "target");
      }
    }

    enum Failure implements Resolution {
      INVALID_TILE_SPEC,
                          UNKNOWN_ENTITY,
                          DEAD_ENTITY,
                          OUT_OF_BOUNDS,
                          INVALID_PATH
    }
  }

  static Resolution resolve(Address address, World world) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(world, "world");

    return switch (address) {
      case Address.TileAddr tile -> resolveTile(tile, world);
      case Address.InventoryAddr inventory -> resolveInventory(world.thrall(), inventory.path());
      case Address.EntityAddr entityAddress -> resolveEntity(entityAddress, world);
    };
  }

  private static Resolution resolveTile(Address.TileAddr address, World world) {
    Vec2 pos = resolveTilePosition(address.spec(), world);
    if (pos == null) {
      return Resolution.Failure.INVALID_TILE_SPEC;
    }

    return world.inBounds(pos)
        ? success(new TileTarget(pos, address.layer()))
        : Resolution.Failure.OUT_OF_BOUNDS;
  }

  private static Vec2 resolveTilePosition(String spec, World world) {
    if (spec == null) {
      return null;
    }

    String value = spec.trim();
    if (value.isEmpty()) {
      return null;
    }

    if (value.equalsIgnoreCase("self")) {
      Actor thrall = world.thrall();
      return thrall != null && thrall.alive() ? thrall.pos() : null;
    }

    Matcher coordinate = COORDINATE_PATTERN.matcher(value);
    if (coordinate.matches()) {
      try {
        return new Vec2(
                                    Integer.parseInt(coordinate.group(1)),
                                    Integer.parseInt(coordinate.group(2)));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }

    if (value.indexOf(',') >= 0) {
      return null;
    }

    Matcher direction = DIRECTION_PATTERN.matcher(value);
    if (direction.matches()) {
      int[] delta = directionDelta(direction.group(1));
      if (delta != null) {
        int distance = parseDistance(direction.group(2));
        if (distance <= 0) {
          return null;
        }

        Actor thrall = world.thrall();
        if (thrall == null || !thrall.alive() || thrall.pos() == null) {
          return null;
        }

        try {
          return new Vec2(
                                          Math.addExact(thrall.pos().x(), Math.multiplyExact(delta[0], distance)),
                                          Math.addExact(thrall.pos().y(), Math.multiplyExact(delta[1], distance)));
        } catch (ArithmeticException ignored) {
          return null;
        }
      }
    }

    Entity entity = world.get(value);
    return entity != null && entity.alive() ? entity.pos() : null;
  }

  private static Resolution resolveEntity(Address.EntityAddr address, World world) {
    String id = address.id();
    if (id == null || id.isBlank()) {
      return Resolution.Failure.UNKNOWN_ENTITY;
    }

    Entity entity = world.get(id);
    if (entity == null) {
      return Resolution.Failure.UNKNOWN_ENTITY;
    }
    if (!entity.alive()) {
      return Resolution.Failure.DEAD_ENTITY;
    }

    String path = address.path();
    if (path == null) {
      return success(new EntityTarget(entity));
    }

    if (entity instanceof Prop prop && path.equals("contents")) {
      Item contents = prop.contents();
      return contents == null
          ? success(new EmptyPropContents(prop))
          : success(new PropContents(prop, contents));
    }

    if (entity instanceof Actor actor) {
      Resolution inventory = resolveInventory(actor, path);
      if (!(inventory instanceof Resolution.Failure failure)
          || failure != Resolution.Failure.INVALID_PATH) {
        return inventory;
      }
    }

    BodyPart part = BodyPart.parse(path);
    return part == null
        ? Resolution.Failure.INVALID_PATH
        : success(new BodyTarget(entity, part));
  }

  private static Resolution resolveInventory(Actor actor, String path) {
    if (actor == null || !actor.alive()) {
      return Resolution.Failure.DEAD_ENTITY;
    }
    if (path == null || path.isEmpty()) {
      return Resolution.Failure.INVALID_PATH;
    }

    if (path.equals("pack")) {
      return success(new PackRoot(actor));
    }

    if (path.startsWith("pack/")) {
      String itemPath = path.substring("pack/".length());
      if (itemPath.isEmpty()) {
        return Resolution.Failure.INVALID_PATH;
      }

      Item item = actor.inventory().find(itemPath).orElse(null);
      return item == null
          ? Resolution.Failure.INVALID_PATH
          : success(new PackedItem(actor, item));
    }

    return EquipmentSlot.parse(path)
        .<Resolution>map(slot -> {
          Item item = actor.inventory().equipped(slot);
          return item == null
              ? success(new EmptyEquipmentSlot(actor, slot))
              : success(new EquippedItem(actor, slot, item));
        })
        .orElse(Resolution.Failure.INVALID_PATH);
  }

  private static Resolution.Success success(Resolved target) {
    return new Resolution.Success(target);
  }

  private static int parseDistance(String value) {
    if (value == null) {
      return 1;
    }

    try {
      int distance = Integer.parseInt(value);
      return distance > 0 ? distance : -1;
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static int[] directionDelta(String value) {
    return switch (value.toLowerCase()) {
      case "n", "north", "up" -> new int[] { 0, -1 };
      case "ne", "northeast" -> new int[] { 1, -1 };
      case "e", "east", "right" -> new int[] { 1, 0 };
      case "se", "southeast" -> new int[] { 1, 1 };
      case "s", "south", "down" -> new int[] { 0, 1 };
      case "sw", "southwest" -> new int[] { -1, 1 };
      case "w", "west", "left" -> new int[] { -1, 0 };
      case "nw", "northwest" -> new int[] { -1, -1 };
      default -> null;
    };
  }
}