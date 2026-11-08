package dev.latrox.rematter.content;

import java.util.Optional;
import net.minecraft.core.Direction;

/** All module functions share the same upright or inverted mounting socket. */
public enum IndustrialModuleMount {
    FLOOR(Direction.UP), CEILING(Direction.DOWN);

    private final Direction anchorFace;
    IndustrialModuleMount(Direction anchorFace) { this.anchorFace = anchorFace; }
    public Direction anchorFace() { return anchorFace; }
    public Direction anchorDirection() { return anchorFace.getOpposite(); }
    public boolean suspended() { return this == CEILING; }

    public static Optional<IndustrialModuleMount> forPlacement(Direction clickedFace, boolean supported) {
        if (!supported) return Optional.empty();
        return switch (clickedFace) {
            case UP -> Optional.of(FLOOR);
            case DOWN -> Optional.of(CEILING);
            default -> Optional.empty();
        };
    }

    /** Only the floor and ceiling provide mounting surfaces. */
    public static Optional<IndustrialModuleMount> atInteriorHeight(int y, int crown) {
        if (y == 0) return Optional.of(FLOOR);
        if (y == crown) return Optional.of(CEILING);
        return Optional.empty();
    }
}
