# Settlement production and wellbeing

[Project overview](../README.md) · [All guides](README.md)

The version remains **0.1.0.0**.

## Build the supply chain

1. Provide housing, a warehouse, a farm and a courier. Keep ready-to-eat meals available.
2. Add a Researcher Station and lectern. Researchers write a scroll in **30 seconds**, spending **2 paper + 1 ink sac or charcoal**. Set the stock target in **Research**; zero pauses new scrolls.
3. Gatherers collect cane, bamboo, sand, gravel and clay using shovels. Use **Production → Workshop** for paper, job blocks, furniture and supplies.
4. Keep each job's barrel outside warehouse range. Couriers move ingredients out and finished goods home. Normal furnaces refine ore, charcoal, glass, bricks and terracotta.

## Make bronze and steel

After **Bronze Age**, craft an **Alloy Furnace** from a furnace, 6 cobblestone and 2 copper ingots. Place it inside the researched settlement, beside a Smeltery Station with a barrel. It has **two material inputs, a separate fuel slot and one output**. Ingots, raw metals and ores work; fuel is always extra.

| Alloy | First input | Second input | Output | Loaded work time |
| --- | --- | --- | --- | --- |
| Bronze | 3 copper | 1 tin | 4 bronze ingots | 20 seconds |
| Steel | 1 iron | 1 coal or charcoal | 1 steel ingot | 30 seconds |

Steel requires **Iron Age → Steelworking** and a staffed smeltery with an alloy furnace. Steel tools last 500 uses and mine faster than iron, while remaining below diamond in speed and mining tier. Steel armor improves iron's durability and protection without replacing diamond.

Smelters service real appliances; couriers deliver ingredients and fuel. Set alloy stock targets in **Production → Metalwork**; the default is 32 for each alloy and zero pauses new automated batches. Already loaded batches can finish. Hoppers feed both material slots from above, fuel from the sides, and extract output from below. Full output pauses new batches; inventory, fuel and partial progress survive saving. Outside a settlement or before research, the furnace explains its lock and does not consume new fuel or materials.

New bronze no longer needs a crafted blend or a second smelting step. Previously made blends still smelt normally. Saved smith alloy/refining orders retain their targets and are serviced by smelters.

## Forge and repair equipment

Bronze Age unlocks the copper-based Blacksmith Station. Supply an anvil, a nearby furnace, a barrel and coal/charcoal. A bronze anvil uses **3 bronze ingots across the top, 1 copper ingot in the centre and 3 copper ingots across the bottom**.

Blacksmiths repair damaged gear first, keeping its original name, enchantments and components. They then fill equipment targets in **Production → Metalwork** using native recipe ingredients and one coal/charcoal per operation. Native netherite upgrading uses a template, the original diamond gear and a netherite ingot.

Bronze anvils work **65% slower than iron**, at 35% of its speed, for repairs and forging. Both use normal anvil wear: a 12% chance per completed operation to advance a wear stage. Moving an anvil preserves its saved wear. Walking, meals and deliveries add time to each job.

Wood and stone equipment remain player-crafted. Bronze, copper, steel and later gear must come from a blacksmith. Blocked player crafting and redstone crafters keep ingredients intact. Found equipment can be stored until settlement research permits its use.

## Happy homes and children

**People → Wellbeing** shows happiness, housing amenities, children and growth requirements. Each citizen's profile shows why their happiness is changing. Happiness is saved from 0–100, starts at 50 and moves gradually toward the conditions around them.

Variety counts meals **actually eaten**, not food sitting in storage. Citizens prefer less recently eaten available foods and keep bowl/bottle returns. Enough housing, safety and distinct amenities help; hunger, crowded housing, injuries and danger hurt. Furnished housing and barracks can provide four amenity types: a garden, meeting bell, lit campfire and books. Multiple flowers or shelves do not multiply the same amenity bonus.

A town gets **one 5% birth chance per loaded minute**. It needs two healthy, fed adults with happiness at least 75 and three recently eaten food types, a spare housing bed, population room and safety. A birth uses **6 warehouse meals**, leaving at least two meals per resident including the newborn. Parents have a five-minute breeding cooldown. Growth can be paused at the banner; births appear in the town journal without chat spam.

A newborn is an actual baby citizen. It plays near its housing, eats, shelters when frightened and rests at night. It takes **no job, guard post or squad role** until native adulthood after **20 loaded minutes**. Children count toward housing and the population cap, but not available workers. Native age, homes, happiness, child membership and the remaining birth cooldown survive saving. Unloaded towns do not accumulate birth rolls or offline aging. NPC towns use the same birth rules after their initial crew is founded.

Better axes, hoes and shovels give bounded bonuses to lumberjacks, farmers and gatherers. Farmers can still work without hoes; available hoes improve speed and wear with actual harvests. Bronze and steel use their native tool components. Miners retain their existing pickaxe-based breaking and replenishment speed.

## Research and exploration

Spend researcher-made scrolls and the listed supplies once per discovery. Accepted settlement members share research; allies keep their own progress. Bronze Age needs three citizens, Iron Age a staffed furnished smithy, and Steelworking a staffed alloy smeltery. Existing unlocks and previously paid timed projects are retained.

Ruined town halls demonstrate real station setups and contain salvageable settlement blocks, a bronze smithy and finite scroll loot. Saved ruins are never rebuilt or restocked.

| Banner destination | Use it for |
| --- | --- |
| People | Citizens, jobs, wellbeing, children and population |
| Production | Stock requests, workshops, alloy targets, equipment and stations |
| Research | Scroll targets, pause reasons, prerequisites and discoveries |
| Neighbours | Caravans, contracts, alliances and quiet town news |

[Education design](education-design.md) refines the proposed schooling and apprenticeship progression. Education gates are not enabled in this update.
