#!/usr/bin/env python3
"""Reuse native iron silhouettes for steel, and furnace models for the alloy furnace."""
import argparse
import json
import zipfile
from pathlib import Path
import generate_progression_assets as progression
from generate_settlement_production_assets import tag, write

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/wwmc'
DATA=ROOT/'data'
PARTS=['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots']

def main(client_jar):
    progression.PALETTES['steel']=[(24,(24,24,24)),(68,(44,52,60)),(107,(76,88,100)),(150,(113,129,145)),(190,(153,170,186)),(216,(192,207,222)),(255,(232,243,255))]
    progression.TEXTURE_SOURCES=[('item/steel_ingot','item/iron_ingot','steel',None)]+[
        ('item/steel_'+part,'item/iron_'+part,'steel','wood' if part in PARTS[:5] else None) for part in PARTS]+[
        ('entity/equipment/humanoid/steel','entity/equipment/humanoid/iron','steel',None),
        ('entity/equipment/humanoid_leggings/steel','entity/equipment/humanoid_leggings/iron','steel',None),
        ('block/alloy_furnace_front','block/blast_furnace_front','bronze',None),
        ('block/alloy_furnace_front_on','block/blast_furnace_front_on','bronze',None)]
    progression.generate_textures(Path(client_jar))
    with zipfile.ZipFile(client_jar) as archive:
        write(ASSETS/'textures/block/alloy_furnace_front_on.png.mcmeta',json.loads(archive.read('assets/minecraft/textures/block/blast_furnace_front_on.png.mcmeta')))
    for name in ['ingot']+PARTS:
        for folder in ['items','models/item']:
            original=ASSETS/f'{folder}/bronze_{name}.json'
            write(ASSETS/f'{folder}/steel_{name}.json',json.loads(original.read_text().replace('bronze','steel')))
        if name!='ingot':
            write(DATA/f'wwmc/recipe/steel_{name}.json',json.loads((DATA/f'wwmc/recipe/bronze_{name}.json').read_text().replace('bronze','steel')))
            write(DATA/f'wwmc/advancement/recipes/misc/steel_{name}.json',json.loads((DATA/f'wwmc/advancement/recipes/misc/bronze_{name}.json').read_text().replace('bronze','steel')))
    write(ASSETS/'equipment/steel.json',{'layers':{'humanoid':[{'texture':'wwmc:steel'}],'humanoid_leggings':[{'texture':'wwmc:steel'}]}})
    for path in (DATA/'minecraft/tags/item').rglob('*.json'):
        value=json.loads(path.read_text()); added=[v.replace('bronze_','steel_') for v in value.get('values',[]) if isinstance(v,str) and v.startswith('wwmc:bronze_')]
        if added: tag(path,added)
    gear=['wwmc:steel_'+p for p in PARTS]
    tag(DATA/'wwmc/tags/item/forged_equipment.json',gear)
    tag(DATA/'wwmc/tags/item/requires_steel.json',['wwmc:steel_ingot']+gear)
    tag(DATA/'c/tags/item/ingots/steel.json',['wwmc:steel_ingot'])
    tag(DATA/'c/tags/item/ingots.json',['#c:ingots/steel'])
    tag(DATA/'minecraft/tags/block/mineable/pickaxe.json',['wwmc:alloy_furnace'])
    tag(DATA/'wwmc/tags/item/requires_bronze.json',['wwmc:alloy_furnace'])
    for lit in [False,True]:
        name='alloy_furnace_on' if lit else 'alloy_furnace'
        write(ASSETS/f'models/block/{name}.json',{'parent':'minecraft:block/orientable','textures':{
            'top':'minecraft:block/copper_block','side':'minecraft:block/blast_furnace_side','front':'wwmc:block/alloy_furnace_front'+('_on' if lit else '')}})
    write(ASSETS/'blockstates/alloy_furnace.json',{'variants':{
        f'facing={facing},lit={str(lit).lower()}':{'model':'wwmc:block/alloy_furnace'+('_on' if lit else ''),'y':angle}
        for facing,angle in [('north',0),('east',90),('south',180),('west',270)] for lit in [False,True]}})
    write(ASSETS/'models/item/alloy_furnace.json',{'parent':'wwmc:block/alloy_furnace'})
    write(ASSETS/'items/alloy_furnace.json',{'model':{'type':'minecraft:model','model':'wwmc:item/alloy_furnace'}})
    write(DATA/'wwmc/recipe/alloy_furnace.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['CCC','IFI','CCC'],
          'key':{'C':'minecraft:cobblestone','I':'minecraft:copper_ingot','F':'minecraft:furnace'},'result':{'id':'wwmc:alloy_furnace'}})
    write(DATA/'wwmc/advancement/recipes/misc/alloy_furnace.json',{'parent':'minecraft:recipes/root','criteria':{
        'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:copper_ingot']}]}},
        'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'wwmc:alloy_furnace'}}},
        'requirements':[['has_material','has_the_recipe']],'rewards':{'recipes':['wwmc:alloy_furnace']}})
    write(DATA/'wwmc/loot_table/blocks/alloy_furnace.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'wwmc:alloy_furnace'}],
          'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    # Previously made blends still smelt. New alloys do not need a crafting-table blend.
    for path in [DATA/'wwmc/recipe/bronze_blend.json',DATA/'wwmc/advancement/recipes/misc/bronze_blend.json']:
        path.unlink(missing_ok=True)
    lang_path=ASSETS/'lang/en_us.json'; lang=json.loads(lang_path.read_text())
    lang['block.wwmc.alloy_furnace']='Alloy Furnace'
    for name in ['ingot']+PARTS: lang['item.wwmc.steel_'+name]='Steel '+name.title()
    lang['tooltip.wwmc.bronze_blend.craft']='Legacy blend: already-made blends can still be smelted.'
    lang['tooltip.wwmc.bronze_blend.smelt']='New bronze comes directly from an Alloy Furnace: 3 copper + 1 tin + separate fuel.'
    lang['tooltip.wwmc.bronze_blend.research']='Alloy furnaces need Bronze Age research in their settlement.'
    write(lang_path,lang)
    print('Generated steel gear and the alloy furnace; legacy blend smelting retained.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--client-jar',required=True)
    main(parser.parse_args().client_jar)
