"""Vanilla recipe-book discovery for a concrete shaped recipe, including its exact ingredient counts."""
from collections import Counter


def shaped_discovery(name, pattern, keys):
    counts = Counter(keys[symbol] for row in pattern for symbol in row if symbol != ' ')
    return {'parent': 'minecraft:recipes/root', 'criteria': {
        'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [
            {'items': [item], 'count': {'min': count}} for item, count in sorted(counts.items())]}},
        'has_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'rematter:' + name}}},
        'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': ['rematter:' + name]}}
