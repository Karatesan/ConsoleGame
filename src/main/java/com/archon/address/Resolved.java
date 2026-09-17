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

  Pattern COORDINATE_PATTERN = Pattern.compile("^([+-]?\\d+),([+-]?\\d+)$");
  Pattern DIRECTION_PATTERN = Pattern.compile("^(n|ne|e|se|s|sw|w|nw)([1-9]\\d*)?$");
  Pattern MALFORMED_DIRECTION_PATTERN = Pattern.compile(
      "^(?:n|ne|e|se|s|sw|w|nw)(?:[+-]\\d*|0\\d*|\\s+.*|[/,:].*)$");

  record EntityTarget(Entity entity) implements Resolved {
    public EntityTarget {
      entity = Objects.requireNonNull(entity, "entity");
    }
  }

  record BodyTarget(Entity entity, BodyPart bodyPart) implements Resolved {
    public BodyTarget {
      entity = Objects.requireNonNull(entity, "entity");
      bodyPart = Objects.requireNonNull(bodyPart, "bodyPart");
    }
  }

  record PackRoot(Actor owner) implements Resolved {
    public PackRoot {
      owner = Objects.requireNonNull(owner, "owner");
    }
  }

  record PackedItem(Actor owner, Item item) implements Resolved {
    public PackedItem {
      owner = Objects.requireNonNull(owner, "owner");
      item = Objects.requireNonNull(item, "item");
    }
  }

  record EquippedItem(Actor owner, EquipmentSlot slot, Item item) implements Resolved {
    public EquippedItem {
      owner = Objects.requireNonNull(owner, "owner");
      slot = Objects.requireNonNull(slot, "slot");
      item = Objects.requireNonNull(item, "item");
    }
  }

  record EmptyEquipmentSlot(Actor owner, EquipmentSlot slot) implements Resolved {
    public EmptyEquipmentSlot {
      owner = Objects.requireNonNull(owner, "owner");
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

  public static Resolution resolve(Address address, World world) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(world, "world");

    return switch (address) {
      case Address.TileAddr tileAddress -> resolveTile(tileAddress, world);
      case Address.InventoryAddr inventoryAddress -> resolveInventory(world.thrall(),
          inventoryAddress.path());
      case Address.EntityAddr entityAddress -> resolveEntity(entityAddress, world);
    };
  }

  private static Resolution resolveTile(Address.TileAddr address, World world) {
    String spec = address.spec();
    if (spec == null || spec.isBlank()) {
      return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
          "A tile specification is required.");
    }

    if (spec.equals("self")) {
      Actor thrall = world.thrall();
      if (thrall == null) {
        return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
            "No thrall is available to resolve self.");
      }
      if (!thrall.alive()) {
        return failure(Resolution.FailureReason.DEAD_ENTITY,
            "The thrall is dead and cannot resolve self.");
      }
      return tileAt(thrall.pos(), address.layer(), world,
          "The thrall does not have a valid tile position.");
    }

    Matcher coordinateMatcher = COORDINATE_PATTERN.matcher(spec);
    if (coordinateMatcher.matches()) {
      try {
        Vec2 pos = new Vec2(
            Integer.parseInt(coordinateMatcher.group(1)),
            Integer.parseInt(coordinateMatcher.group(2)));
        return tileAt(pos, address.layer(), world,
            "The coordinate does not resolve to a valid tile position.");
      } catch (NumberFormatException ignored) {
        return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
            "The coordinate values are outside the supported range.");
      }
    }

    if (spec.indexOf(',') >= 0 || spec.indexOf('\n') >= 0 || spec.indexOf('\r') >= 0
        || spec.indexOf('\t') >= 0 || spec.indexOf(' ') >= 0
        || MALFORMED_DIRECTION_PATTERN.matcher(spec).matches()) {
      return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
          "The tile specification is malformed.");
    }

    Matcher directionMatcher = DIRECTION_PATTERN.matcher(spec);
    if (directionMatcher.matches()) {
      Actor thrall = world.thrall();
      if (thrall == null) {
        return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
            "No thrall is available to resolve a relative tile.");
      }
      if (!thrall.alive()) {
        return failure(Resolution.FailureReason.DEAD_ENTITY,
            "The thrall is dead and cannot resolve a relative tile.");
      }
      if (thrall.pos() == null) {
        return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
            "The thrall does not have a valid tile position.");
      }

      int distance;
      try {
        String distanceText = directionMatcher.group(2);
        distance = distanceText == null ? 1 : Integer.parseInt(distanceText);
      } catch (NumberFormatException ignored) {
        return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
            "The direction distance is outside the supported range.");
      }

      int[] delta = directionDelta(directionMatcher.group(1));
      try {
        Vec2 pos = new Vec2(
            Math.addExact(thrall.pos().x(), Math.multiplyExact(delta[0], distance)),
            Math.addExact(thrall.pos().y(), Math.multiplyExact(delta[1], distance)));
        return tileAt(pos, address.layer(), world,
            "The relative tile position is outside the supported range.");
      } catch (ArithmeticException ignored) {
        return failure(Resolution.FailureReason.INVALID_TILE_SPEC,
            "The relative tile position is outside the supported range.");
      }
    }

    Entity entity = world.get(spec);
    if (entity == null) {
      return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
          "No entity exists with ID '" + spec + "'.");
    }
    if (!entity.alive()) {
      return failure(Resolution.FailureReason.DEAD_ENTITY,
          "The entity with ID '" + spec + "' is dead.");
    }

    return tileAt(entity.pos(), address.layer(), world,
        "The entity with ID '" + spec + "' does not have a valid tile position.");
  }

  private static Resolution tileAt(Vec2 pos, Address.Layer layer, World world, String invalidDetail) {
    if (pos == null) {
      return failure(Resolution.FailureReason.INVALID_TILE_SPEC, invalidDetail);
    }
    if (!world.inBounds(pos)) {
      return failure(Resolution.FailureReason.OUT_OF_BOUNDS,
          "The resolved tile position is outside the world bounds.");
    }
    return new Resolution.Found(new TileTarget(pos, layer));
  }

  private static Resolution resolveEntity(Address.EntityAddr address, World world) {
    String id = address.id();
    if (id == null || id.isBlank()) {
      return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
          "An entity ID is required.");
    }

    Entity entity = world.get(id);
    if (entity == null) {
      return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
          "No entity exists with ID '" + id + "'.");
    }
    if (!entity.alive()) {
      return failure(Resolution.FailureReason.DEAD_ENTITY,
          "The entity with ID '" + id + "' is dead.");
    }

    String path = address.path();
    if (path == null) {
      return new Resolution.Found(new EntityTarget(entity));
    }

    if (entity instanceof Prop prop && path.equals("contents")) {
      Item item = prop.contents();
      return item == null
          ? new Resolution.Found(new EmptyPropContents(prop))
          : new Resolution.Found(new PropContents(prop, item));
    }

    if (entity instanceof Actor actor) {
      Resolution inventoryResolution = resolveActorInventoryPath(actor, path);
      if (inventoryResolution != null) {
        return inventoryResolution;
      }
    }

    BodyPart bodyPart = BodyPart.parse(path);
    if (bodyPart != null) {
      return new Resolution.Found(new BodyTarget(entity, bodyPart));
    }

    return failure(Resolution.FailureReason.INVALID_PATH,
        "The path '" + path + "' is not valid for this entity.");
  }

  private static Resolution resolveInventory(Actor actor, String path) {
    if (actor == null) {
      return failure(Resolution.FailureReason.UNKNOWN_ENTITY,
          "No thrall is available to resolve the inventory address.");
    }
    if (!actor.alive()) {
      return failure(Resolution.FailureReason.DEAD_ENTITY,
          "The thrall is dead and cannot resolve the inventory address.");
    }

    Resolution resolution = resolveActorInventoryPath(actor, path);
    return resolution != null
        ? resolution
        : failure(Resolution.FailureReason.INVALID_PATH,
            "The inventory path is invalid.");
  }

  private static Resolution resolveActorInventoryPath(Actor actor, String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }

    if (path.equals("pack")) {
      return new Resolution.Found(new PackRoot(actor));
    }

    if (path.startsWith("pack/")) {
      String itemPath = path.substring("pack/".length());
      if (itemPath.isEmpty()) {
        return failure(Resolution.FailureReason.INVALID_PATH,
            "An item path is required after 'pack/'.");
      }

      Item item = actor.inventory().find(itemPath).orElse(null);
      return item == null
          ? failure(Resolution.FailureReason.INVALID_PATH,
              "No packed item matches the requested item path.")
          : new Resolution.Found(new PackedItem(actor, item));
    }

    return EquipmentSlot.parse(path)
        .<Resolution>map(slot -> {
          Item item = actor.inventory().equipped(slot);
          return item == null
              ? new Resolution.Found(new EmptyEquipmentSlot(actor, slot))
              : new Resolution.Found(new EquippedItem(actor, slot, item));
        })
        .orElse(null);
  }

  private static Resolution.Failure failure(
      Resolution.FailureReason reason, String detail) {
    return new Resolution.Failure(
        Objects.requireNonNull(reason, "reason"),
        Objects.requireNonNull(detail, "detail"));
  }

  private static int[] directionDelta(String direction) {
    return switch (direction) {
      case "n" -> new int[] {0, -1};
      case "ne" -> new int[] {1, -1};
      case "e" -> new int[] {1, 0};
      case "se" -> new int[] {1, 1};
      case "s" -> new int[] {0, 1};
      case "sw" -> new int[] {-1, 1};
      case "w" -> new int[] {-1, 0};
      case "nw" -> new int[] {-1, -1};
      default -> throw new IllegalArgumentException("Unsupported direction: " + direction);
    };
  }
}