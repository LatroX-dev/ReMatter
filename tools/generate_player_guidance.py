#!/usr/bin/env python3
"""Write optional breadcrumbs that only real server-operation hooks can award."""
from pathlib import Path
import argparse
from generated_files import GeneratedFiles

ROOT = Path(__file__).resolve().parents[1]
ADVANCEMENTS = (
    ('start_workshop', 'hand_preparator', None),
    ('first_lesson', 'decomposer', 'start_workshop'),
    ('put_it_back', 'material_synthesizer', 'first_lesson'),
    ('hands_off', 'network_interface', 'first_lesson'),
    ('proper_tools', 'pressure_module', 'start_workshop'),
    ('something_older', 'primal_shard', 'start_workshop'),
    ('on_paper', 'primal_cell_16k', 'first_lesson'),
    ('industrial_scale', 'industrial_reactor', 'proper_tools'),
    ('grown_not_mined', 'minecraft:diamond', 'industrial_scale'),
    ('one_way', 'primal_converter', 'something_older'),
)


def generate(check):
    out = GeneratedFiles(ROOT, check)
    for name, icon, parent in ADVANCEMENTS:
        display = {'title': {'translate': f'advancement.rematter.{name}.title'},
                   'description': {'translate': f'advancement.rematter.{name}.description'},
                   'icon': {'id': icon if ':' in icon else 'rematter:' + icon},
                   'show_toast': True, 'announce_to_chat': False, 'hidden': False}
        if parent is None:
            display['background'] = 'minecraft:gui/advancements/backgrounds/adventure'
        data = {'display': display, 'criteria': {'earned': {'trigger': 'minecraft:impossible'}}, 'requirements': [['earned']]}
        if parent is not None:
            data['parent'] = 'rematter:progression/' + parent
        out.json(ROOT / f'src/main/resources/data/rematter/advancement/progression/{name}.json', data)
    print(f"{'Checked' if check else 'Generated'} {out.count} optional progression advancements")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
