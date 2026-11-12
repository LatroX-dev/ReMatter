"""Recipe-crystal hues and device assignments for ReMatter's machine family."""

FAMILY = {
    "analyzer": "ice",
    "decomposer": "copper",
    "material_synthesizer": "leaf",
    "chemical_reactor": "violet",
    "industrial_reactor": "violet",
    "material_preparator": "amber",
    "industrial_preparator": "amber",
    "sinter_furnace": "ember",
    "matter_drive": "mint",
    "matter_terminal": "mint",
    "cell_transfer_station": "mint",
    "network_interface": "blue",
    "energy_cube": "ember",
    "reinforced_energy_cube": "ember",
    "resonant_energy_cube": "ember",
}

RGB = {
    "ice": (215, 209, 188),  # Silith
    "copper": (220, 137, 79),
    "leaf": (125, 210, 127),
    "blue": (103, 148, 241),
    "violet": (179, 129, 232),
    "amber": (227, 169, 75),
    "ember": (234, 72, 72),
    "mint": (87, 205, 219),  # Diamond powder
}

# Hardware surfaces stay neutral. Family colors belong to the functional
# insets and contacts, never to the entire machine or component silhouette.
CHASSIS = (57, 63, 65)
RECESS = (30, 37, 39)
MATTE_TRIM = (90, 103, 108)
COPPER_CONTACT = (174, 111, 73)


def hex_color(rgb):
    return "#" + bytes(rgb).hex()
