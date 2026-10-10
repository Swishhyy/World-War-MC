# Changelog

[Project overview](../README.md) · [All guides](README.md) · [Versioning rules](curseforge.md#public-version-format)

## Unreleased

- Add saved citizen happiness from actual meals, housing, safety and distinct housing amenities. Rotate available meals for variety; keep bowl and bottle returns.
- Add food-funded births from happy adults with spare housing, population room and parent cooldowns. Babies play near home, shelter and rest, and take no jobs until native adulthood. Pause growth in People → Wellbeing; no chat spam or unloaded catch-up births.
- Improve farmer, lumberjack and gatherer speed with better native tools, including bronze and steel. Keep mining's existing pickaxe timing; farmers can work without a hoe and use available hoes for a bonus.
- Add a smelter-operated Alloy Furnace with two material inputs, separate fuel, saved progress, hopper support and clear pause reasons. Make bronze directly from copper + tin; keep old blends smeltable.
- Add Steelworking after Iron Age, steel ingots, tools and armor, using native iron model and texture recolors. Smelters alloy iron + coal/charcoal; blacksmiths forge and repair the equipment.
- Share small fuel deliveries across production stations. Ordinary smelters return finished iron; blacksmiths release surplus ingredients when their finished orders are stocked locally.
- Simplify the banner to an overview and four main destinations: People, Production, Research and Neighbours.
- Add stock orders for automatic job block production and blacksmith forging, without requiring example items.
- Require blacksmith forging for bronze, copper and later equipment. Keep wood and stone player-crafted; protect ingredients when blocked, including vanilla redstone crafters.
- Add blacksmith equipment forging and a bronze anvil that works 65% slower than iron, with normal anvil wear. Repairs take priority and preserve the original gear.
- Replace new research timers with tradeable researcher-made scrolls, paid discoveries, and practical settlement requirements. Preserve existing unlocks and already-paid projects.
- Add Gatherers for cane, bamboo, sand, gravel and clay. Preserve plant bases and protected construction.
- Let smelters make charcoal, glass, bricks and terracotta. Let couriers collect surplus fuel and alloys while retaining production reserves.
- Add ruined town halls containing salvageable settlement blocks, a bronze smithy and finite scroll loot.

- Add a Neighbours banner board with nearby-town needs, trade status, NPC requests, alliances and quiet news. Keep other player towns' journals private.
- Let existing traders fulfil accepted player-settlement contracts with physical warehouse deliveries, home stock reserves, incoming demand reservations and saved emerald payments. Manual delivery remains available.
- Let loaded NPC settlements arrange up to two neighbour trade routes and exchange real surplus. Leave their primary checkpoint available for player trade; record deliveries and happiness-based births at the banner.
- Give player-connected routes priority over autonomous NPC caravans within the existing server trader limit.
- Remove the duel feature. Refund previously reserved preview stakes when loading existing saves.
- Add owner-approved battles for expedition resource outposts: hold the flag to capture its existing miners, storage and supply route. Both owners must stay online.
- Fix guards swapping away their bows while drawing; use real arrows and fall back to melee when ammunition runs out. Let guards collect shields from stands or storage and block between attacks, with a visible raised-shield pose.
- Use native melee range so guards keep approaching instead of stopping at their longer block-work reach and swinging too far away.
- Apply normal shield durability wear to a guard's actual blocked damage; frontal blocks protect health while rear hits still land.
- Log caravan handoffs, contract payments, outpost ownership changes and interrupted-battle recovery without recurring chat announcements.
- Make tin, alloy-furnace and equipment recipes discoverable in the vanilla recipe book, and show actual alloy recipes in the Settlement Guide. Keep research requirements.
- Explain raw tin, tin ingots, alloy ingredients and legacy Bronze Blend in item tooltips; keep old blends usable while directing new production through the alloy furnace.
- Recolor the vanilla gunpowder sprite for Bronze Blend: copper powder with four pale blue tin flecks, preserving its exact shape and transparency.
- Replace cod and salmon carcass shapes with whole fish: intact heads, visible eyes, fins and forked tails, with a sideways dropped-item pose.
- Fix lumberjack planting through grass and flowers and beneath natural forest canopies. Let nearby natural leaves from other tree species be cleared when they obstruct a trunk.
- Use reachable job-barrel or carried saplings when selecting new planting work, and show clearer forestry pause reasons in citizen screens, settlement needs and rate-limited server logs.
- Stop overlapping warehouse, housing and hospital scan ranges from marking natural trees as buildings. Allow forestry beside town banners; retain protection for placed logs and real construction, with the blocking block's coordinates in the pause reason.
- Recolor vanilla Minecraft textures for bronze tools, armor, ingots and blocks, and tin ores and materials. Preserve vanilla shapes, wooden handles, rock backgrounds and worn armor layouts.

## 0.1.0.0

Villager Colonies starts at **0.1.0.0**, with settlement ages, research, traps and villager fixes. The mod remains playable and in development.

- Rename the mod and distributable to **Villager Colonies** and **`villager-colonies-0.1.0.0.jar`**. Reset the version while retaining the `wwmc` mod ID and existing saves.
- Use the Villager Colonies emblem in the mod list, mod information panel and configuration menu.
- Carry forward all **World War MC 0.1.0.1** bug fixes and the additions below. Update in-game titles, guides, downloads and release packaging for the new name.

- Add five research-gated traps: wooden spikes, tangle nets, bronze snares, bronze caltrops and iron spring traps. Friendly traffic is safe; wear survives moving and saving.
- Add paid rearming and repairs, craftsmen maintaining nearby traps after combat, courier repair supplies, and quiet maintenance notices at the banner.
- Spawn waves beyond furnished stations and placed traps on safe ticking natural ground. Postpone blocked arrivals instead of spawning inside defenses; report persistent problems in the console.

- Add Stone, Bronze and Iron progression shared by a settlement's owner and accepted members. Higher-tier loot can be stored until its research unlocks use.
- Add a Researcher Station and lectern work: pay project supplies once, wait for real work, and keep progress through pauses and saves.
- Show clear research pause reasons in the banner and Campaign / Research, including blocked lecterns, unstaffed jobs, sleep, danger and recovery. Distinguish walking from paused work without chat spam.
- Let researchers retry another approach when their route becomes blocked, and recheck inaccessible lecterns after a short pause. Keep paid supplies and progress intact.
- Add tin ore, bronze alloying, bronze tools and armor, with further research for diamond and netherite gear. Existing settlements retain their equipment access.
- Give carcasses recognizable 3D models, improve fishing and tool swings, and hide clothing under the armor covering it.
- Generate furnished ruined castles and mining workshops using the mod's stations, beds, storage and research desks. Claiming them retains their rooms and supplies.
- Let lumberjacks fell natural trees beside their own job barrels or Lumber Station. Keep player-placed logs, nearby building planks and unrelated storage protected.
- Keep a worker's actual status visible during its retry pause instead of replacing it with "No work".
- Stop harmless flowers and grass from blocking worker paths and interactions.
- Return workers with blocked job routes to their job after 30 seconds without progress. Traders are exempt; jobs and inventories are retained.
- Mobilize guards as soon as a wave arrives, let shield guards answer the alarm, and keep injured guards defending until combat ends before hospital recovery.
- Send frightened civilians to reachable housing beds, including through house doors, instead of fleeing outdoors.
- Add Housing Plans, Civic Planning and City Planning research for 10, 15 and 25 extra population places, alongside emerald upgrades and within the server maximum.
- Move skill promotions and hospital recoveries into the settlement banner's Campaign / Journal tab. Other campaign notices use the hotbar instead of filling chat.

## World War MC 0.1.0.1

A bugfix release for citizen jobs, movement and recovery, with clearer town status and server logs. WWMC remains playable and in development.

- Bring injured citizens back from unloaded or frozen chunks so hospital care can resume; retain their health, job and carried equipment. Failed recalls retry after 30 seconds without using the successful-recall cooldown.
- Finish walking to the selected standing spot when vanilla navigation stops short; recheck actual reach and try another approach when needed. This prevents pantry and job interactions from stalling at the edge of reach.
- Use the actual slab, dirt-path and carpet surfaces for recall and stuck rescues instead of requiring full-block floors.
- Keep hauling warnings visible when Courier jobs are disabled, unstaffed or their assigned citizen is unavailable for work.
- Add configurable server diagnostics for sustained worker stalls, blocked routes, stuck rescues, recall failures and missing citizens; include context and rate limits. Log crafting recipe exceptions with their recipe ID and stack trace.
- Include full job barrels and missing repair/enchanting materials in the Needs list.
- Fix citizens rejecting reachable job barrels and work furniture on bottom slabs and carpets. Approach checks use the floor's actual collision surface while keeping the four-block reach limit and blocked-wall checks.
- Show filled and enabled job places in the town header; list support stations separately. The Jobs tab distinguishes loaded openings, disabled places and places waiting for loading or trade.
- Explain why citizens have no job instead of suggesting higher priorities when all enabled crews are full.
- Distinguish missing job barrels from barrels a worker cannot reach, explain barrel ownership near warehouses and overlapping stations, and show complete hover text beside row buttons.

Install **0.1.0.1** on both clients and servers. Existing town saves, jobs and inventories remain compatible.

## World War MC 0.1.0.0

- Start the four-part public version series. The leading **0** marks ongoing development.
- Carry forward the settlement and campaign features from the earlier **0.14.0-alpha** build.
- Replace the generic mod description with a gameplay summary and add source and issue links.
- Name CI downloads with the mod version, loader, and Minecraft version; include only the distributable JAR.

This is a numbering reset. Existing testers should replace the old WWMC JAR and use matching client and server versions. The mod ID and saved-data formats are unchanged.

## Earlier development builds

The notes below keep their original internal version numbers. They record when features were introduced; install the current public build for today's behavior.

## 0.14.0-alpha

This build makes the town easier to read in play. **Working citizens** use small swings, hand poses, particles and quiet nearby sounds while mining, chopping, farming, enchanting, crafting, cooking, smithing, fishing, butchering or treating patients. **Job clothes** combine profession outfits with distinct colored sashes, cuffs and hats; the saved job determines the look even while a citizen waits or rests. Real equipment still renders over the outfit.

**Town colors** now match the main flag and four physical corner banners. The owner chooses a color under **Relationships → Town → Next color**. Existing towns receive a stable color; missing corner flags are repaired on safe loaded terrain without replacing buildings or loading distant chunks.

The **Settlement Guide** opens a custom handbook with **nine short topics**, item icons, supply-chain diagrams, search and visual 3×3 station recipes. Right-click an existing guide, craft one, or use `/wwmc guide`. Press **?** on a town or station screen for relevant help. Press **L** for the new **WWMC advancement tree**: 24 milestones introduce housing, storage, couriers, production, recovery and the campaign. Work milestones require real completed jobs. Existing town saves and guide items remain compatible; use matching **0.14.0-alpha** builds on server and clients.


## 0.13.0

0.13.0 adds seven systems for running a larger realm. The flag's new **Needs** tab lists what the town lacks, such as a cook with no coal, injured citizens without hospital beds, or a full warehouse, most urgent first. Its **Show** button outlines the station in the world, visible through walls. Citizens earn **experience** at their jobs, with modest bonuses. **Varied, hearty meals** keep them full longer and working a little faster. Guard Stations get **roles**: swordsman, shield guard or archer. Owners can mark **patrol routes**, and raised **watchtowers** give earlier warnings. Expeditions gain **objectives**: rescue captives, recover stolen supplies, defeat a fortified Bandit Captain, or defend a raided neighbor. Outposts mine their **region's ore**, and the new **Research** tab spends those regional goods on lasting improvements. A shared **settlement map** shows claims, routes, sites and pings for you and your allies. See [Needs, experience and morale](settlement-guide.md#needs-experience-and-morale), [Guard stations and posts](settlement-guide.md#guard-stations-and-posts) and [Multiplayer campaign](multiplayer-campaign.md).

## 0.12.3

0.12.3 fixes appliance loading for cooks and smelters, adds farm and mine yield upgrades and pickaxe-scaled vein replenishment, and makes actual hospital beds the only place injured citizens rest until full health. See [Production and recovery](production-recovery.md) for setup and balance.

This build also gives **every job block one citizen, except quarries**. This includes cooks, smelters, butchers, lumberjacks, couriers, blacksmiths and guards. Quarries keep eight workers by default, their server setting, and crew upgrades. Add more stations to expand production, hauling or defense. Older extra workers take open jobs without losing their names or inventories; valid range upgrades and quarry progress remain. Crew upgrades and configurable crew sizes now apply only to quarries. Install matching 0.12.3-alpha builds on the server and every client.

## 0.12.1

0.12.1 adds a dedicated **Relationships** screen at the flag: player invitations and permissions, alliance controls, and editable town names. Player claims deny interactions until permission is accepted. Entry notices show the current town name. Flags survive explosions and cannot be moved by pistons, and lost flags can be restored without resetting a town. For a server failing with **Overworld settings missing**, see [World metadata recovery](server-startup.md).

## 0.12.0

0.12.0 adds a **multiplayer campaign loop**: accepted town membership, reciprocal alliances, industry specialization, demand-driven shipments, guard squads, bandit camps and occupied mines, supplied outposts, material-funded projects, working hospital medics, NPC supply contracts and a persistent town journal. Open **Campaign** at a town banner; use `/wwmc squad` for field orders away from home. Existing town identities, ownership, job assignments and inventories are preserved. See [Multiplayer campaign](multiplayer-campaign.md) for project costs and a two-player walkthrough.

## 0.11.3

0.11.3 **brings stranded citizens back**. A citizen could freeze at the edge of the area players keep loaded, or be left behind in a chunk that unloaded during an errand. Its station then sat idle, with the citizen listed as out of range. Each town now remembers where its citizens were last seen. When a citizen's station is loaded but the citizen is not, the town loads a 3×3 chunk window around its last place for a few seconds. The citizen is then moved beside its station and keeps its job. A citizen without a job is brought to the banner instead. A citizen that is not found after two searches leaves the roster, so its job and population place open up, and it rejoins if it turns up later. Citizens who went missing before this update have no recorded place: they leave the roster after five minutes with their station loaded, and also rejoin if found. A station's Crew tab and `/wwmc citizens` show where each missing citizen was last seen. See [Personal inventories](settlement-guide.md#personal-inventories).

## 0.11.2

0.11.2 makes **traders plan their whole trip**. Before, a trader only looked about 16 blocks ahead and walked toward the destination in a straight line, so it headed into forests and stopped at rivers even when a cleared highway or a bridge was nearby. Traders now keep a map of the land between the two towns, read from chunks already loaded around players, towns and traders. They plan a route that prefers roads, takes open ground over forest, and crosses water only on bridges. A route is reported blocked only after 15 seconds without progress, with the spot where the trader is stuck. See [Trading and other settlements](settlement-guide.md#trading-and-other-settlements).

## 0.11.1

0.11.1 fixes enchanters reporting usable tables as unreachable without walking. They choose reachable, clear standing ground within four-block work reach instead of trying to path into the table block. Blocked views remain blocked; opening an entrance or changing the furniture lets them try a new approach without blacklisting the table itself. Existing enchanting progress and local courier supplies are preserved.

## 0.11.0

0.11.0 adds **hunters, fishermen, animal keepers and butchers**, with a whole-carcass → raw-portions → cooked-meals food chain. **Couriers are now the only haulers within a town**: production workers use their own job barrels, and wait for deliveries or free storage. Every mine has exactly **one miner**, including older upgraded mines. Regular meals are three times less frequent by default (six loaded minutes), and scarce food is shared with priority for hungry citizens who were fed least recently. Hire couriers and add local barrels when updating an existing town.

## 0.10.0

0.10.0 makes citizens **keep their jobs** and adds **job priorities**. Each citizen has its own station and goes back to it every morning and after errands, instead of taking whichever station had the smallest crew at that moment. The banner's new **Jobs** tab sets each job to Off, Low, Normal or High: open places in higher-priority jobs fill first and draw citizens from lower ones, and Off frees a job's crew. See [Jobs and priorities](settlement-guide.md#jobs-and-priorities). Pathfinding is fixed: citizens find ordinary routes across a town and around buildings again, which they often gave up on in 0.9.2, while still preferring nearby roads and keeping to bridges over water. A Mine Station now works an exposed ore up to two blocks away as a vein, and its miner walks over and mines an ore touching the station instead of reporting it out of reach.

## 0.9.2

0.9.2 makes every citizen job favor paved paths and solid bridges over grass shortcuts. Citizens on land do not plan swims through open water, and follow bridge bends without cutting corners. Citizens already in water can still get out. Traders also look for narrow bridge decks between their usual waypoints, so wide rivers do not require swimming. Roads work automatically with dirt paths, gravel, common stone paving, planks, slabs, and stairs.

## 0.9.1

0.9.1 fixes traders stopping on walkable routes beneath roofs and tree overhangs. They choose reachable ground near their feet, try shorter and sideways legs, and retry stalled paths without teleporting or losing cargo. A headless world regression checks real delivery and return over 640 blocks, beneath an overhang and around a wall, with no nearby player.

## 0.9.0

0.9.0 adds **multiple owned towns, Trader Blocks, physical supply routes, and small neutral NPC towns**. Each town has one trader checkpoint and one partner. Set exports with Keep and Send controls; traders carry real goods between warehouses, preserving cargo through full storage and restarts. Towns owned by different players require both owners to choose the route. Neutral NPC towns have farming, timber, or mining specialties and use the existing citizen jobs, food, beds, and storage. See [Trading and other settlements](settlement-guide.md#trading-and-other-settlements).

## 0.8.1

0.8.1 makes the in-game server config readable: six sections, short setting names, and hover explanations with units and tick-to-time conversions. Existing config keys, values and defaults remain unchanged.

## 0.8.0

0.8.0 adds **emerald upgrades**. A station's screen sells a wider range or more crew slots, three levels each, and the town screen sells room for more citizens: a new town holds 10, and each population upgrade adds 5, costs more emeralds than the last and makes every enemy wave larger and tougher, bringing pillagers and then vindicators. Farms, craftsmen and the new enchanters always have exactly one worker. A new **Enchanter Station** enchants unenchanted gear and books with lapis at a nearby enchanting table: slowly (about five minutes per item, longer for rarer gear) and never above level 25. Citizens who spot a hostile **call the guards**, who send up to two guards on duty to deal with it; wave attackers **glow**, and any still alive a minute after a wave arrives are reported so the guards hunt them down. Every station and the banner have a new detailed model, turned to face whoever placed it. Existing towns keep their citizens: a town from an earlier build counts as having bought enough population upgrades for everyone it already has.

## 0.7.0

0.7.0 replaces chat read-outs with screens: right-click the settlement banner for a town overview (citizens, stations, food, storage, alarm and waves, with buttons for work priority, the alarm and recruiting), a station for its status, crew and storage, or a citizen for their job, health, meals and equipment above their bag. Craftsmen now learn any crafting-table recipe: click the Craftsman Station's teach slot with an item, then set how many to keep with its slider. Barrels near work stations become job storage, and a new **Courier Station** employs couriers who carry finished goods to the warehouse and keep smelters' and cooks' barrels stocked. A Mine Station touching an ore works it as an endless vein. Farms take one farmer each by default. Short notices now appear above the hotbar instead of in chat. Existing towns keep their stations and progress; their craftsman orders become learned orders.

## 0.6.0

0.6.0 adds a craftable in-game Settlement Guide, shared armor between guard shifts, better armor selection, retirement of equipment below 25% durability, Blacksmith Stations that repair real equipment using warehouse materials, and healing from real meals or food given by the owner. Existing towns and inventories remain compatible.

## 0.5.2

0.5.2 gives citizens four-block work and melee reach, measured from their eyes to block faces or enemy hitboxes. Lumberjacks choose clear standing spots beside trees and cut unprotected natural leaves that block the trunk or trap their body, keeping real drops and consuming axe durability. The tree’s original natural recognition is saved after access clearing, so work can resume after a restart while all player-block and building checks still apply.

## 0.5.1

0.5.1 makes citizens return their previous job's armor, weapons and tools when they change jobs, moves citizens who stay stuck for 30 seconds onto the settlement banner, stops citizens shoving each other off quarry stairs, and only sends quarry crews down a staircase that is intact, rebuilding collapsed steps. Citizens now open doors, craftsmen deliver the tools they make, smelters and cooks no longer burn equipment, and new mines and cave mining stay out of quarry chunks.

## 0.5.0

0.5.0 adds Smeltery and Cook Stations, makes guard shifts independent for each station, and activates every assigned guard when a town bell rings. It retains the staggered citizen updates, shared resource scans, bounded path probes, and quarry crash fix from 0.4.2. Existing worlds, inventories, and quarry progress remain compatible.
