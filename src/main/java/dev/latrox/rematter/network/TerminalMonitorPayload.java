package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server-authoritative Network Stock & Automation Monitor snapshot for wired and wireless Matter Terminals.
 * Summarizes cells, Drives, workers, emitters, Void Cell coverage, machine target caps, emitter thresholds,
 * and live rolling per-substance net rates (u/min).
 */
public record TerminalMonitorPayload(
        int containerId,
        int nodes,
        int drives,
        int finiteCells,
        int voidCells,
        boolean voidUnrestricted,
        int activeWorkers,
        int totalWorkers,
        int activeEmitters,
        int totalEmitters,
        long used,
        long capacity,
        List<MonitorRow> rows
) implements CustomPacketPayload {
    public static final int MAX_ROWS = 64;
    public static final TerminalMonitorPayload EMPTY = new TerminalMonitorPayload(
            -1, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, List.of());
    public static final Type<TerminalMonitorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_monitor"));

    public enum RowKind {
        RATE,
        TARGET_CAP,
        LEVEL_EMITTER,
        VOID_OVERFLOW
    }

    public record MonitorRow(
            RowKind kind,
            String substanceId,
            String phase,
            boolean known,
            String contextId,
            long current,
            long limit,
            boolean active,
            boolean belowMode
    ) {
        public MonitorRow {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(substanceId);
            Objects.requireNonNull(phase);
            Objects.requireNonNull(contextId);
        }
    }

    public TerminalMonitorPayload {
        rows = List.copyOf(rows);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalMonitorPayload> CODEC = new StreamCodec<>() {
        @Override
        public TerminalMonitorPayload decode(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            int nodes = buf.readVarInt();
            int drives = buf.readVarInt();
            int finiteCells = buf.readVarInt();
            int voidCells = buf.readVarInt();
            boolean voidUnrestricted = buf.readBoolean();
            int activeWorkers = buf.readVarInt();
            int totalWorkers = buf.readVarInt();
            int activeEmitters = buf.readVarInt();
            int totalEmitters = buf.readVarInt();
            long used = buf.readVarLong();
            long capacity = buf.readVarLong();
            int count = MachineSnapshot.count(buf, MAX_ROWS);
            var rows = new ArrayList<MonitorRow>(count);
            for (int i = 0; i < count; i++) {
                rows.add(new MonitorRow(
                        buf.readEnum(RowKind.class),
                        buf.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH),
                        buf.readUtf(8),
                        buf.readBoolean(),
                        buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readBoolean(),
                        buf.readBoolean()));
            }
            return new TerminalMonitorPayload(
                    containerId, nodes, drives, finiteCells, voidCells, voidUnrestricted,
                    activeWorkers, totalWorkers, activeEmitters, totalEmitters, used, capacity, rows);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TerminalMonitorPayload payload) {
            buf.writeVarInt(payload.containerId());
            buf.writeVarInt(payload.nodes());
            buf.writeVarInt(payload.drives());
            buf.writeVarInt(payload.finiteCells());
            buf.writeVarInt(payload.voidCells());
            buf.writeBoolean(payload.voidUnrestricted());
            buf.writeVarInt(payload.activeWorkers());
            buf.writeVarInt(payload.totalWorkers());
            buf.writeVarInt(payload.activeEmitters());
            buf.writeVarInt(payload.totalEmitters());
            buf.writeVarLong(payload.used());
            buf.writeVarLong(payload.capacity());
            buf.writeVarInt(payload.rows().size());
            for (MonitorRow row : payload.rows()) {
                buf.writeEnum(row.kind());
                buf.writeUtf(row.substanceId(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                buf.writeUtf(row.phase(), 8);
                buf.writeBoolean(row.known());
                buf.writeUtf(row.contextId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buf.writeVarLong(row.current());
                buf.writeVarLong(row.limit());
                buf.writeBoolean(row.active());
                buf.writeBoolean(row.belowMode());
            }
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptTerminalMonitor(payload);
            }
        });
    }

    @Override
    public Type<TerminalMonitorPayload> type() {
        return TYPE;
    }
}
