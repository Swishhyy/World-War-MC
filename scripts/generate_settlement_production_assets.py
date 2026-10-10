#!/usr/bin/env python3
"""Native model/texture reuse for settlement production. Run after generate_progression_assets.py."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/wwmc'
DATA = ROOT / 'data'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def item(name, model):
    write(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'wwmc:item/{name}'}})
    write(ASSETS / f'models/item/{name}.json', model)


def tag(path, values):
    old = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    for value in values:
        if value not in old['values']:
            old['values'].append(value)
    write(path, old)


def box(start, end, texture):
    return {'from': start, 'to': end, 'faces': {face: {'texture': texture} for face in ['up', 'down', 'north', 'south', 'east', 'west']}}


def main():
    for stage, parent in [('bronze_anvil', 'anvil'), ('chipped_bronze_anvil', 'chipped_anvil'), ('damaged_bronze_anvil', 'damaged_anvil')]:
        write(ASSETS / f'models/block/{stage}.json', {'parent': f'minecraft:block/{parent}', 'textures': {'body': 'wwmc:block/bronze_block', 'particle': 'wwmc:block/bronze_block', 'top': 'wwmc:block/bronze_block'}})
    variants = {}
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        for wear in range(12):
            model = 'bronze_anvil' if wear < 4 else 'chipped_bronze_anvil' if wear < 8 else 'damaged_bronze_anvil'
            variants[f'facing={facing},wear={wear}'] = {'model': f'wwmc:block/{model}', 'y': angle}
    write(ASSETS / 'blockstates/bronze_anvil.json', {'variants': variants})
    item('bronze_anvil', {'parent': 'wwmc:block/bronze_anvil'})
    item('research_scroll', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/globe_banner_pattern'}})
    write(DATA / 'wwmc/recipe/bronze_anvil.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['BBB', ' I ', 'III'],
                                                'key': {'B': 'wwmc:bronze_ingot', 'I': 'minecraft:copper_ingot'}, 'result': {'id': 'wwmc:bronze_anvil'}})
    station_recipe = json.loads((DATA / 'wwmc/recipe/blacksmith_station.json').read_text())
    station_recipe['key']['I'] = 'minecraft:copper_ingot'
    write(DATA / 'wwmc/recipe/blacksmith_station.json', station_recipe)
    write(DATA / 'wwmc/loot_table/blocks/bronze_anvil.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'wwmc:bronze_anvil',
          'functions': [{'function': 'minecraft:copy_state', 'block': 'wwmc:bronze_anvil', 'properties': ['wear']}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for tool in ['pickaxe']:
        tag(DATA / f'minecraft/tags/block/mineable/{tool}.json', ['wwmc:bronze_anvil'])
    tag(DATA / 'wwmc/tags/item/requires_bronze.json', ['wwmc:bronze_anvil', 'wwmc:blacksmith_station'])
    iron_tag = DATA / 'wwmc/tags/item/requires_iron.json'
    old = json.loads(iron_tag.read_text()); old['values'] = [v for v in old['values'] if v != 'wwmc:blacksmith_station']; write(iron_tag, old)
    gear = [f'wwmc:bronze_{part}' for part in ['sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots']]
    for metal in ['copper', 'iron', 'golden', 'diamond', 'netherite']:
        gear += [{'id': f'minecraft:{metal}_{part}', 'required': False} for part in ['sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots', 'spear']]
    gear += ['minecraft:bucket', 'minecraft:shears', 'minecraft:shield', 'minecraft:crossbow', 'minecraft:mace']
    write(DATA / 'wwmc/tags/item/forged_equipment.json', {'replace': False, 'values': gear})
    name = 'gatherer_station'
    elements = [box([1, 0, 1], [15, 3, 15], '#wood'), box([1, 3, 1], [3, 11, 15], '#wood'), box([13, 3, 1], [15, 11, 15], '#wood'),
                box([3, 3, 1], [13, 11, 3], '#wood'), box([3, 3, 13], [13, 11, 15], '#wood'), box([3, 3, 3], [9, 9, 13], '#sand'),
                box([9, 3, 3], [13, 9, 13], '#gravel'), box([4, 9, 8], [5, 16, 9], '#handle'), box([3, 12, 7.5], [6, 15, 9.5], '#shovel')]
    write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/block', 'textures': {'particle': 'minecraft:block/oak_planks', 'wood': 'minecraft:block/oak_planks',
          'sand': 'minecraft:block/sand', 'gravel': 'minecraft:block/gravel', 'handle': 'minecraft:block/stripped_birch_log', 'shovel': 'minecraft:block/cobblestone'}, 'elements': elements})
    write(ASSETS / f'blockstates/{name}.json', {'variants': {f'facing={facing}': {'model': f'wwmc:block/{name}', 'y': angle} for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    item(name, {'parent': f'wwmc:block/{name}'})
    write(DATA / f'wwmc/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PPP', 'PSP', 'PPP'],
          'key': {'P': '#minecraft:planks', 'S': 'minecraft:stone_shovel'}, 'result': {'id': f'wwmc:{name}'}})
    loot = (DATA / 'wwmc/loot_table/blocks/lumber_station.json').read_text().replace('lumber_station', name)
    write(DATA / f'wwmc/loot_table/blocks/{name}.json', json.loads(loot))
    tag(DATA / 'minecraft/tags/block/mineable/axe.json', [f'wwmc:{name}'])
    lang_path = ASSETS / 'lang/en_us.json'; lang = json.loads(lang_path.read_text())
    lang.update({'block.wwmc.bronze_anvil': 'Bronze Anvil', 'item.wwmc.research_scroll': 'Research Scroll', 'block.wwmc.gatherer_station': 'Gatherer Station',
                 'tooltip.wwmc.research_scroll': 'Written by researchers at lecterns. Spend at your town banner or trade with another settlement.'})
    write(lang_path, lang)


if __name__ == '__main__':
    main()
