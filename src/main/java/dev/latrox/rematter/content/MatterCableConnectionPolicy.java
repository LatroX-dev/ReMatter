package dev.latrox.rematter.content;

import net.minecraft.core.Direction;

/** Geometry only: never grants access to a network, a capability or stored resources. */
final class MatterCableConnectionPolicy {
    private MatterCableConnectionPolicy() {}

    static boolean acceptsMachine(MachineKind kind, Direction front, Direction side, boolean cable) {
        if (kind == MachineKind.TERMINAL) return side == front.getOpposite();
        return !IndustrialStructure.isIndustrial(kind) && (!cable || side != front && side != Direction.UP);
    }

    /** A connected arm ends in a device flange unless it continues into another cable. */
    static boolean deviceArm(boolean connected, boolean neighborIsCable) {
        return connected && !neighborIsCable;
    }

    /** The cable's direction towards the port is opposite the port's outward world direction. */
    static boolean acceptsPort(boolean formed, Direction outward, Direction towardsPort) {
        return formed && outward == towardsPort.getOpposite();
    }
}
