package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.MachineMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-observed chamber construction preview and multi-position diagnostics for the authorized controller menu. */
public record StructureHintPayload(
        int containerId,
        BlockPos position,
        String expected,
        String orientation,
        List<Entry> entries,
        boolean hasBounds,
        BlockPos minBounds,
        BlockPos maxBounds,
        boolean showOverlay) implements CustomPacketPayload {

    private static final Set<String> ORIENTATIONS = Set.of("", "up", "down");

    public record Entry(BlockPos position, String expected, String orientation) {
        public Entry {
            position = position.immutable();
            if (expected.isEmpty() || expected.length() > 128 || !ORIENTATIONS.contains(orientation)) {
                throw new IllegalArgumentException("Invalid structure hint entry");
            }
        }
    }

    public static final StructureHintPayload EMPTY = new StructureHintPayload(-1, BlockPos.ZERO, "", "", List.of(), false, BlockPos.ZERO, BlockPos.ZERO, false);
    public static final Type<StructureHintPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "structure_hint"));

    public StructureHintPayload(int containerId, BlockPos position, String expected, String orientation) {
        this(containerId, position, expected, orientation,
                expected.isEmpty() ? List.of() : List.of(new Entry(position, expected, orientation)),
                !expected.isEmpty(), position, position, !expected.isEmpty());
    }

    public StructureHintPayload(
            int containerId,
            BlockPos position,
            String expected,
            String orientation,
            List<Entry> entries,
            boolean hasBounds,
            BlockPos minBounds,
            BlockPos maxBounds) {
        this(containerId, position, expected, orientation, entries, hasBounds, minBounds, maxBounds, !expected.isEmpty() || !entries.isEmpty());
    }

    public StructureHintPayload {
        position = position.immutable();
        minBounds = minBounds.immutable();
        maxBounds = maxBounds.immutable();
        entries = List.copyOf(entries);
        if (expected.length() > 128 || !ORIENTATIONS.contains(orientation) || entries.size() > IndustrialStructure.MAX_HINTS) {
            throw new IllegalArgumentException("Invalid structure hint");
        }
    }

    public static StructureHintPayload fromFormation(int containerId, IndustrialStructure.Formation formation) {
        return fromFormation(containerId, formation, true);
    }

    public static StructureHintPayload fromFormation(int containerId, IndustrialStructure.Formation formation, boolean showOverlay) {
        if (formation.formed() || formation.mismatches().isEmpty()) {
            return new StructureHintPayload(containerId, BlockPos.ZERO, "", "", List.of(), false, BlockPos.ZERO, BlockPos.ZERO, false);
        }
        var list = formation.mismatches().stream()
                .limit(IndustrialStructure.MAX_HINTS)
                .map(m -> new Entry(m.position(), m.expected(), m.orientation()))
                .toList();
        var primary = list.getFirst();
        boolean bounded = formation.minBounds() != null && formation.maxBounds() != null;
        return new StructureHintPayload(
                containerId,
                primary.position(),
                primary.expected(),
                primary.orientation(),
                list,
                bounded,
                bounded ? formation.minBounds() : primary.position(),
                bounded ? formation.maxBounds() : primary.position(),
                showOverlay);
    }

    public boolean present() { return !expected.isEmpty() || !entries.isEmpty(); }

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureHintPayload> CODEC = new StreamCodec<>() {
        @Override public StructureHintPayload decode(RegistryFriendlyByteBuf b) {
            int containerId = b.readVarInt();
            BlockPos pos = b.readBlockPos();
            String exp = b.readUtf(128);
            String orient = b.readUtf(8);
            int count = b.readVarInt();
            if (count < 0 || count > IndustrialStructure.MAX_HINTS) {
                throw new IllegalArgumentException("Too many structure hint entries: " + count);
            }
            var entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(b.readBlockPos(), b.readUtf(128), b.readUtf(8)));
            }
            boolean hasBounds = b.readBoolean();
            BlockPos min = hasBounds ? b.readBlockPos() : BlockPos.ZERO;
            BlockPos max = hasBounds ? b.readBlockPos() : BlockPos.ZERO;
            boolean showOverlay = b.readBoolean();
            return new StructureHintPayload(containerId, pos, exp, orient, entries, hasBounds, min, max, showOverlay);
        }

        @Override public void encode(RegistryFriendlyByteBuf b, StructureHintPayload p) {
            b.writeVarInt(p.containerId);
            b.writeBlockPos(p.position);
            b.writeUtf(p.expected, 128);
            b.writeUtf(p.orientation, 8);
            b.writeVarInt(p.entries.size());
            for (var entry : p.entries) {
                b.writeBlockPos(entry.position);
                b.writeUtf(entry.expected, 128);
                b.writeUtf(entry.orientation, 8);
            }
            b.writeBoolean(p.hasBounds);
            if (p.hasBounds) {
                b.writeBlockPos(p.minBounds);
                b.writeBlockPos(p.maxBounds);
            }
            b.writeBoolean(p.showOverlay);
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> {
            if (payload.containerId == 0) {
                dev.latrox.rematter.client.ClientStructureHints.accept(payload);
            } else if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == payload.containerId) {
                menu.acceptStructureHint(payload);
                dev.latrox.rematter.client.ClientStructureHints.accept(payload);
            }
        });
    }

    @Override public Type<StructureHintPayload> type() { return TYPE; }
}
