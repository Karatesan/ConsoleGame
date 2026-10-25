// src/main/java/com/archon/verb/Material.java
package com.archon.verb;

import com.archon.model.Item;
import com.archon.model.Tag;

/** Whatever flows down a pipeline. */
public sealed interface Material {
    MaterialKind kind();

    record OfItem(Item item) implements Material {
        @Override public MaterialKind kind() { return MaterialKind.ITEM; }
    }

    record OfSubstance(Tag substance) implements Material {
        @Override public MaterialKind kind() { return MaterialKind.SUBSTANCE; }
    }
}