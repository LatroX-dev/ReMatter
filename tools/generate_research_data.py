#!/usr/bin/env python3
"""Generate exact material research families; never infer permissions from tags or recipes."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/data/rematter/rematter/material_research'
# Explicit natural-material Discovery allowlist. Manufactured alloys, components, essences and boss loot stay absent.
TIERS = {
    1: set('aluminum amber andesite boron calcite charcoal chromium clay coal copper deepslate diorite granite graphite iron lead natural_ice nickel salt silica silicon soil stone sulfur tin tuff zinc'.split()),
    2: set('amethyst basalt blackstone certus_quartz cinnabar emerald end_stone ender_pearl fluorite glowstone gold lapis lithium netherrack obsidian osmium peridot platinum quartz quicksilver redstone ruby sapphire seashell silver titanium uraninite uranium'.split()),
    3: set('arcane_crystal diamond dimensional_shard iridium netherite_scrap sky_stone'.split()),
}
DIMENSIONS = {
    'basalt': 'minecraft:the_nether', 'blackstone': 'minecraft:the_nether',
    'glowstone': 'minecraft:the_nether', 'netherrack': 'minecraft:the_nether',
    'quartz': 'minecraft:the_nether', 'netherite_scrap': 'minecraft:the_nether',
    'end_stone': 'minecraft:the_end',
}
NAMES = {'silica': 'Sand & Glass', 'natural_ice': 'Ice', 'soil': 'Soil', 'netherite_scrap': 'Ancient Debris & Netherite Scrap'}

def generate(check=False):
    source = json.loads((ROOT / 'tools/data/material_catalogue.json').read_text())
    families = source['families']
    declared = set().union(*TIERS.values())
    assert declared <= families.keys(), declared - families.keys()
    assert sum(map(len, TIERS.values())) == len(declared), 'A Discovery family has exactly one tier'
    grouped = {key: [] for key in families}
    for row in source['items']:
        assert row['reviewed']
        grouped[row['family']].append(row['item'])
    expected = set()
    for key, family in sorted(families.items()):
        assert grouped[key], f'Family without exact item members: {key}'
        # Learning teaches these retained substance identities, never their elemental composition.
        substances = sorted({(r['substance'], r['phase']) for r in family['batch']['matter']})
        tier = next((tier for tier, keys in TIERS.items() if key in keys), 0)
        doc = {'schema': 1, 'name': NAMES.get(key, key.replace('_', ' ').title()),
               'items': sorted(grouped[key]),
               'substances': [{'substance': id, 'phase': phase} for id, phase in substances],
               'derive': bool(family['synthesis']), 'starting_knowledge': False,
               'discovery_tier': tier, 'requires_dimension': DIMENSIONS.get(key, '')}
        path = OUT / (key + '.json'); expected.add(path)
        text = json.dumps(doc, indent=2, ensure_ascii=False) + '\n'
        if check:
            if not path.exists() or path.read_text() != text:
                raise SystemExit(f'Outdated research family: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    extra = set(OUT.glob('*.json')) - expected
    if extra:
        raise SystemExit(f'Unexpected research files; remove explicitly: {sorted(extra)}')
    print(f'{len(expected)} exact families; {len(declared)} explicitly permitted Discovery families')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
