package dev.latrox.rematter.client;

import net.minecraft.network.chat.Component;

/** A machine status in the reader's language; a Network Interface's order statuses say how many orders they concern. */
public final class StatusText {
    private StatusText() {}

    public static Component of(String status, int staleOrders, int heldOrders) {
        return switch (status) {
            case "automation_orders_stale" -> Component.translatable("status.rematter.automation_orders_stale_count", staleOrders);
            case "automation_quarantined" -> Component.translatable("status.rematter.automation_quarantined_count", heldOrders);
            default -> Component.translatable("status.rematter." + status);
        };
    }
}
