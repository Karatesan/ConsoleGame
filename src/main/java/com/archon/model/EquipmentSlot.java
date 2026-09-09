package com.archon.model;

import java.util.*;

public enum EquipmentSlot {
    HEAD("head"),
    TORSO("torso"),
    LEGS("legs"),
    HAND_LEFT("hand/left"),
    HAND_RIGHT("hand/right");

    public final String path;

    EquipmentSlot(String path) {
        this.path = path;
    }

    // Pre-computed map for fast O(1) resolution without allocating streams
    private static final Map<String, EquipmentSlot> BY_PATH = new HashMap<>();

    static {
        for (EquipmentSlot slot : values()) {
            BY_PATH.put(slot.path.toLowerCase(), slot);
        }
    }

    /**
     * Resolves CLI argument strings like "hand/left" or "TORSO" into an enum.
     */
    public static Optional<EquipmentSlot> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_PATH.get(text.trim().toLowerCase()));
    }
}