package dev.latrox.rematter.content;

import net.minecraft.core.Direction;

/** Geometry only: never grants access to a network, a capability or stored resources. */
final class MatterCableConnectionPolicy {
    private MatterCableConnectionPolicy() {}

    /** A cable or a neighbouring device attaches only to a side that can be a connection: never the front. */
    static boolean acceptsMachine(MachineKind kind, Direction front, Direction side) {
        return PortSides.available(kind, MachinePortMode.Side.local(front, side));
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
