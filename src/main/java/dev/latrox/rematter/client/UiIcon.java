package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachinePortMode;
import net.minecraft.resources.Identifier;

/** The single sprite vocabulary for controls, lamps and empty slots. */
public enum UiIcon {
    ACCESS("access"),
    ANALYSE("analyse"),
    ARROW_RIGHT("arrow_right"),
    BACK("back"),
    CANCEL("cancel"),
    CHEMICAL("chemical"),
    CLEAR("clear"),
    CONFIRM("confirm"),
    CRANK("crank"),
    DERIVE("derive"),
    DEVICES("devices"),
    DISABLED("disabled"),
    DISCOVER("discover"),
    EJECT("eject"),
    ENERGY("energy"),
    EXCHANGE("exchange"),
    FAMILIES("families"),
    FUEL("fuel"),
    GAS("gas"),
    HELP("help"),
    HOPPER("hopper"),
    INFO("info"),
    KNOWN("known"),
    LIQUID("liquid"),
    LOAD("load"),
    LOCATE("locate"),
    LOCKED("locked"),
    LOOP("loop"),
    LOOP_OFF("loop_off"),
    MACHINE("machine"),
    MAKE_ONE("make_one"),
    MANUAL("manual"),
    MATTER("matter"),
    MINUS("minus"),
    NETWORK_OFFLINE("network_offline"),
    NETWORK_ONLINE("network_online"),
    NEXT("next"),
    OPEN("open"),
    PAUSE("pause"),
    PLUS("plus"),
    PORT_AUTO("port_auto"),
    PORT_FLUID_IN("port_fluid_in"),
    PORT_FLUID_OUT("port_fluid_out"),
    PORT_ITEM_IN("port_item_in"),
    PORT_ITEM_OUT("port_item_out"),
    PREVIEW("preview"),
    PREVIOUS("previous"),
    PRIMAL("primal"),
    PROGRESS("progress"),
    QUEUE_ADD("queue_add"),
    RECIPE("recipe"),
    REFUND("refund"),
    RESEARCH("research"),
    RESUME("resume"),
    SEARCH("search"),
    SETTINGS("settings"),
    SIDES("sides"),
    SLOT_ADDITIVE("slot_additive"),
    SLOT_CELL("slot_cell"),
    SLOT_EFFICIENCY("slot_efficiency"),
    SLOT_FUEL("slot_fuel"),
    SLOT_INPUT("slot_input"),
    SLOT_LENS("slot_lens"),
    SLOT_MIX("slot_mix"),
    SLOT_OUTPUT("slot_output"),
    SLOT_PARALLEL("slot_parallel"),
    SLOT_PRESSURE("slot_pressure"),
    SLOT_PRIMAL_CELL("slot_primal_cell"),
    SLOT_SAMPLE("slot_sample"),
    SLOT_SHARD("slot_shard"),
    SLOT_SPEED("slot_speed"),
    SLOT_UPGRADE("slot_upgrade"),
    SOLID("solid"),
    STAR("star"),
    STAR_OUTLINE("star_outline"),
    START("start"),
    STATUS_BLOCKED("status_blocked"),
    STATUS_DONE("status_done"),
    STATUS_ERROR("status_error"),
    STATUS_IDLE("status_idle"),
    STATUS_WAITING("status_waiting"),
    STATUS_WARNING("status_warning"),
    STATUS_WORKING("status_working"),
    STOCK("stock"),
    STOP("stop"),
    TARGET("target"),
    TIME("time"),
    UNKNOWN("unknown"),
    UNLOCKED("unlocked"),
    VENT("vent"),
    WARNING("warning");

    public final Identifier sprite;
    UiIcon(String name) { sprite = Identifier.fromNamespaceAndPath("rematter", "icon/" + name); }

    public static UiIcon port(MachinePortMode mode) {
        return switch (mode) {
            case AUTOMATIC -> PORT_AUTO;
            case ITEM_INPUT -> PORT_ITEM_IN;
            case ITEM_OUTPUT -> PORT_ITEM_OUT;
            case FLUID_INPUT -> PORT_FLUID_IN;
            case FLUID_OUTPUT -> PORT_FLUID_OUT;
            case POWER -> ENERGY;
            case MATTER -> MATTER;
            case CHEMICAL -> CHEMICAL;
            case DISABLED -> DISABLED;
        };
    }
}
