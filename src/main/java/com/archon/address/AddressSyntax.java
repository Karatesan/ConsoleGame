package com.archon.address;

import com.archon.model.BodyPart;
import com.archon.model.EquipmentSlot;
import com.archon.model.Vec2;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressSyntax {
    private static final Pattern COORDINATES =
            Pattern.compile("([+-]?\\d+)\\s*,\\s*([+-]?\\d+)");
    private static final Pattern RELATIVE =
            Pattern.compile("(ne|nw|se|sw|n|s|e|w)(\\d+)?", Pattern.CASE_INSENSITIVE);

    private AddressSyntax() {}

    sealed interface Path {}
    record RootPath() implements Path {}
    record ContentsPath() implements Path {}
    record BodyPath(BodyPart part) implements Path {}
    record PackPath(String query) implements Path {}
    record SlotPath(EquipmentSlot slot) implements Path {}
    record BadPath(String reason) implements Path {}

    sealed interface Tile {}
    record AbsoluteTile(int x, int y) implements Tile {}
    record RelativeTile(Vec2 direction, int distance) implements Tile {}
    record AnchorTile(String id) implements Tile {}
    record BadTile(String reason) implements Tile {}

    public static Optional<String> problem(String raw) {
        String reason = switch (Address.parse(raw)) {
            case Address.Invalid invalid -> invalid.reason();
            case Address.EntityAddr entity -> pathProblem(path(entity.path(), true));
            case Address.InventoryAddr inventory -> pathProblem(path(inventory.path(), false));
            case Address.TileAddr address ->
                    tile(address.spec()) instanceof BadTile bad ? bad.reason() : null;
        };
        return Optional.ofNullable(reason);
    }

    static Path path(String text, boolean entityPath) {
        if (entityPath && text.isEmpty()) {
            return new RootPath();
        }
        if (entityPath && text.equals("contents")) {
            return new ContentsPath();
        }
        if (entityPath && text.startsWith("body/")) {
            BodyPart part = BodyPart.parse(text.substring("body/".length()));
            return part == null ? badPath(text) : new BodyPath(part);
        }
        if (text.equals("pack")) {
            return new PackPath(null);
        }
        if (text.startsWith("pack/")) {
            String query = text.substring("pack/".length());
            return query.isBlank() ? badPath(text) : new PackPath(query);
        }

        return EquipmentSlot.parse(text)
                .<Path>map(SlotPath::new)
                .orElseGet(() -> badPath(text));
    }

    static Tile tile(String spec) {
        try {
            if (spec.contains(",")) {
                Matcher coordinates = COORDINATES.matcher(spec.trim());
                if (!coordinates.matches()) {
                    return new BadTile("invalid coordinates \"" + spec + "\"; use @x,y");
                }
                return new AbsoluteTile(
                        Integer.parseInt(coordinates.group(1)),
                        Integer.parseInt(coordinates.group(2))
                );
            }

            Matcher relative = RELATIVE.matcher(spec);
            if (relative.matches()) {
                return new RelativeTile(
                        Vec2.dir(relative.group(1)),
                        relative.group(2) == null ? 1 : Integer.parseInt(relative.group(2))
                );
            }
        } catch (NumberFormatException exception) {
            return new BadTile("tile coordinate or distance is too large in \"" + spec + "\"");
        }
        return new AnchorTile(spec);
    }

    private static BadPath badPath(String text) {
        return new BadPath("unknown target path \"" + text + "\"");
    }

    private static String pathProblem(Path path) {
        return path instanceof BadPath bad ? bad.reason() : null;
    }
}