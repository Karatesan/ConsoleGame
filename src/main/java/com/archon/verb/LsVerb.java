    // src/main/java/com/archon/verb/LsVerb.java
    package com.archon.verb;

    import com.archon.address.Resolved;
    import com.archon.model.ItemLocation;
    import com.archon.model.World;

    public final class LsVerb implements Verb {

        @Override
        public Check validateStructural(ValidationContext c) {
            return c.checkOptionalAddress(0);
        }

        @Override
        public ExitCode execute(VerbContext c) {
            String address = c.inv.arg(0) == null ? "/pack" : c.inv.arg(0);
            Resolved.Target target = c.resolveTarget(address);
            if (target == null) return ExitCode.BLOCKED;

            String listing = formatList(c.world, target);
            if (listing == null) {
                return c.rejectBlocked("nothing to list at " + address);
            }
            c.say(listing);
            return ExitCode.SUCCESS;
        }

        private static String formatList(World world, Resolved.Target target) {
            return switch (target) {
                case Resolved.OnLocation(ItemLocation.Pack(var owner)) ->
                        owner.inventory().isPackEmpty()
                                ? "(empty)"
                                : String.join("\n", owner.inventory().pack().stream()
                                .map(item -> "  " + item.id() + "  " + item.tags())
                                .toList());
                case Resolved.OnLocation ignored -> "(empty)";
                case Resolved.OnItem(var item, var ignored) -> "  " + item.id() + "  " + item.tags();
                case Resolved.OnTile(var pos, var ignored) -> world.tile(pos).ground().toString();
                default -> null;
            };
        }
    }