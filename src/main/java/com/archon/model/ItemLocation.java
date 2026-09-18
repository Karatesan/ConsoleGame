package com.archon.model;

import java.util.Objects;

public sealed interface ItemLocation {
    Vec2 pos();

    record Pack(Actor owner) implements ItemLocation {
        public Pack {
            Objects.requireNonNull(owner, "owner");
        }

        @Override
        public Vec2 pos() {
            return owner.pos();
        }
    }

    record Equipment(Actor owner, EquipmentSlot slot) implements ItemLocation {
        public Equipment {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(slot, "slot");
        }

        @Override
        public Vec2 pos() {
            return owner.pos();
        }
    }

    record Contents(Prop owner) implements ItemLocation {
        public Contents {
            Objects.requireNonNull(owner, "owner");
        }

        @Override
        public Vec2 pos() {
            return owner.pos();
        }
    }

    record Ground(Vec2 pos) implements ItemLocation {
        public Ground {
            Objects.requireNonNull(pos, "pos");
        }
    }
}