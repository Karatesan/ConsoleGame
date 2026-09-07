package com.archon.verb;

import com.archon.model.Item;
import com.archon.model.Tag;

/** Whatever flows down a pipeline. */
public sealed interface Material {
    record OfItem(Item item) implements Material {}
    record OfSubstance(Tag substance) implements Material {}
}