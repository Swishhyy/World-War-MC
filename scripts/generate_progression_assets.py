"""Generate progression assets; recolor vanilla textures for bronze and tin.

Requires Python, Pillow and the official Minecraft 26.2 client JAR. Colors change;
pixel positions, tool handles, rock backgrounds, transparency and armor UVs stay intact.
"""
import argparse
import hashlib
from io import BytesIO
from pathlib import Path
import json
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/wwmc'
DATA = ROOT / 'data'
CLIENT_SHA1 = '2dc72797acbc1b63fc16a11c4ac393605f453754'
PALETTES = {
    'bronze': [(24, (24, 24, 24)), (68, (73, 46, 24)), (107, (117, 73, 32)),
               (150, (167, 106, 48)), (190, (201, 140, 65)), (216, (223, 167, 84)), (255, (255, 220, 146))],
    'tin': [(24, (24, 24, 24)), (68, (55, 70, 78)), (107, (90, 112, 120)),
            (150, (140, 164, 173)), (190, (181, 204, 213)), (216, (208, 225, 232)), (255, (240, 249, 255))],
    # Copper powder colors sampled from the approved gunpowder recolor preview.
    'blend': [(0, (0, 0, 0)), (45, (80, 35, 15)), (62, (99, 47, 23)),
              (73, (119, 57, 32)), (80, (130, 61, 34)), (84, (148, 70, 38)),
              (114, (207, 105, 58)), (138, (253, 138, 83)), (255, (255, 220, 146))],
}
# destination, vanilla source, palette, optional pixels to preserve
TEXTURE_SOURCES = [
    ('item/raw_tin', 'item/raw_iron', 'tin', None),
    ('item/tin_ingot', 'item/iron_ingot', 'tin', None),
    ('item/bronze_blend', 'item/gunpowder', 'blend', None),
    ('item/bronze_ingot', 'item/iron_ingot', 'bronze', None),
] + [
    ('item/bronze_' + part, 'item/iron_' + part, 'bronze', 'wood' if part in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe') else None)
    for part in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots')
] + [
    ('block/tin_ore', 'block/iron_ore', 'tin', 'block/stone'),
    ('block/deepslate_tin_ore', 'block/deepslate_iron_ore', 'tin', 'block/deepslate'),
    ('block/raw_tin_block', 'block/raw_iron_block', 'tin', None),
    ('block/tin_block', 'block/iron_block', 'tin', None),
    ('block/bronze_block', 'block/iron_block', 'bronze', None),
    ('entity/equipment/humanoid/bronze', 'entity/equipment/humanoid/iron', 'bronze', None),
    ('entity/equipment/humanoid_leggings/bronze', 'entity/equipment/humanoid_leggings/iron', 'bronze', None),
]

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def tag(namespace, kind, name, values):
    write(DATA / namespace / 'tags' / kind / (name + '.json'), {'replace': False, 'values': values})

def recipe(name, value):
    write(DATA / 'wwmc/recipe' / (name + '.json'), value)

def unlock_metal_recipes():
    """Discover metal recipes from their materials, like vanilla's recipe book.

    Discovery explains the recipe; AgeProgression still checks permission when
    a player takes its result. No visible advancement or chat notice is added.
    """
    for path in sorted((DATA / 'wwmc/recipe').glob('*.json')):
        if not path.stem.startswith(('tin_', 'raw_tin_', 'bronze_')):
            continue
        value = json.loads(path.read_text())
        inputs = value.get('ingredients', list(value.get('key', {}).values()))
        if 'ingredient' in value:
            inputs = [value['ingredient']]
        materials = sorted({item for item in inputs if isinstance(item, str)
                            and item.startswith(('wwmc:tin_', 'wwmc:deepslate_tin_', 'wwmc:raw_tin', 'wwmc:bronze_'))})
        if path.stem == 'bronze_blend':
            materials.append('minecraft:copper_ingot')
        if not materials:
            raise ValueError('Missing recipe discovery material: ' + path.stem)
        recipe_id = 'wwmc:' + path.stem
        write(DATA / 'wwmc/advancement/recipes/misc' / path.name, {
            'parent':'minecraft:recipes/root',
            'criteria':{
                'has_material':{'trigger':'minecraft:inventory_changed',
                                'conditions':{'items':[{'items':materials}]}},
                'has_the_recipe':{'trigger':'minecraft:recipe_unlocked',
                                  'conditions':{'recipe':recipe_id}}},
            'requirements':[['has_material', 'has_the_recipe']],
            'rewards':{'recipes':[recipe_id]}})

def shaped(name, pattern, key, result=None, count=1):
    recipe(name, {'type':'minecraft:crafting_shaped', 'category':'misc', 'pattern':pattern, 'key':key,
                  'result':{'id':result or 'wwmc:' + name, 'count':count}})

def shapeless(name, ingredients, result, count=1):
    recipe(name, {'type':'minecraft:crafting_shapeless', 'category':'misc', 'ingredients':ingredients,
                  'result':{'id':result, 'count':count}})

def item_model(name, texture=None, parent=None):
    model = {'parent':parent} if parent else {'parent':'minecraft:item/handheld' if name.startswith('bronze_') and name.split('_')[-1] in ('sword','pickaxe','axe','shovel','hoe') else 'minecraft:item/generated',
                                             'textures':{'layer0':texture or 'wwmc:item/' + name}}
    write(ASSETS / 'models/item' / (name + '.json'), model)
    write(ASSETS / 'items' / (name + '.json'), {'model':{'type':'minecraft:model','model':'wwmc:item/' + name}})

def save_texture(name, image, folder='item'):
    path = ASSETS / 'textures' / folder / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def tinted(rgb, palette):
    light = round(rgb[0] * .299 + rgb[1] * .587 + rgb[2] * .114)
    if light <= palette[0][0]:
        return rgb  # Retain vanilla's dark outlines, including those around wooden handles.
    for (low, shadow), (high, highlight) in zip(palette, palette[1:]):
        if light <= high:
            fraction = (light - low) / (high - low)
            return tuple(round(a + (b - a) * fraction) for a, b in zip(shadow, highlight))
    return palette[-1][1]

def generate_textures(client_jar):
    """Swap colors only; the client hash fixes the exact vanilla source textures."""
    if hashlib.sha1(client_jar.read_bytes()).hexdigest() != CLIENT_SHA1:
        raise ValueError('Use the official Minecraft 26.2 client JAR; its SHA-1 must be ' + CLIENT_SHA1)
    with zipfile.ZipFile(client_jar) as archive:
        def source(name):
            return Image.open(BytesIO(archive.read('assets/minecraft/textures/' + name + '.png'))).convert('RGBA')
        for destination, vanilla, material, preserve in TEXTURE_SOURCES:
            original = source(vanilla)
            image = original.copy()
            rock_colors = set()
            if preserve and preserve != 'wood':
                rock = source(preserve)
                rock_colors = {rock.getpixel((x, y)) for y in range(rock.height) for x in range(rock.width)}
            changed = 0
            for y in range(image.height):
                for x in range(image.width):
                    red, green, blue, alpha = original.getpixel((x, y))
                    if not alpha or (red, green, blue, alpha) in rock_colors:
                        continue
                    if preserve == 'wood' and not red == green == blue:
                        continue
                    pixel = tinted((red, green, blue), PALETTES[material]) + (alpha,)
                    image.putpixel((x, y), pixel)
                    changed += pixel != original.getpixel((x, y))
            if destination == 'item/bronze_blend':
                # Four tin flecks recolor existing powder pixels without changing its silhouette.
                for x, y, color in [(8, 5, (166, 189, 214)), (4, 8, (165, 192, 217)),
                                    (11, 8, (150, 177, 202)), (5, 10, (173, 203, 223))]:
                    alpha = original.getpixel((x, y))[3]
                    if not alpha:
                        raise ValueError('Tin fleck must stay within the vanilla gunpowder sprite')
                    image.putpixel((x, y), color + (alpha,))
            if not changed or image.getchannel('A').tobytes() != original.getchannel('A').tobytes():
                raise ValueError('Invalid vanilla recolor: ' + destination)
            folder, name = destination.rsplit('/', 1)
            save_texture(name, image, folder)
    print('Recolored 20 vanilla textures; shapes, handles, rock backgrounds and armor UVs preserved.')

def cube(lo, hi, texture):
    return {'from':lo,'to':hi,'faces':{face:{'texture':'#'+texture} for face in ('down','up','north','south','west','east')}}

def block(name):
    write(ASSETS / 'models/block' / (name+'.json'), {'parent':'minecraft:block/cube_all','textures':{'all':'wwmc:block/'+name}})
    write(ASSETS / 'blockstates' / (name+'.json'), {'variants':{'':{'model':'wwmc:block/'+name}}})
    item_model(name,parent='wwmc:block/'+name)

def fish_carcass():
    """A whole fish with an intact head, eyes, fins and a forked tail."""
    parts=[cube([4.5,5,6.5],[10.5,10.5,9.5],'skin'),
           cube([2,6,6.25],[4.5,10,9.75],'skin'),
           cube([1.5,6.5,6.75],[2,9,9.25],'skin'),
           cube([10.5,6,7],[12,9.5,9],'skin'),
           cube([12,6.75,7.5],[13,8.75,8.5],'fin'),
           cube([4.5,5,6.48],[10.5,6,9.52],'belly'),
           cube([6,10,7.65],[9,12.5,8.35],'fin'),
           cube([7.5,3.5,7.65],[10,5.5,8.35],'fin'),
           cube([5.5,5.5,5.25],[8,6.1,6.6],'fin'),
           cube([5.5,5.5,9.4],[8,6.1,10.75],'fin')]
    for lo,hi,angle in [([12.75,7.5,7.5],[16,9.5,8.5],22.5),
                        ([12.75,6,7.5],[16,8,8.5],-22.5)]:
        tail=cube(lo,hi,'fin')
        tail['rotation']={'origin':[13,7.75,8],'axis':'z','angle':angle}
        parts.append(tail)
    for lo,hi in [([2.8,8.2,6.12],[4.1,9.5,6.27]),
                  ([2.8,8.2,9.73],[4.1,9.5,9.88])]:
        parts.append(cube(lo,hi,'eye_white'))
    for lo,hi in [([2.85,8.55,6.06],[3.6,9.3,6.15]),
                  ([2.85,8.55,9.85],[3.6,9.3,9.94])]:
        parts.append(cube(lo,hi,'eye'))
    parts.append(cube([1.46,7.1,7.1],[1.52,7.35,8.9],'eye'))
    write(ASSETS / 'models/item/fish_carcass.json', {
        'parent':'minecraft:block/block',
        'textures':{'particle':'#skin','belly':'minecraft:block/white_terracotta',
                    'eye_white':'minecraft:block/white_concrete','eye':'minecraft:block/black_concrete'},
        'elements':parts,
        'display':{
            'gui':{'rotation':[20,-25,-15],'scale':[.9,.9,.9]},
            'ground':{'rotation':[90,0,0],'translation':[0,2,0],'scale':[.65,.65,.65]},
            'fixed':{'rotation':[0,0,0],'scale':[.8,.8,.8]},
            'thirdperson_righthand':{'rotation':[0,90,-25],'translation':[0,2,0],'scale':[.55,.55,.55]},
            'thirdperson_lefthand':{'rotation':[0,-90,25],'translation':[0,2,0],'scale':[.55,.55,.55]},
            'firstperson_righthand':{'rotation':[0,-45,0],'translation':[0,2,0],'scale':[.75,.75,.75]},
            'firstperson_lefthand':{'rotation':[0,45,0],'translation':[0,2,0],'scale':[.75,.75,.75]}}})

def carcass(kind, skin):
    if kind in ('cod','salmon'):
        fish_carcass()
        write(ASSETS / 'models/item' / (kind+'_carcass.json'), {
            'parent':'wwmc:item/fish_carcass',
            'textures':{'skin':'minecraft:block/terracotta' if kind=='cod' else 'minecraft:block/red_terracotta',
                        'fin':skin}})
        return
    textures={'particle':skin,'skin':skin,'hoof':'minecraft:block/black_wool','flesh':'minecraft:block/white_terracotta'}
    small=kind in ('chicken','rabbit')
    parts=[cube([3,3,5],[10 if small else 12,8,11],'skin'),cube([10,3.5,5.5],[14,7.5,10.5],'skin')]
    for x in (4,8 if small else 10):
        for z in (3.5,10):parts.extend([cube([x,2,z],[x+1.5,4,z+2.5],'skin'),cube([x,2,z],[x+1.5,3,z+1],'hoof')])
    if kind in ('cow','sheep'):parts.extend([cube([12,7,5],[13,9,6],'flesh'),cube([12,7,10],[13,9,11],'flesh')])
    if kind=='rabbit':parts.extend([cube([12,7,6],[13,11,7],'skin'),cube([12,7,9],[13,11,10],'skin')])
    if kind=='chicken':parts.extend([cube([13,4,5],[15,6,6],'flesh'),cube([11,7.5,6],[13,9,7],'flesh')])
    model={'parent':'minecraft:block/block','textures':textures,'elements':parts,
           'display':{'gui':{'rotation':[25,-35,0],'translation':[0,1,0],'scale':[.95,.95,.95]},
                      'ground':{'translation':[0,1,0],'scale':[.6,.6,.6]},'fixed':{'rotation':[0,90,0],'scale':[.8,.8,.8]},
                      'thirdperson_righthand':{'rotation':[70,0,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
                      'firstperson_righthand':{'rotation':[0,-30,0],'translation':[0,2,0],'scale':[.75,.75,.75]}}}
    write(ASSETS / 'models/item' / (kind+'_carcass.json'),model)

def main(client_jar, textures_only=False):
    generate_textures(client_jar)
    if textures_only:
        return
    names=['raw_tin','tin_ingot','bronze_blend','bronze_ingot']+['bronze_'+part for part in ['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots']]
    for name in names:
        item_model(name)
    for name in ('tin_ore','deepslate_tin_ore','raw_tin_block','tin_block','bronze_block'):
        block(name)
        entry={'type':'minecraft:item','name':'wwmc:'+name}
        if name.endswith('ore'):
            entry={'type':'minecraft:alternatives','children':[
                {'type':'minecraft:item','name':'wwmc:'+name,'conditions':[{'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}]},
                {'type':'minecraft:item','name':'wwmc:raw_tin','functions':[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'function':'minecraft:explosion_decay'}]}]}
        write(DATA / 'wwmc/loot_table/blocks' / (name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for base in ('tin','bronze'):
        shaped(base+'_block',['III','III','III'],{'I':'wwmc:'+base+'_ingot'})
        shapeless(base+'_ingot_from_block',['wwmc:'+base+'_block'],'wwmc:'+base+'_ingot',9)
    shaped('raw_tin_block',['III','III','III'],{'I':'wwmc:raw_tin'})
    shapeless('raw_tin_from_block',['wwmc:raw_tin_block'],'wwmc:raw_tin',9)
    shapeless('bronze_blend',['minecraft:copper_ingot']*3+['wwmc:tin_ingot'],'wwmc:bronze_blend',4)
    for name,ingredient,result in [('tin_from_raw','wwmc:raw_tin','wwmc:tin_ingot'),('tin_from_ore','wwmc:tin_ore','wwmc:tin_ingot'),
                                    ('tin_from_deepslate','wwmc:deepslate_tin_ore','wwmc:tin_ingot'),('bronze_from_blend','wwmc:bronze_blend','wwmc:bronze_ingot')]:
        for cooking,duration in [('smelting',200),('blasting',100)]:
            recipe(name+'_'+cooking,{'type':'minecraft:'+cooking,'category':'misc','ingredient':ingredient,'result':{'id':result},'experience':.7,'cookingtime':duration})
    patterns={'sword':[' I ',' I ',' S '],'pickaxe':['III',' S ',' S '],'axe':['II ','IS ',' S '],
              'shovel':[' I ',' S ',' S '],'hoe':['II ',' S ',' S '],'helmet':['III','I I'],'chestplate':['I I','III','III'],
              'leggings':['III','I I','I I'],'boots':['I I','I I']}
    for part,pattern in patterns.items():
        key={'I':'wwmc:bronze_ingot'}
        if any('S' in row for row in pattern):key['S']='minecraft:stick'
        shaped('bronze_'+part,pattern,key)
    unlock_metal_recipes()
    shaped('researcher_station',['PPP','PIP','PPP'],{'P':'#minecraft:planks','I':'minecraft:lectern'})
    for role,center in [('guard','minecraft:wooden_sword'),('barracks','minecraft:stone_sword'),('butcher','minecraft:wooden_axe'),('quarry','wwmc:bronze_pickaxe')]:
        shaped(role+'_station',['PPP','PIP','PPP'],{'P':'#minecraft:planks','I':center})
    elements=[cube([1,0,1],[3,12,3],'wood'),cube([13,0,1],[15,12,3],'wood'),cube([1,0,13],[3,12,15],'wood'),cube([13,0,13],[15,12,15],'wood'),
              cube([0,11,0],[16,13,16],'wood'),cube([2,2,4],[14,10,13],'shelf'),cube([4,13,5],[12,13.5,11],'cover'),
              cube([4.5,13.5,5.5],[7.75,14.3,10.5],'page'),cube([8.25,13.5,5.5],[11.5,14.3,10.5],'page'),cube([13,13,3],[14.5,15,4.5],'ink')]
    write(ASSETS/'models/block/researcher_station.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/bookshelf','wood':'minecraft:block/oak_planks','shelf':'minecraft:block/bookshelf','cover':'minecraft:block/blue_wool','page':'minecraft:block/white_wool','ink':'minecraft:block/black_wool'},'elements':elements})
    write(ASSETS/'blockstates/researcher_station.json',{'variants':{'facing='+direction:{'model':'wwmc:block/researcher_station','y':angle} for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    item_model('researcher_station',parent='wwmc:block/researcher_station')
    write(DATA/'wwmc/loot_table/blocks/researcher_station.json',json.loads((DATA/'wwmc/loot_table/blocks/enchanter_station.json').read_text().replace('enchanter_station','researcher_station')))
    for kind,skin in [('cow','brown_wool'),('pig','pink_wool'),('sheep','white_wool'),('chicken','white_wool'),('rabbit','brown_wool'),('cod','brown_terracotta'),('salmon','pink_terracotta')]:carcass(kind,'minecraft:block/'+skin)
    write(ASSETS/'equipment/bronze.json',{'layers':{'humanoid':[{'texture':'wwmc:bronze'}],'humanoid_leggings':[{'texture':'wwmc:bronze'}]}})
    for namespace,kind,name,values in [('c','item','ingots/tin',['wwmc:tin_ingot']),('c','item','ingots/bronze',['wwmc:bronze_ingot']),
         ('c','item','ingots',['#c:ingots/tin','#c:ingots/bronze']),('c','block','ores/tin',['wwmc:tin_ore','wwmc:deepslate_tin_ore']),
         ('c','block','ores',['#c:ores/tin']),('c','item','ores/tin',['wwmc:tin_ore','wwmc:deepslate_tin_ore']),('c','item','ores',['#c:ores/tin']),
         ('c','item','raw_materials/tin',['wwmc:raw_tin']),('c','item','raw_materials',['#c:raw_materials/tin'])]:tag(namespace,kind,name,values)
    tag('minecraft','block','mineable/pickaxe',['wwmc:'+n for n in ['tin_ore','deepslate_tin_ore','raw_tin_block','tin_block','bronze_block','bronze_snare','bronze_caltrops','iron_spring_trap']])
    tag('minecraft','block','needs_stone_tool',['wwmc:tin_ore','wwmc:deepslate_tin_ore','wwmc:tin_block','wwmc:raw_tin_block','wwmc:bronze_block'])
    for part,group in [('sword','swords'),('pickaxe','pickaxes'),('axe','axes'),('shovel','shovels'),('hoe','hoes')]:tag('minecraft','item',group,['wwmc:bronze_'+part])
    for enchant,parts in [('sword',['sword']),('sharp_weapon',['sword','axe']),('mining',['pickaxe','axe','shovel','hoe']),('mining_loot',['pickaxe','axe','shovel','hoe']),('durability',list(patterns)),
                         ('armor',['helmet','chestplate','leggings','boots']),('head_armor',['helmet']),('chest_armor',['chestplate']),('leg_armor',['leggings']),('foot_armor',['boots'])]:
        tag('minecraft','item','enchantable/'+enchant,['wwmc:bronze_'+p for p in parts])
    gear=['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots','spear']
    def optional(id):return {'id':id,'required':False}
    tag('wwmc','item','requires_bronze',['wwmc:bronze_'+p for p in list(patterns)]+['wwmc:bronze_blend','wwmc:bronze_ingot','wwmc:bronze_block','wwmc:quarry_station']+[optional('minecraft:copper_'+p) for p in gear]+['wwmc:bronze_snare','wwmc:bronze_caltrops'])
    tag('wwmc','item','requires_iron',[optional('minecraft:'+m+'_'+p) for m in ('iron','golden') for p in gear]+['minecraft:chainmail_'+p for p in ['helmet','chestplate','leggings','boots']]
        +['minecraft:bucket','minecraft:water_bucket','minecraft:lava_bucket','minecraft:milk_bucket','minecraft:powder_snow_bucket','minecraft:cod_bucket','minecraft:salmon_bucket','minecraft:pufferfish_bucket','minecraft:tropical_fish_bucket','minecraft:axolotl_bucket','minecraft:tadpole_bucket',
          'minecraft:shears','minecraft:flint_and_steel','minecraft:shield','minecraft:crossbow','minecraft:trident','minecraft:anvil','minecraft:chipped_anvil','minecraft:damaged_anvil','minecraft:blast_furnace','minecraft:smithing_table','wwmc:blacksmith_station','wwmc:iron_spring_trap'])
    tag('wwmc','item','requires_gemcraft',[optional('minecraft:diamond_'+p) for p in gear]+['minecraft:enchanting_table','minecraft:mace','wwmc:enchanter_station'])
    tag('wwmc','item','requires_netherite',[optional('minecraft:netherite_'+p) for p in gear])
    configured={'type':'minecraft:ore','config':{'size':7,'discard_chance_on_air_exposure':0.0,'targets':[
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'wwmc:tin_ore'}},
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'wwmc:deepslate_tin_ore'}}]}}
    write(DATA/'wwmc/worldgen/configured_feature/tin_ore.json',configured)
    write(DATA/'wwmc/worldgen/placed_feature/tin_ore.json',{'feature':'wwmc:tin_ore','placement':[{'type':'minecraft:count','count':8},{'type':'minecraft:in_square'},
        {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-32},'max_inclusive':{'absolute':64}}},{'type':'minecraft:biome'}]})
    write(DATA/'wwmc/neoforge/biome_modifier/tin_ore.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':'wwmc:tin_ore','step':'underground_ores'})
    lang_path=ASSETS/'lang/en_us.json'; lang=json.loads(lang_path.read_text())
    for name in names:lang['item.wwmc.'+name]=name.replace('_',' ').title()
    for name in ['tin_ore','deepslate_tin_ore','raw_tin_block','tin_block','bronze_block','researcher_station']:lang['block.wwmc.'+name]=name.replace('_',' ').title()
    write(lang_path,lang)

if __name__=='__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client-jar', type=Path, required=True, help='Official Minecraft 26.2 client JAR')
    parser.add_argument('--textures-only', action='store_true', help='Recolor textures without regenerating models or gameplay data')
    args = parser.parse_args()
    main(args.client_jar, args.textures_only)
