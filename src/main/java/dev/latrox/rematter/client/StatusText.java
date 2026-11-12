package dev.latrox.rematter.client;

import net.minecraft.network.chat.Component;

/** A machine status in the reader's language; a Network Interface's order statuses say how many orders they concern. */
public final class StatusText {
    private StatusText() {}

    /** Unknown future states stay visible as a warning instead of falsely appearing idle. */
    public static UiIcon icon(String status) {
        return switch (status) {
            case "idle", "ready", "awaiting_request", "paused", "research_paused", "energy_full", "none" -> UiIcon.STATUS_IDLE;
            case "working", "grinding", "sieving", "collecting", "dosing", "mixing", "packing", "pressing", "sintering", "cooling", "researching", "generating", "cable_limited", "research_energy_debt", "identity_checkpoint" -> UiIcon.STATUS_WORKING;
            case "analyzed", "research_complete", "target_reached" -> UiIcon.STATUS_DONE;
            case "insert_sample", "input", "input_batch", "secondary_input", "tertiary_input", "missing_matter", "matter", "energy", "insufficient_energy", "crank", "choose_process", "selection_unavailable", "analysis_needed", "research_payment", "generator_fuel", "network_controlled", "manual_priority", "resources_reserved" -> UiIcon.STATUS_WAITING;
            case "storage_changed", "automation_orders_stale", "automation_orders_stale_count", "research_refund", "target_unavailable" -> UiIcon.STATUS_WARNING;
            case "no_storage", "missing_cell", "output", "output_blocked", "storage_full", "storage_types_full", "storage_filtered", "secondary_slot_occupied", "tertiary_slot_occupied", "preparation_tier", "preparation_process", "unsupported_input", "unsupported_components", "requires_pressure", "requires_advancement", "requires_open_air", "requires_research", "requires_field", "requires_precision", "structure", "structure_too_short", "structure_unloaded", "structure_frame", "structure_wall", "structure_endcaps", "structure_ports", "structure_modules_missing", "structure_module_incomplete", "structure_module_position", "structure_module_type", "structure_interior", "structure_ambiguous", "structure_claimed", "network_too_large", "network_unloaded", "automation_interface_conflict", "automation_clear_slots", "automation_job_refused", "research_owner", "research_identity_changed" -> UiIcon.STATUS_BLOCKED;
            case "unknown_schema", "unsupported_data", "network_invalid", "automation_data_locked", "automation_quarantined", "automation_quarantined_count", "automation_catalogue_invalid", "preparation_catalogue_invalid", "invalid_quantity" -> UiIcon.STATUS_ERROR;
            case "automation_off", "automation_disconnected", "automation_backend_missing" -> UiIcon.NETWORK_OFFLINE;
            default -> UiIcon.STATUS_WARNING;
        };
    }

    public static UiIcon icon(String status, dev.latrox.rematter.content.MachineKind kind) {
        return status.equals("ready") && kind == dev.latrox.rematter.content.MachineKind.NETWORK_INTERFACE
                ? UiIcon.NETWORK_ONLINE : icon(status);
    }

    public static Component of(String status, int staleOrders, int heldOrders) {
        return switch (status) {
            case "automation_orders_stale" -> Component.translatable("status.rematter.automation_orders_stale_count", staleOrders);
            case "automation_quarantined" -> Component.translatable("status.rematter.automation_quarantined_count", heldOrders);
            default -> Component.translatable("status.rematter." + status);
        };
    }
}
