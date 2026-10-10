# Settlement guide

[Project overview](../README.md) · [All guides](README.md) · [Multiplayer and campaign](multiplayer-campaign.md)

This is the detailed reference for the playable settlement systems. For a shorter introduction, use the [README's first-town steps](../README.md#get-started) or the in-game Settlement Guide.

| Looking for | Go to |
| --- | --- |
| Your first town | [Start a settlement](#start-a-settlement) |
| Station setup and recipes | [Job blocks](#job-blocks-define-building-purpose) · [Crafting](#crafting) |
| Trees, tunnels and quarries | [Forestry](#forestry-and-construction-protection) · [Mining](#tunnel-mines-and-quarries) |
| Storage and deliveries | [Inventories](#personal-inventories) · [Job barrels and couriers](#job-barrels-and-couriers) |
| Meals and injured citizens | [Food](#food-and-healing) · [Hospital recovery](production-recovery.md#hospital-recovery) |
| Guards and attacks | [Guard stations](#guard-stations-and-posts) · [Alarms and waves](#alarms-and-enemy-waves) |
| Other towns | [Trading](#trading-and-other-settlements) · [Relationships](multiplayer-campaign.md#relationships-claims-and-flags) |
| Progress and troubleshooting | [Needs](#needs-experience-and-morale) · [Station upgrades](#station-upgrades) · [Population](#population) |
| Player equipment and ages | [Ages and research](#ages-and-research) |
| Commands and settings | [Commands](#commands) · [Server configuration](#server-configuration) |

## Start a settlement

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this development build. Craft a **Settlement Guide** from one book and one blue dye, or run `/wwmc guide`, then right-click it to read the instructions.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. New towns extend **240 blocks in each horizontal direction**, a 481×481 block footprint including the center. Banners matching your town color appear at the four corners when those chunks are loaded and the ground can support a banner. The mod does not load distant chunks to place them or replace obstructing blocks.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Beds are detected automatically within **three blocks of the station on every axis**: a **7×7×7 cube**, including the station block. Both halves of each bed must fit inside the cube and your claim. No corner selection is required.
4. Place a **Warehouse Station** within that same range of your chests or barrels. It detects multiple containers, including trapped and double chests. Stock food, axes, appropriate pickaxes, saplings, and cobblestone or other tunnel floor supplies. You can add or remove storage later without registering it again.
5. Place **Farm** and **Lumber Stations** with crops or tree roots inside their **7×7×7** ranges. Put barrels in their ranges and add a **Courier Station** to deliver tools and collect goods. Every job block takes **one worker**, except a quarry, which supports **eight** by default. Only quarries can buy more crew slots (see [Station upgrades](#station-upgrades)). Citizens reserve individual trees or excavation positions so a shared crew cannot harvest the same target twice. Placed logs and actual buildings remain protected; a nearby housing, hospital, barracks or warehouse scan range alone does not protect natural trees.
6. Prepare farmland and plant crops yourself. Carrots or potatoes supply both food and replanting stock; wheat is collected, and a cook makes it into bread. Put a lumber station by natural trees or accessible soil. It fells the connected tree, collects real leaf drops, and replants from saplings in its job barrel or worker's bag. Couriers bring warehouse supplies to the job barrel. If no tree is accessible, it can plant a new one instead. Trees grow at Minecraft's normal rate.
7. For mining, the simplest choice is a **Mine Station placed within two blocks of an exposed ore** (an ore with at least one open side): its miner works that ore as an endless vein. See [Ore veins](#ore-veins). A Mine Station with no ore beside it digs tunnels instead: place it facing into the intended descent, with open walking space in front. It chooses and saves a random depth between **Y −30 and 10**, then digs a staircase and eight 24-block side branches. Near that depth, workers also walk to accessible exposed cave ores they can reach. Alternatively, place a **Quarry Station** facing the neighboring chunk you want excavated. The quarry removes that complete 16×16 chunk from the surface down to Y −64, preserving bedrock. Keep the station and level, walkable ground outside the target chunk, in line with the station, where the crew's staircase begins. The whole plan must fit inside the town claim.
8. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the town's population limit: 10 citizens at first, raised with emeralds on the town screen (see [Population](#population)). Each citizen takes the open job of highest priority and keeps it (see [Jobs and priorities](#jobs-and-priorities)), obtains supplies, works, and delivers cargo in batches.
9. Right-click your citizen with an empty hand to open their screen: job, current activity, health, next meal and equipment above their **36-slot bag**. Add food, spare tools, saplings, or guard gear directly. Sneak-right-click releases their job, and they take another open place; holding an item while interacting shows their status above the hotbar. Right-click the banner for the town screen. Mine depths are automatic.
10. Place a **Guard Station**, supply armor stands in its local range, and configure its day/night posts as described below. One citizen becomes its guard automatically; defense slots fill before production jobs. Add more Guard Stations for more defenders or expedition soldiers.
11. Place a **Craftsman Station** near your warehouse and teach it what to make (see [Craftsmen](#craftsmen)). Add a **Smeltery Station** with a furnace or blast furnace in range, and a **Cook Station** with a furnace, smoker or lit campfire in range. Stock raw ores, raw food, wheat, and fuel in the warehouse. See [Smelters and cooks](#smelters-and-cooks).
12. Add a **Blacksmith Station** with an anvil and job barrel within three blocks on each axis. Stock repair materials in the warehouse for couriers to deliver. Blacksmiths repair damaged tools/weapons and worn guard armor, retaining names and enchantments.
13. Hang a **bell** inside the town so guards can raise the alarm, and prepare for the first enemy wave once the town has three citizens. See [Alarms and enemy waves](#alarms-and-enemy-waves).
14. Every production station needs **barrels** in range and **couriers** to move goods and supplies. Add hunters or keepers, a butcher, and a cook for meat, or a fisherman beside suitable water. See [Animal food jobs](#animal-food-jobs) and [Job barrels and couriers](#job-barrels-and-couriers).
15. Place an **Enchanter Station** within five blocks of an enchanting table surrounded by bookshelves, and stock lapis lazuli. See [Enchanters](#enchanters).
16. Spend spare emeralds on the stations that matter most and on room for more citizens. See [Station upgrades](#station-upgrades).

**Range preview:** hold any station block and aim at a block face to see a blue outline at its prospective placement position. The outline accounts for replaceable grass/snow. It turns red when the placement context is blocked. Placing a station displays a green outline for about three seconds. Right-click an existing station with an empty hand to open its screen and briefly show its range. The **Station Inspector** also previews an existing station while you aim at it and opens its screen when right-clicked.

Ordinary stations show their local 7×7×7 area, or their upgraded range; an Enchanter Station shows 11×11×11; a quarry shows the neighboring chunk's footprint. The quarry outline indicates its horizontal target, not the complete depth. Mines extend beyond the local outline along their planned tunnels. Inspection reports facing, depth, progress, and active crew size.

Keep doors and paths accessible. Stations are solid blocks; citizens need to reach a neighboring block. A full warehouse, missing tools, inaccessible resources, or missing food produces a visible worker status instead of creating supplies out of thin air.

### Forestry and construction protection

Workers mine, harvest, plant, and use stations from up to **four blocks away**, while still requiring a clear view of the work. Lumberjacks approach clear ground with headroom and firm footing outside the canopy. If nearby natural leaves obstruct a selected tree or intersect the worker, they cut one reachable leaf at a time before felling the tree. Leaves from neighboring tree species can also be cleared in a mixed forest. Decorative/persistent leaves, player-placed blocks, protected furniture, and other citizens’ footing remain protected. Walls still block work and melee attacks. The previous 30-second stuck rescue remains a fallback.

Player-placed solid blocks are recorded from this version onward, even before a town is founded. Lumberjacks reject a tree if any connected log is recorded as player-placed, if logs enter a protected building range, or if the tree touches construction such as planks or unrelated block entities. The owning Lumber Station and its job barrels may sit directly beside a natural tree: they stay intact when it is felled. Recognition also requires rooted trunks and non-decorative leaves. Felling follows the complete connected trunk and branches beyond the local detection cube, within bounded size, claim, and loaded-chunk limits. Unloaded or ambiguous trees are skipped rather than partly cut.

Placement history cannot be recovered for buildings made before this feature was installed. For an older build, **right-click its logs with the Station Inspector** to protect the connected logs in your claim. Decorative trees deliberately built by the player are protected too.

Replanting uses one real sapling for a small tree or four in a 2×2 plot for a large tree. The plot needs suitable soil, growing room and an accessible approach within the lumber station's local range. Harmless grass and flowers at the planting spot can be replaced; natural overhead leaves do not reject the plot. Protected plants, decorative leaves, solid obstructions and water still block planting. Keep spare saplings in the job barrel or worker's bag: leaf drops are random, so a harvest does not guarantee enough to replant. Axes need sufficient remaining durability for the whole tree. Lumberjacks do not create saplings or speed up growth.

If a lumberjack stops, open its citizen screen to read the activity. Forestry reasons identify trees outside station range, player logs or nearby buildings, incomplete trees, unloaded branches, blocked approaches, missing saplings and cramped planting ground. The same problems appear in settlement needs and the existing rate-limited server diagnostics. Saplings waiting to grow are a normal pause.

### Tunnel mines and quarries

A mine creates a three-block-high descending staircase, followed by two-block-high tunnels at its target Y. By default the spine has four junctions three blocks apart, each with one 24-block branch on either side. Workers approach each cut from the previous cleared step and use actual building stock to fill missing tunnel floor support. One miner works the staircase and branches; add separate Mine Stations for more miners.

A quarry targets the **adjacent chunk in the direction you faced when placing it**, rather than the chunk containing its station. Crews walk into the pit and dig the blocks around them, one horizontal layer at a time, with actual pickaxe durability and drops. Mining speed follows the block's hardness and the pickaxe, as for a player: about a second per stone block with a stone pickaxe, longer for harder blocks or weaker tools. Tunnel and cave mining use the same timing.

**Getting in and out.** The pit keeps a one-block-wide **spiral staircase** around its edge: one block per layer is left standing, each a step down and over from the last, starting level with the ground just outside the chunk edge nearest the station. Citizens walk it down to the working layer and back up for meals, deliveries, rest, and alarms, a few steps at a time. The staircase is checked from the rim down on every work search, and crews only go down while every step is solid with two clear blocks above it. Where a cave removed a step, or sand or gravel collapsed, a worker standing on the step above rebuilds it with cobblestone, stone, or dirt from storage. While a step is missing, flooded, or blocked, and for quarries planned before staircases existed, crews work from the control block instead, so the quarry keeps going. Villager pathfinding cannot climb ladders reliably, so the pit uses stairs rather than ladders.

**Safety.** Citizens never shove each other, so crews can pass on the one-block stairs. A citizen who falls inside their own town's quarry takes no fall damage. Hostile mobs can still spawn in a dark pit; keep it lit.

**Nothing stalls a layer.** Quarries fell natural trees and leaves in their chunk. Blocks they must not remove are left standing, and the layer carries on around them: anything beside water or lava, containers and other block entities, player-placed or Inspector-protected blocks, planks, and unbreakable blocks. A quarry planned before this version keeps its progress; if its pit is already below the surrounding ground it has no staircase, and its crew works from the control block. Inspection reports the working layer and how the crew gets in.

A new mine plan is refused if any of its tunnels would cross a quarry's chunk, and miners never dig cave ore inside one, so the pit floor stays level. Mines planned before this version keep their tunnels; where one runs through a quarry chunk, the quarry digs down through it and the old tunnels show as trenches in the floor.

Both jobs preserve player-placed blocks, stations, protected furnishing ranges, containers/block entities, and living entities' footing. Mines also skip logs and planks. In a mine, water, lava, protected blocks, or an unsuitable tool can block a tunnel until cleared. Drain or clear obstructions yourself and inspect the worker's status. These jobs do not pump liquids, place lighting, or guarantee safe unsupported terrain. Work only runs in loaded chunks and never forces chunks to load.

Each mine chooses its depth once when its plan is created and saves the result. There is no depth command. The entrance must be above at least part of the configured depth band, and the planned descent must fit the claim. Already removed blocks grant no resources again. Completed work and parallel cuts persist across restarts.

When miners encounter caves near the selected depth, they scan a bounded nearby area for exposed ore. They walk to reachable targets and use the correct actual pickaxe. They resume planned tunnels when no cave ore is accessible. This is local cave work; systematic exploration of an entire cave network remains future work.

### Commands

| Command | Purpose |
| --- | --- |
| `/wwmc guide` | Receive the visual handbook with short topics, search and station recipes. |
| `/wwmc status` | Inspect your town's population, loaded beds, stations, and priority. |
| `/wwmc recruit [1-8]` | Recruit citizens up to the housing/population limit. Defaults to one. |
| `/wwmc name <name>` | Rename your town, up to 48 characters. |
| `/wwmc priority balanced\|food\|materials` | Set every job's priority from a preset: balanced puts guards and traders first, food also puts farms and cooks first, and materials puts farms and cooks last. |
| `/wwmc job <job> <off\|low\|normal\|high>` | Set one job's priority, for example `/wwmc job farm high`. |
| `/wwmc citizens` | List each loaded citizen with their job and what they are doing. |
| `/wwmc needs` | List the town's needs, most urgent first, with the station each concerns. |
| `/wwmc map` | Open the shared settlement map. |
| `/wwmc ping <meet\|bridge\|build\|danger\|resource> [note]` | Mark where you stand on the shared map for your town and its allies. |
| `/wwmc craft` | List craftsman orders with town stock and targets; change them on the Craftsman Station screen. |
| `/wwmc craft bread on\|off` | Switch the cooks' bread order, also available on the Cook Station screen. |
| `/wwmc alarm` | Sound the alarm yourself, or call the all-clear early while it rings. |
| `/wwmc wave` | Bring the next enemy wave forward to now, even in daylight. |

Commands affect your own town at your current position. You can own several towns in the Overworld, each with its own population limit. If you own several and stand outside them, use a banner screen or enter the town you want to manage. Player claims deny block placement/removal, block and container use, entity interactions and player attacks until the owner's invitation is accepted. Configure builders and stewards on the banner's Relationships screen; alliances alone grant no claim access. The owner can rename the town on Relationships -> Town. Entry notices display that name. An occupied settlement's banner is its fixed rally point and cannot be mined normally; all settlement banners resist explosions and piston movement. Lost flags can be restored at their original coordinates without changing the saved town.

### Job blocks define building purpose

Beds alone do not determine what a structure is. A **role station and nearby furniture** provide that meaning. Housing, barracks, hospital, warehouse, farm, lumber, and guard supply stations scan a fixed 7×7×7 cube centered on themselves, from offsets −3 through +3 on each axis. Mines and quarries use explicit excavation plans. Furniture/resource changes are picked up on the next inspection or worker scan.

Ranges can overlap. A complete bed belongs to the nearest housing, barracks, or hospital station that contains both halves; hospital-owned beds do not recruit citizens. Each chest/barrel block belongs to the nearest warehouse, and work targets belong to the nearest station of that job. Equal distances use station coordinates (X, then Y, then Z) as a stable tie-breaker. A double chest's two physical inventories are each included once.

Only loaded blocks inside the settlement claim count. Scanning never loads chunks. A known station in an unloaded chunk retains ownership of its nearby furniture until it is loaded and validated; its own production/capacity stays paused.

| Station | Current interpretation | Later role |
| --- | --- | --- |
| Housing | Residential beds, recruiting capacity, and rest. | Families, migration, approved housing expansion. |
| Barracks | Camp/troop beds, currently usable as housing. | Recruiting, training, and organizing military units. |
| Hospital | Patient beds excluded from housing capacity; a funded medic uses meals and paper dressings to treat wounded citizens. | Casualty evacuation. |
| Warehouse | Chests, trapped chests, and barrels within its 7×7×7 range. | Reserves, convoy loading. |
| Farm | Mature supported crops within its 7×7×7 range. | Planting expansions, varied crops, food processing. |
| Lumber | Whole trees rooted in range, real sapling planting, and replanting. | Larger forestry areas and better species/terrain handling. |
| Mine | One miner working an endless vein or digging a staircase, branches and accessible cave ores. | Cave exploration, reinforcement, lighting. |
| Quarry | Full neighboring chunk excavation, layer by layer, entered by a spiral staircase. | Machinery, dedicated haulage, liquid management. |
| Craftsman | Learned crafting-table recipes kept at chosen stock levels from real materials. | Stonecutter and smithing orders. |
| Smeltery | Warehouse ores/raw metals smelted in nearby furnaces or blast furnaces. | Specialized metallurgy and technology. |
| Cook | Raw food cooked in furnaces, smokers or lit campfires; three wheat become bread. | More meals and food orders. |
| Blacksmith | Repairs first, then alloys metals and forges ordered equipment at an anvil and furnace. | More metallurgy recipes. |
| Guard | Day/night posts, shared gear, bell alarms, player-led squads and convoy escorts. | Larger armies and siege tactics. |
| Courier | The only town hauler: moves job outputs, tools and inputs through the warehouse. | Convoys between towns. |
| Enchanter | Enchants unenchanted gear and books with lapis at an enchanting table within 5 blocks, up to level 25. | Enchanting orders and libraries. |
| Researcher | Writes real scrolls at a lectern using paper and ink/charcoal; paid work survives saves. | More technologies and later machines. |
| Gatherer | Harvests cane/bamboo tops and dry exposed sand, gravel and clay with a shovel. | More regional gathering. |

Each station has its own small model built from vanilla textures, a workbench, a watchtower or a tent for example, turned to face the player who placed it. A mine's tunnel entrance and a quarry's red flag point the way they dig. Stations declare use; this build does not infer enclosed rooms, roofs, or architectural quality. Work validates supplies, protection, reservations, loaded terrain, and access before changing blocks.

Saves from 0.1.0-alpha keep their towns and stations, but old selected room bounds are ignored in favor of the fixed range. Reposition stations or furniture if an earlier selected room extended farther than three blocks. Existing surveyor items become Station Inspectors and retain the `wwmc:surveyor` ID and recipe.

Existing towns smaller than 240 blocks widen to the 240 minimum automatically unless the larger square would reach another town, in which case they keep their saved radius. A widened town gets new corner banners; the old ones stay as ordinary blocks you may remove. Older mine stations default to facing north and now use tunnel plans, so inspect or reposition them before assigning workers. Mine plans from 0.2.0-alpha receive a one-time automatic-depth replacement when used; existing excavated blocks remain air and grant no duplicate drops. New automatic plans keep their chosen depth across reloads. Quarry plans retain their existing progress. Legacy numbered citizen labels receive personal names when their citizens load; custom names are preserved. Old nine-slot cargo saves expand into the new inventory. Server configs with a `settlementRadius` below 240 are corrected to 240.

### Crafting

All markers use eight planks around a center item in a crafting table.

| Marker | Center item |
| --- | --- |
| Settlement Banner | Blue wool |
| Housing Station | Oak door |
| Barracks Station | Stone sword |
| Hospital Station | Paper |
| Warehouse Station | Chest |
| Farm Station | Wheat seeds |
| Lumber Station | Stone axe |
| Mine Station | Stone pickaxe |
| Quarry Station | Bronze pickaxe; Bronze Age required |
| Craftsman Station | Crafting table |
| Smeltery Station | Furnace |
| Cook Station | Smoker |
| Hunter Station | Leather |
| Fisherman Station | Fishing rod |
| Animal Keeper Station | Hay bale |
| Butcher Station | Wooden axe |
| Guard Station | Wooden sword |
| Blacksmith Station | Copper ingot; Bronze Age required |
| Courier Station | Barrel |
| Enchanter Station | Book |
| Researcher Station | Lectern |

The Station Inspector is a shapeless recipe with two paper and one stick. The **Settlement Guide** is a shapeless recipe with one book and one blue dye; right-click it to open the native book screen. `/wwmc guide` gives another copy. Tools consumed to craft stations are separate from tools supplied to workers.

### Ages and research

New settlements begin in the Stone Age. The owner and accepted members share age unlocks everywhere; allies and invitations alone do not grant them. Existing settlements keep their previous equipment access.

Researchers now make **Research Scrolls** at real lecterns. Each takes **2 paper, 1 ink sac or charcoal, and 30 seconds of active work**. Set their warehouse target in the banner's **Research** page. Scrolls are real items and can be traded. Spend them alongside the listed materials to unlock discoveries; there is no second project timer. Sleep, danger, blocked routes, missing supplies and full storage all show clear pause reasons.

| Discovery | Warehouse supplies | Practical requirement | Unlocks |
| --- | --- | --- | --- |
| Bronze Age | 24 copper, 8 tin, 8 coal, 6 scrolls | Staffed researcher and at least 3 citizens | Copper/bronze forging, bronze anvil, blacksmith and quarry |
| Iron Age | 16 bronze, 16 iron, 16 coal, 12 scrolls | Bronze Age; staffed smith with anvil and furnace | Iron/gold forging, utility equipment and iron anvils |
| Gemcraft | 8 diamonds, 24 lapis, 16 scrolls | Iron Age | Diamond gear and enchanting |
| Netherite Smithing | 4 netherite scraps, 16 gold, 20 scrolls | Gemcraft | Netherite upgrading |

**Already-paid research is preserved.** An unfinished project from an older build still completes at its lectern using its saved progress, without charging scrolls or materials again. Completed unlocks remain available.

After Bronze Age, use an **Alloy Furnace** in your settlement: **3 copper + 1 tin + separate fuel → 4 bronze ingots** in 20 seconds. Both material slots accept ingots, raw metals or ores. A smelter and courier can automate it from its job barrel. Old blends remain smeltable for save compatibility.

**Equipment must be forged.** Wood and stone gear stay player-crafted. Copper, bronze, iron, gold, diamond and netherite gear must come from a blacksmith. Set orders in **Production → Metalwork**; recipe clicks and vanilla redstone crafters retain their ingredients when a forge-only output is blocked. Existing metal orders learned by craftsmen move to the blacksmith automatically. Native netherite upgrades consume a template, the original diamond item and a netherite ingot, retaining the original item's components.

**Found gear stays yours.** Equipment may be stored before its age is researched; wearing and using it wait for the unlock. Once unlocked, found equipment works normally. Creative and spectator players retain their bypass. See [settlement production](settlement-production.md) for the early-game chain and anvil recipe.

### Trading and other settlements

Craft a **Trader Block** with a compass surrounded by eight planks. Place one inside each town; a second Trader Block in the same claim is rejected without consuming its item. One citizen works as the trader. Trader Blocks have no crew or range upgrades.

1. Found a second town outside the first claim and give both towns housing, citizens, and warehouses. Population limits belong to each town separately.
2. Open the Trader Block empty-handed and select the other town on **Routes**. Your own towns connect immediately; another player's town must select yours to accept. Each town supports one partner at a time.
3. Click the example slot with an item, or shift-click one from your inventory, to add it to **Exports**. The example stays with you. Up to six items can be listed.
4. Set **Keep** to the amount that must remain in this town's warehouse and **Send** to the maximum carried per trip. For example, Keep 64 / Send 32 sends up to 32 carrots above a 64-carrot reserve. Send 0 pauses that item. Configure the other town's exports separately.
5. The citizen loads goods at home, visits the home checkpoint, walks to the other checkpoint, deposits goods in its warehouse, and returns. These are supply routes, with no automatic price or payment.

Goods in transit remain separate from meals and ordinary work supplies. Full destination storage keeps the remaining load on the citizen. **Pause** stops new departures; **Disconnect** returns undelivered goods.

**Route planning.** Traders keep a coarse map of the land between the two Trader Blocks, one cell for every 4×4 blocks, saved with the world. It records open ground, roads, forest, water and bridges, and is read only from chunks that are already loaded: near players, around the towns and around the trader. Nothing is loaded just to map it, so the map fills in as you and your traders travel. When you have walked or flown over the land, the trader plans its whole trip over it. It prefers roads, takes open cleared ground over forest, and crosses water only on bridges, so it follows a cleared highway or a distant bridge rather than walking straight into woods or a river. Land nobody has seen yet is assumed walkable and planned through, a little more reluctantly than known open ground; once the trader sees it, the route is planned again. A spot the trader cannot reach is avoided for five minutes. Between waypoints, traders follow reachable ground legs, including beneath roofs, and retry stalled legs with shorter and sideways alternatives. A route counts as blocked only after 15 seconds without progress; the status then names where the trader is stuck. Roads, bridges, and open doors help. Citizens on land do not plan to swim across a river; citizens pushed into water can still swim out. Traders do not teleport, use portals, sail boats, or build roads. A dead trader drops its actual goods and the town can assign another citizen.

**Road materials:** dirt paths, gravel, cobblestone, common stone/brick paving, planks, slabs, and stairs are preferred automatically by all citizen jobs. Solid bridge decks over water also receive a preference. Grass and dirt remain usable when a road is unavailable. Keep bridges connected to both banks, with room for a citizen to stand and walk. Datapacks can extend the `wwmc:paved_paths` block tag for other paving materials.

Each active trader maintains a moving **3×3 chunk window**. The route does not keep every intervening chunk loaded. The default server limit is **8 active town traders**, and the maximum route length is **8,192 blocks**. Travel continues on a running server when an owner logs off. Ordinary workers still require ticking chunks; distant abstract town simulation is future work. Install matching mod versions on server and clients because the screen protocol changes in 0.9.0.

Small NPC towns appear as you explore suitable **loaded Overworld terrain**. Deterministic regions usually put candidate towns about **1,000–2,000 blocks apart**, with larger gaps where terrain or claims prevent building. Sites must be dry, gently sloped, clear of block entities and recorded player blocks, and outside every existing town claim. Construction is saved and proceeds in batches of 128 block placements per tick. Revisiting a region cannot duplicate its town; destroying it does not cause a respawn.

NPC towns start neutral with a house, 12 beds, warehouse, trader checkpoint, guard, farm, lumber operation, renewable iron vein, smelter, kitchen, and courier. They begin with six named citizens (subject to the population cap) and starter supplies, then can have children under the same happiness, spare-food, housing and population rules as player towns while ticking. Small crews take one worker per station. Farmers export carrots, timber towns oak logs, and mining towns iron ingots; their own reserves are protected. A free neutral town accepts a proposed route automatically. Deliveries build goodwill; attacking its citizens ends your route, makes it refuse further trade, and its guards defend the town. Countries, sieges, conquest, negotiated prices, route networks, carts and escorts remain future work.

| Setting | Default | Meaning |
| --- | --- | --- |
| `maxActiveTraders` | 8 | Town traders with a moving 9-chunk window in one dimension. |
| `tradeRouteDistance` | 8192 | Longest banner-to-banner trader route, in blocks. |
| `randomSettlements` | true | Discover new neutral NPC towns; disabling leaves existing towns intact. |
| `npcTownSpacing` | 1408 | Region size for future candidate towns, in blocks. |
| `maxNpcTowns` | 48 | Maximum automatically generated NPC towns in the Overworld. |

### Server configuration

Open **Mods → Villager Colonies → Config** while your single-player world is loaded. Settings are grouped into **Settlements & Upgrades**, **Quarry Crew**, **Mining & Quarries**, **Work & Food**, **Enchanting**, **Defense & Waves**, **Trade & Other Towns**, and **Diagnostics**. Hover a label or control for its explanation, valid range and units. The native Undo, Reset and Done controls still apply; Reset affects only the open section. Return to the category menu and press Done to save. Active TOML keys stay in their original locations. The six retired non-quarry crew settings are removed on config reload; quarry and other server overrides carry over. Multiplayer server configuration remains controlled by the server.

The generated `wwmc-server.toml` config controls these defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `settlementRadius` | 240 | Horizontal radius of new towns; 240 is also the minimum. |
| `maxCitizens` | 64 | Hard ceiling on citizens per town, whatever its population upgrades; housing beds also limit recruiting. |
| `basePopulation` | 10 | Citizen limit of a town before population upgrades. |
| `populationPerUpgrade` | 5 | Extra citizens each population upgrade allows. |
| `populationUpgradeCost` | 8 | Emeralds for the first population upgrade; each later one costs this much more than the last. |
| `stationUpgradeCost` | 8 | Emeralds for a station's first range or crew upgrade; each further level costs twice the last. |
Veins replenish in about 15 seconds with a stone pickaxe (`oreVeinSeconds`). Wood takes 20s, iron about 12.3s, diamond about 10.6s, and netherite 10s for common ores. Tool speed uses square-root scaling, capped at 1.5 times stone speed; gold ore multiplies the wait by two, diamond and emerald by six, and ancient debris by eight. The station screen shows the assigned miner's equipped pickaxe and estimated delay. Miners still need a suitable tool, use its durability, and spend time physically breaking the ore.
| `quarryWorkers` | 8 | Crew slots per quarry before crew upgrades. |
| `rationTicks` | 2400 | Base loaded ticks between regular meals, multiplied by `mealIntervalMultiplier`. |
| `mealIntervalMultiplier` | 3 | Regular meal interval multiplier; the default gives six loaded minutes between meals. |
| `fishingSeconds` | 30 | Working seconds on a dry bank per whole-fish catch. |
| `animalBreeders` | 4 | Adult animals of each species a keeper preserves before harvesting surplus. |
| `enchantMinutes` | 5 | Minutes an enchanter spends on a book or common item; iron and gold ×1.3, diamond ×1.6, netherite ×2, plus more for uncommon, rare and epic items. |
| `enchanterMaxLevel` | 25 | Highest enchanting level enchanters reach (at most 29); level 30 is the player's alone. |
| `alarmThreshold` | 10 | Hostiles citizens must sight at once before a guard runs to ring the bell. |
| `enemyWaves` | true | Send hostile waves against towns while their owner is home. |
| `waveMinPopulation` | 3 | Citizens a town needs before waves are scheduled. |
| `waveIntervalDays` | 2 | Average in-game days between waves, ±25%. |
| `waveBaseMobs` | 2 | Hostiles in every wave before population scaling. |
| `waveMobsPerCitizen` | 0.5 | Extra hostiles per citizen, rounded up. |
| `waveMaxMobs` | 40 | Largest possible wave before population upgrades. |
| `waveMobsPerUpgrade` | 2 | Extra hostiles per population upgrade, also above `waveMaxMobs`. |
| `mineMinY` | −30 | Lower endpoint for a new mine's randomly chosen depth. |
| `mineMaxY` | 10 | Upper endpoint for a new mine's randomly chosen depth. |
| `quarryTargetY` | −64 | Bottom depth when a new quarry plan is created. |
| `mineBranchLength` | 24 | Length of each mine side branch. |
| `mineBranchPairs` | 4 | Paired side-branch junctions along the mine spine. |

See [server diagnostics](diagnostics.md) for worker warnings, recovery events and log settings.

### Personal inventories

Citizens keep resources in a persistent **36-slot bag**. Their owner can open it with an empty-hand right-click within eight blocks; the screen also shows the citizen's job, activity, health, next meal and equipment. Work pauses while the inventory is open; guards continue defending during an alarm. Menus close when the citizen dies, you move out of range, or ownership is no longer valid.

Workers use carried supplies before collecting replacements from their own job barrels. Deliveries leave finished goods and unused equipment in those barrels for couriers. Citizens keep at most **one spare meal** when shared stock is plentiful; low stock stays in the pantry for hungry citizens. Production supplies and goods never become a worker's warehouse trip.

**Changing jobs.** Old gear is put away and returned to the new job's barrel for a courier to collect. Unloaded, full or missing storage keeps the items in the bag. Guards still share armor through their station's stands and defend before returning gear during alarms. Equipment below 25% durability is carried for repair. Smelters and cooks never burn bows or tools.

**Getting unstuck.** Citizens open doors on their way and walk through harmless flowers, grass and ferns. These plants also leave a worker's hands clear; walls, leaves and harmful plants still obstruct work. Citizens finish the short step to a selected work position when Minecraft navigation stops short. A worker with repeated failed job routes or 30 seconds without movement progress is returned to safe standing room beside its job. The banner is a fallback if the job has no room. Its job, health and inventory remain intact, and the failed errand is dropped. Retry pauses do not reset the timer, but ordinary idle time does not start it. Traders, deployed squads, sleeping citizens, citizens in combat and civilians sheltering at home are exempt. Recovery accepts slabs, dirt paths and carpets and never enters unloaded or frozen ground.

**Out of range.** Each town remembers where every citizen last stood while ticking. If a citizen is frozen or unloaded for ten seconds while its station is loaded (the banner, for a citizen without a job), it is brought back. A frozen citizen is simply moved. For an unloaded one, the town loads a 3×3 chunk window around its last place for a few seconds, never longer, and moves it once it appears. It lands beside its station, keeps its job and drops the errand that led it away. Injured citizens return too, so they can reach hospital care. Failed recalls without standing room retry after 30 seconds. A citizen that is not found after two searches, or that has been missing for five minutes with no recorded place, leaves the roster: its job and population place open up, and the owner is told if online. If it turns up later, it rejoins. Traders on a trip are never fetched.

A full warehouse leaves the remainder in the citizen's bag. An unusually large tree harvest has a saved backlog that moves into the bag when space opens; citizens wait for space instead of dropping overflow on the ground. On death, actual carried items and equipped gear drop normally.

### Food and healing

Regular meals default to **7,200 loaded ticks / six minutes**, three times farther apart than before. `rationTicks` keeps its existing saved base value; the new `mealIntervalMultiplier` defaults to 3, so the slowdown also applies to existing worlds without overwriting custom values.

Meals and direct feeding satisfy hunger; they do not heal injuries. Injured citizens rest in a free, reachable **Hospital Station** bed until fully healed. A funded Field Hospital adds a medic who speeds up treatment. See [Hospital recovery](production-recovery.md#hospital-recovery) for supplies and recovery rates.

When storage holds fewer than two meals per town citizen, nobody takes spare food. Among loaded hungry citizens with no carried meal, those fed least recently get priority. Every healthy citizen waits until its next meal; injured citizens eat for hunger and recover in hospital beds. With ten hungry citizens and ten loaves, each can eat one loaf. With abundant stock a citizen may carry **one** spare. Farmers and cooks return their food outputs instead of keeping reserves.

**Hearty and varied meals.** A meal keeps a citizen full for a time set by its nutrition and saturation, measured against bread. Bread keeps the configured interval, a steak lasts about three quarters longer, and a carrot a little over half as long, with limits of half and 1.75 times. Citizens remember their last six meals. Three different foods among them make a citizen **Content**, with 5% faster work; four or more make it **Delighted**, with 8%. The citizen screen shows the mood and recent meals, and the Needs tab warns when storage holds only one kind of food. Meals never heal injuries; hospital beds do.

Citizens may walk to the communal pantry to eat; this does not transfer production goods or tools. Couriers deliver food loads intact, including a single meal, and eat from communal stock instead of claiming meals from their cargo. Raw meat, raw fish and whole carcasses are not meals. Rotten flesh, spider eyes and poisonous food are rejected.

### Animal food jobs

Craft each station from the center item listed above surrounded by eight planks. Add a job barrel and couriers; each job block employs one citizen. The food priority preset includes all four roles.

| Job | Setup and behavior | Output |
| --- | --- | --- |
| Hunter | Sword or axe in its barrel. Searches for adult cows, pigs, sheep, chickens and rabbits within 24 blocks by default. Named, leashed and animals in keeper ranges are protected. | One whole carcass per animal; real leather, wool and feathers are preserved. |
| Fisherman | Fishing rod, a dry reachable bank, open sky and two-block-deep water in station range. Works for 30 seconds per catch by default. | One whole cod or salmon carcass; fishing does not require entering water. |
| Animal Keeper | Fenced pen and pairs in range, normal breeding feed, plus a sword or axe for surplus adults. Uses real breeding and growth. Keeps four adults per species and breeds up to a bounded herd. | Carcasses from surplus adults; babies, named animals and mating animals are preserved. |
| Butcher | Axe and carcasses in its barrel. The station is its cutting table. | Raw portions for cooks: cow/pig 4, sheep 3, chicken/rabbit/cod/salmon 2. |

The chain is **producer barrel → courier → warehouse → courier → butcher → courier → warehouse → courier → cook → courier → pantry**. Carcasses cannot be eaten or placed directly in cooking appliances. Without couriers, production remains in job barrels. Supply each station's tools and keep the kitchen fuelled. Keepers use wheat for cows/sheep, seeds for chickens, roots for pigs and carrots/dandelions for rabbits; couriers avoid taking scarce ready-to-eat crops for breeding.

`fishingSeconds` controls catch time and `animalBreeders` controls the breeding group. Hunters affect only their own supported game targets; ordinary player kills retain vanilla drops.

### Guard stations and posts

1. Craft a **Guard Station** from eight planks around an iron helmet and place it in your claim. **Each Guard Station employs one guard**, on duty through both day and night. Add more stations for more guards; empty posts draw available citizens before lower-priority production jobs. A guard deployed in a squad still holds their station assignment, so leave additional staffed posts for home defense. An unstaffed station needs an available citizen or a recruit.
2. Put equipped **armor stands within three blocks of the station on each axis**. On duty, guards take usable protective armor for empty slots and upgrade to pieces with higher armor/toughness attributes. An upgrade exchanges the real old and new pieces on the stand. Guards also check their bags and local job barrels, preserving durability, names and enchantments. Gear below 25% durability is never taken back into service. Equipped armor is visible.
3. Guards **look for weapons** themselves: one melee weapon (a **sword** or **spear**), one **bow**, and up to 32 **arrows** for it. They check their own bag, items held in the hands of armor stands in the station range (stands double as weapon racks), their job barrels, and loose weapons or arrows that have lain on the ground in town for five seconds, such as a fallen skeleton's bow. They take the strongest melee weapon available and swap up when they find a better one, returning the weaker weapon to storage. Loose items they cannot reach are skipped for a minute.
4. In combat, a guard with a bow and arrows shoots enemies 5–24 blocks away when no citizen or player stands in the line of fire; each shot uses one real arrow and bow durability, and arrows are not recoverable. Closer in, they switch to their sword or spear; melee attacks with swords, spears, or fists reach up to four blocks from the guard’s eyes to the target hitbox, with line of sight required. A guard without a melee weapon puts the bow away and fights unarmed. Weapons lose durability in use. Guards defend against nearby visible hostile monsters inside the town claim and prioritize combat over supply trips.
5. **Sneak-right-click the Guard Station with the Station Inspector.** Then right-click clear ground for the **day post**, followed by clear ground for the **night post**. Both positions need dry footing, headroom, and a location inside the same claim. The pair is saved together. Sneak-click ground during selection cancels it. Until configured, both posts default to the station.
6. One set of armor equips the station's guard through both shifts. Worn armor returns to empty matching stand slots for the repair chain; a full or unreachable rack sends it to the local guard barrel, or the guard's bag if storage is full. Alarms call all assigned guards. On-duty guards roam among reachable town stations and nearby paths, and revisit the post periodically. Day duty runs from tick 23000 through 12999; night duty from 13000 through 22999. Post inspection reports both positions and crew size. Unloaded posts/terrain are never force-loaded.

7. **Roles.** The Guard Station's Role button cycles three roles. A **swordsman** behaves as described above: it patrols and chases hostiles within 16 blocks, 32 on alert, and answers citizens' calls. A **shield guard** holds its post like a gate. It fights only hostiles within 10 blocks of the post, or anything attacking it, never leaves to answer calls, and takes 15% less damage, 25% with a shield in its off hand. An **archer** spots hostiles from 28 blocks, 40 on alert, and pauses twice as long at each patrol stop. It still needs a bow and arrows.
8. **Patrol routes.** Click **Mark patrol route**, then use the Station Inspector on clear ground inside the town for up to eight points in order. Sneak and use it to save the route. The guard walks from its active post through each point and back, skipping any that are unloaded. **Clear route** returns it to its own rounds past the town's stations.
9. **Watchtowers.** A Guard Station at least six blocks above the lowest ground eight blocks to each side is a watchtower, and its screen says so. Its guard counts hostiles from 48 blocks for the alarm. It also reports newly sighted hostiles outside the claim to managers in town, with their direction from the tower, at most every two minutes. A band of three or more also goes in the journal. Hostiles already reported are not reported again while they linger in view. The Signal Fires research raises this to 64 blocks and once a minute.

Citizens are drawn with the villager head, robe, and skin on a humanoid body with free arms, so armor, weapons, and tools are visible. Assigned citizens now wear job-specific profession clothes, colored accessories and hats; real equipment remains visible over them. Patrolling does not yet include formation orders or player/faction warfare.

### Worn equipment and blacksmiths

Equipment with **less than 25% durability remaining** retires from use. Guards return armor to shared stands, or their local barrel when no rack accepts it; couriers collect retired rack gear. Workers leave worn tools in job barrels.

After Bronze Age, craft a Blacksmith Station from a **copper ingot surrounded by eight planks**. Add a bronze or iron anvil and a barrel in range. Forging also requires a furnace and coal/charcoal. Metallurgy belongs to smelters using the new Alloy Furnace. Set orders in **Production → Metalwork**. A bronze anvil works **65% slower than iron** for repairs and forging; both use normal anvil wear. Its station screen shows its condition and speed, and moving it preserves wear. Couriers deliver damaged tools, weapons and protective armor plus their matching repair material. The smith repairs the original item, preserving names and enchantments, and returns it to the local barrel for courier pickup. Worn armor on stands in the smith's own range can also be serviced.

Each material repairs up to 25% of maximum durability: iron gear uses iron ingots, gold gold ingots, diamond diamonds, netherite netherite ingots, leather armor leather, stone tools cobblestone and wooden tools planks. Missing supplies or full storage pauses the job; equipment is never copied or discarded.

### Smelters and cooks

Smelters supply actual furnaces or blast furnaces in range, using raw metals, ores and fuel delivered to their job barrels. Cooks use smokers or lit campfires, with ingredients and smoker fuel delivered to their own barrels. Appliance progress, recipes and output remain vanilla.

Cooks turn prepared raw meat or fish into cooked meals and make one bread from **three wheat**. The Cook Station screen controls the bread order, which aims for 32 bread in town stock. A whole carcass must first pass through a butcher. Player-harvested vanilla raw meat can still be cooked normally.

Workers collect finished items into their bags and leave them in local job barrels; couriers bring them to the warehouse. One worker operates each Smeltery or Cook Station. Missing appliances, ingredients or fuel pauses work locally; a full barrel keeps products in the worker's saved inventory.

### Craftsmen

A **Craftsman Station** (eight planks around a crafting table) employs one craftsman, always; place more stations for more craftsmen. Right-click it to open its order screen.

**Teaching.** Click the **Teach** slot while holding any item, or shift-click an item in your inventory, and the craftsmen learn the crafting-table recipe that makes it. You keep the item. Any shaped or shapeless recipe works, including recipes added by other mods and datapacks; special recipes such as dyeing armor, copying maps or fireworks cannot be taught. A town knows up to 27 orders. Teaching any planks makes a **planks (any wood)** order that uses whichever logs the town holds.

**Amounts.** Each order has a slider for how many to **keep in town**, from 0 to 256; 0 pauses it. Stackable items start at 16 and tools at 1. Stock counts the warehouse and every job barrel. The ▲ button raises an order's priority and ✕ forgets it. Each row shows the town's stock and whether the order is stocked, ready to craft, or missing materials.

**Work.** A craftsman takes the highest order below its target that has materials, from the station's own barrels, supplied by couriers, carries up to eight batches of real items to the bench and crafts them there. Each batch is checked against Minecraft's recipe before it is made, and container items such as milk buckets come back empty. When two orders make each other, such as iron ingots and iron blocks, each only uses the other's stock above its target, so they never convert back and forth. When nothing is short or materials are missing, the craftsman takes other work for a while.

New towns, and towns from earlier builds, start with these orders: stone pickaxe 2, stone axe 2, stone sword 2, bow 1, arrows 64, torches 32, ladders 32, sticks 32 and planks (any wood) 64. Orders switched off in an earlier build start at 0. Cooks still bake one bread from three wheat up to 32 bread; the Cook Station screen or `/wwmc craft bread off` switches it off.

### Settlement screens

Right-click with an empty hand to open:

- **Settlement banner:** a short overview with population, jobs, food, research status and the most urgent need. **People** manages jobs, citizens and housing; **Production** manages stock, workshop and forge orders; **Research** manages scrolls and discoveries; **Neighbours** shows trade and town news. **Needs** lists problems. **More** keeps the alarm, projects, expeditions, settings and map accessible.
- **Any station:** its detected resources and job status, the citizens assigned to it and what each is doing, the contents of its barrels (or the warehouse's containers), and its upgrades. Work stations have a button for their job's priority, the Cook Station a bread switch, the Guard Station a button to choose its posts, and stations that can be upgraded buttons for range upgrades or quarry crew upgrades. Hover a button to see exactly what it does and costs.
- **Craftsman Station:** the order screen described above.
- **Citizen:** their job, activity, health, next meal and equipment above their bag.

Screens refresh every second and close when you move more than eight blocks away. Owners and accepted stewards can open them. Use Relationships -> Players to invite members or revoke access; invitees accept on Relationships -> Invitations. Builders can build and interact inside the claim. The Relationships -> Town tab saves a custom town name. The field-order screen (`/wwmc squad`) remains usable away from the banner. Short notices, including alarms and waves, appear above the hotbar instead of in chat.

### Jobs and priorities

Every citizen has its **own station**. It goes back there each morning, after deliveries, meals and alarms, and waits beside it when there is no work, rather than taking another station. Assignments stay the same from day to day; a station's Crew tab lists its worker, or a quarry's crew, including those asleep or out of range. When updating an older town, places above the new limit are released automatically, and those citizens take other open jobs without losing their inventories.

Each job has a **priority**: Off, Low, Normal or High. Set it with the - and + buttons in the banner's **Jobs** tab, the priority button on a station's screen, or `/wwmc job`.

The overview's Jobs row shows **filled / enabled job places**. A station count includes Housing, Barracks and Warehouse Stations, which employ nobody. Most work stations add one place; a quarry adds its configured crew, and a hospital adds a medic only after funding the Field Hospital project. The Jobs tab lists open loaded places, switched-off places and places waiting for loading or a trade route. If all enabled places are full, add work stations or expand a quarry crew; raising a priority moves workers between jobs without creating more places.

- A citizen without a job, such as a new recruit, takes the open place of highest priority. Among equal priorities, guard posts fill first, then the trader, then the station with the fewest workers, then the nearest.
- About every half minute, and at once after you change a priority, a citizen moves to an open place in a job of **higher** priority than its own. Equal priorities never trade workers, so a new Normal station waits for a recruit, a free citizen or a raised priority.
- **Off** frees everyone in that job, and nobody takes it until you raise it again.
- Guards and traders start at High and every other job at Normal. The preset button on the banner, or `/wwmc priority`, sets every job at once: balanced, food (all food jobs High) or materials (all food jobs Low). Both production presets keep couriers at High so deliveries retain their staff.
- An open guard post draws a citizen from a lower-priority job at once, day or night.
- Sneak-right-click a citizen to release it from its job: it takes another open place and avoids that station for a minute.

A citizen part-way through an enchantment, a repair or a trade run finishes it before moving to another job. Towns from earlier versions take their old priority preset, and each citizen keeps the station it was working at when the update loads.

### Needs, experience and morale

Routine skill promotions and hospital recoveries are saved in **Campaign → Journal** at the settlement banner. Other campaign notices appear briefly above the hotbar instead of filling chat.

The town screen's **Needs** tab gathers what the owner can fix, most urgent first, from loaded stations, storage and citizens:

- Missing or full storage, low food, and storage holding only one kind of food.
- Citizens without housing beds, and injured citizens without a hospital bed.
- Out-of-range and jobless citizens.
- Stations with no worker, no job barrel, no fuel, or no oven, anvil, enchanting table or patient beds.
- Guard Stations without chosen posts, a blocked trade route, and a missing courier.
- Anything a worker is waiting for, grouped per station.

Rows that concern one place have a **Show** button. It closes the screen, outlines the block through walls with a tall marker for 20 seconds, and gives its distance and direction in chat. `/wwmc needs` lists the same in chat.

Hover over shortened row text to read the full explanation. A **missing job barrel** means none belongs to that station: place one in range, outside warehouse coverage, and nearer to this station than other job stations. **Cannot reach a job barrel** means one was detected but the worker could not reach clear standing ground beside it. Citizens can approach over paths, slabs and carpets; walls and blocked entrances still prevent access.

**Experience.** Citizens earn experience from finished work at each job, such as a harvest, a vein yield, a felled tree, a batch of cooking, a repair, a delivery or a hostile killed by a guard. Experience is kept per job, so a citizen moved elsewhere keeps what it learned. The levels are Novice, Trained (25), Skilled (75), Expert (175) and Master (375). Each level adds 3% work speed, or 4% for cooks and smelters. For miners, quarry workers, lumberjacks, hunters, fishermen and butchers, each level also spares their tools 5% of uses. Guards instead hit 5% harder and take 3% less damage per level. Each level also gives a 10% chance of recovering from a swing or shot in half the usual time. Small speed bonuses work as a chance of a double work step, so they count in full on average. Reaching a level is written in the journal. Losing a citizen of Skilled level or above is recorded too: experienced citizens are worth bringing home. The citizen screen shows the current job's level, every job's progress and the diet.

### Job barrels and couriers

Every production station needs a **barrel within its range**, normally three blocks on each axis, outside Warehouse Station ranges. Overlapping job ranges assign each barrel to the nearest station. Guards and blacksmiths now use job barrels too; couriers and traders use warehouses.

**Only couriers haul within a town.** Workers collect their own tools and ingredients, work locally, and leave goods in their barrels. They do not fetch production inputs from a warehouse or deliver their output there. Missing, unreachable, empty or full barrels stop the affected part of production until supplied or cleared. Citizens can visit the pantry for a meal, and traders keep their routes between towns.

A Courier Station employs **one courier**. Place more Courier Stations to increase hauling capacity. Couriers move finished goods to the warehouse and restock job barrels with tools, saplings, floor blocks, smelting and cooking inputs, fuel, animal feed, carcasses, repair inputs, learned crafting materials, books and lapis. They preserve the supplies each job uses. Retired armor from guard racks goes through the same repair chain. Small food-chain loads take priority while the pantry is low; other small loads are collected when larger errands are finished. Only one courier serves a particular job's barrels at a time.

Every job block has **one worker**, including older stations with crew upgrades. Build more stations for more workers; quarries keep their separate crew setting and upgrades. Start with barrels and couriers before expanding production, then balance supply and pickup rates against workers and storage.

### Ore veins

A **Mine Station placed within two blocks of an exposed ore**, on every axis, works that ore as an **endless vein**. The ore needs at least one open side, such as air, a torch or a ladder; ore buried on every side does not count, so older mines beside hidden ore keep digging their tunnels. Its miner walks to the station first, then to a standing spot with a clear view of the ore, mines it with a pickaxe able to harvest it, and collects the ore's normal drops, including Fortune, while the block stays in place. Only citizens get endless drops; a player who mines the ore breaks it normally. Each Mine Station has one miner, including old upgraded stations; excess crew members take other jobs.

Veins replenish in about 15 seconds with a stone pickaxe (`oreVeinSeconds`). Wood takes 20s, iron about 12.3s, diamond about 10.6s, and netherite 10s for common ores. Tool speed uses square-root scaling, capped at 1.5 times stone speed; gold ore multiplies the wait by two, diamond and emerald by six, and ancient debris by eight. The station screen shows the assigned miner's equipped pickaxe and estimated delay. Miners still need a suitable tool, use its durability, and spend time physically breaking the ore.

### Enchanters

An **Enchanter Station** (eight planks around a book) needs an **enchanting table within five blocks on each axis**, an 11×11×11 range, larger than other stations'. Surround the table with bookshelves as you would for yourself: the bookshelves set the enchanting level exactly as for a player, but an enchanter never goes above level **25** (`enchanterMaxLevel`); level 30 stays yours. One enchanter works each station.

Stock **lapis lazuli** and **unenchanted gear or books** in the warehouse for couriers to deliver, or directly in the station's barrel. The enchanter takes one item at a time, its own barrel first, armor and weapons before tools and books, and the rarest first. Each item costs one, two or three lapis by level, like the table's rows, and takes a long time: about **five minutes** for a book or a common item (`enchantMinutes`), 1.3 times as long for iron or gold gear, 1.6 for diamond and twice as long for netherite, with uncommon, rare and epic items taking longer still. Work only happens during the working day, so a busy enchanter finishes a few items each day. Progress is saved; the station screen shows the table's level, the lapis in stock, the items waiting and how far along the current item is. Finished items go back to the job barrel for couriers to collect. Nearly broken gear waits for the blacksmith first.

### Station upgrades

Every station that can use them sells upgrades on its screen, paid in **emeralds from your inventory**; emerald blocks count as nine, with change given back. Creative players pay nothing.

- **Range**, up to three levels: each level widens the station's cube by a block in every direction, from 7×7×7 to 9×9×9, 11×11×11 and 13×13×13 (an enchanter goes from 11 up to 17). Wider ranges reach more beds, chests, crops, trees, furnaces, anvils, armor stands and barrels. Quarries, mines, couriers and craftsmen have no range upgrades.
- **Crew**, for **quarries only**, up to three levels: each adds one worker slot to the configured quarry crew. Every other job block has one worker, including older upgraded stations.
- **Yield**, for **farms and mines**, up to three levels: +10%, +20%, then +30% average extra produce or mineral drops. Each harvested unit has that bonus chance. Seeds and Silk Touch ore blocks do not multiply; crop replanting reserves its real planting item first. Prices are 16, 32, and 64 emeralds by default (`yieldUpgradeCost`).

Each range or crew level costs twice the one before: 8, 16, then 32 emeralds by default (`stationUpgradeCost`). A broken station's item keeps its upgrades and shows them in its tooltip, so you can move an upgraded station without paying again; plain stations still stack with freshly crafted ones. The range preview shows the upgraded range.

### Population

A new town holds up to **10 citizens** (`basePopulation`). The town screen's **Grow** button raises the limit by **5** (`populationPerUpgrade`) for emeralds: 8 for the first upgrade, then 16, 24 and so on (`populationUpgradeCost`), up to the server's ceiling (`maxCitizens`, 64). Housing beds still limit recruiting as before.

**Population research** in the banner's **Research** tab gives another way to grow. **Housing Plans** adds 10 places, **Civic Planning** adds 15, and **City Planning** adds 25. Study them in that order using warehouse materials. Their bonuses stack with emerald upgrades, survive saving, and still obey `maxCitizens`; each recruit needs an available housing bed. The overview shows the research bonus and the correct next upgrade limit.

A bigger town draws bigger attacks. Each population upgrade adds **2 attackers** to every wave (`waveMobsPerUpgrade`), even beyond `waveMaxMobs`; from the first upgrade a tenth of each wave per upgrade are **pillagers**, and from the third upgrade **vindicators** join them. Towns from earlier builds count as having bought enough upgrades for the citizens they already have.

### Alarms and enemy waves

**Noticing a threat.** Every second, the town counts the hostile monsters its citizens can see inside the claim: guards watch out to 24 blocks (32 during an alarm), other citizens only notice hostiles within 8 blocks. One or two monsters are left to the guards. When at least `alarmThreshold` (default **10**) are in sight at once, the guard closest to a **bell** within 96 blocks runs to ring it. Bells must be inside the claim and loaded. If that guard is killed, cannot find a path, or takes longer than a minute, another guard is sent. Without a reachable bell, you receive a warning instead and the town is not alerted.

**The alarm.** A bell rung inside the town raises the alarm and wakes guards. An arriving wave also raises it immediately, without requiring a bell runner. Frightened civilians run to a reachable **housing or barracks bed**, prefer the bed they used before, open house doors on the way, and wait inside until it is safe. A nearby hostile can send a civilian home even without a town alarm. Losing sight of the hostile on the way does not cancel the trip. At night they can stay asleep in their bed. Without a reachable bed they use housing or the banner as a fallback. Guards prioritize nearby attackers, share reports, and answer wave threats; shield guards leave their gate during alarms. Injured guards keep defending and seek hospital beds after combat ends and ten quiet seconds pass. After **30 seconds** without sighted or reported hostiles, the town gives the all-clear. `/wwmc alarm` raises the alarm or calls the all-clear early.

**Enemy waves.** Once a town has `waveMinPopulation` (default **3**) citizens, a wave is scheduled about every `waveIntervalDays` (default **2**) in-game days. It arrives after sunset, only while you are online and within 64 blocks of the claim, and never while the previous wave's attackers are still alive. Waves contain `waveBaseMobs + waveMobsPerCitizen × population` hostiles, rounded up and capped at `waveMaxMobs`: four for a three-citizen town, 12 for 20 citizens, 18 for 32. Small towns face zombies; from 6 citizens a quarter of each wave are skeletons, and from 10 citizens 15% are spiders. The wave gathers 40–64 blocks from the banner on loaded open ground inside the claim, away from stations and at least 24 blocks from you, then marches on the banner and attacks citizens on sight. You are told its size and compass direction, and again when it has been repelled. Wave mobs do not despawn and remember their town across restarts. `/wwmc status` shows the alarm state and the next wave; `/wwmc wave` calls the next wave immediately. Peaceful difficulty prevents waves. Population upgrades make every wave larger and bring pillagers and vindicators; see [Population](#population).

**Glowing attackers.** Every wave attacker glows through walls and is reported to the guards immediately. Reports refresh while attackers remain, including after a reload. If any remain after a minute, a single hotbar notice tells you how many the guards are still hunting.

**Calling the guards.** Citizens who see a hostile within 16 blocks of them inside the claim, near their work for example, call the guards. Up to two guards on duty answer each call: they walk to the hostile, wherever it has gone in town, and fight it as soon as they see it. A guard who cannot reach it within 90 seconds leaves it to the others for two minutes. Calm endermen and other neutral mobs that are not angry are not reported. The citizen's screen shows the call for a few seconds, and the town screen's alarm row shows how many hostiles are reported.

## Implemented foundation

- Persistent named settlements, owners, non-overlapping claims, and job priorities.
- Citizens who keep their own station, and job priorities from Off to High that decide which open places fill first.
- Settlement banner and twenty-one role stations, each with its own detailed model, survival crafting recipes and a creative tab.
- Settlement ages, a working researcher, tin and bronze equipment, and shared research-based player progression.
- Emerald upgrades: wider station ranges, more crew slots, and room for more citizens at the price of larger enemy waves.
- Screens for the town, every station, the Craftsman's orders and each citizen, refreshed every second.
- A 240-block minimum claim radius, a configurable town color, and matching banners at the four claim corners.
- Automatic 7×7×7 station detection and live updates when nearby furniture/resources change.
  Bed and warehouse locations refresh within one second; inspection and recruitment refresh them immediately. Workers still check detected beds and storage against the live world before using them.
- A placement range outline and a Station Inspector for checking existing stations.
- Deterministic ownership of overlapping beds, storage, and same-job work targets.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens with individual saved names, personal inventories, and custom job AI.
- Guards with day/night posts, town patrols, visible shared shift armor, equipment upgrades, and swords, spears, or bows they find themselves.
- A craftable Settlement Guide with every block recipe and instructions for the playable systems.
- Blacksmiths who repair tools, weapons, and protective armor at actual anvils with matching materials delivered by couriers.
- Enchanters who slowly enchant unenchanted gear and books with lapis at an enchanting table, up to level 25.
- Hunger and meal variety, fair access to scarce food, and injury recovery in hospital beds.
- Bell alarms: manual, projectile, redstone, and guard bell rings activate every assigned guard; civilians take cover until the all-clear. Guards also run to the bell when citizens sight a large hostile force.
- Civilians who call the guards about hostiles they spot, and guards who go and deal with them.
- Population-scaled hostile waves that arrive at night while the owner is home, glow, and are hunted down if they linger.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Hunters, fishermen and animal keepers supply whole carcasses; butchers prepare raw portions for cooks. Keepers feed real breeding pairs and preserve babies and adult breeders.
- Whole-tree felling, player-placement protection, and planting from actual saplings in storage.
- Endless ore veins for mine stations placed near an exposed ore, automatic mine depth selection between Y −30 and 10, descending tunnels, accessible cave ore gathering, and full-chunk quarries that crews enter by a spiral staircase.
- Craftsmen who learn any crafting-table recipe from an example item and keep the amount you choose in stock.
- Job barrels at work stations, and couriers who move goods between them and the warehouse.
- Smelters who supply nearby furnaces/blast furnaces with courier-delivered raw metals, ores, and fuel, and collect their real results.
- Cooks who supply smokers or lit campfires with raw food, collect cooked food, and make bread from three wheat.
- Openable 36-slot personal inventories, saved overflow, local supplies, one spare meal when abundant, and real tool durability.
- One citizen per job block except quarries, exclusive resource reservations, civilian nighttime rest, and guard duty through the night.
- Save/reload support for settlement data, player block protection, replanting sites, excavation progress, cargo, tools, and meal timers.

Further plans include automatic housing construction, wars between rival settlements, distant simulation, larger technology trees, and countries. Barracks identify troop housing and support military projects. Fund a Field Hospital for a medic who treats wounded citizens with meals and paper dressings. Injured citizens rest in hospital beds until full health; ordinary meals satisfy hunger.

## Happiness, children and steel

See [Settlement production](settlement-production.md) for the alloy furnace, Steelworking, happiness, loaded-time births and the growth toggle. [Education design](education-design.md) describes the proposed schooling and apprenticeship system; education restrictions are not enabled.
