# Development guide

The public project name is **Villager Colonies**. Its internal `wwmc` mod ID, Java package, resource namespace, `/wwmc` commands, config filenames and diagnostic prefix are retained for compatibility with existing worlds and integrations. The distributable is now named `villager-colonies-<mod_version>.jar`.

[Project overview](../README.md) · [All guides](README.md) · [Roadmap](ROADMAP.md)

| Looking for | Go to |
| --- | --- |
| Building and testing | [Build and verify](#build-and-verify) |
| Source layout and saved data | [Architecture](#architecture) |
| Player and citizen responsibilities | [Player direction](#player-direction-and-citizen-autonomy) |
| Future simulation work | [Offline and distant settlement design](#offline-and-distant-settlement-design) |
| Development goals | [Stages](#development-stages) · [Roadmap](ROADMAP.md) |

## Player direction and citizen autonomy

The player decides job priorities, approved structures, work sites, and eventually military objectives. Citizens take the open job of highest priority and keep it, and decide how to get to work, when to collect supplies, and when to rest or flee a nearby monster.

Production moves real Minecraft items. Farmer replanting reserves one seed or crop from the harvest; lumber and mining consume tool durability. Workers carry drops home. Furnished residential areas, warehouse supplies, and accessible work sites are the initial town-management loop.

There is no generative AI or external service dependency. Initial decisions use bounded job searches, priorities, reservations, and goal states. Later personalities and faction decisions will build on the same deterministic simulation state.

## Offline and distant settlement design

**Implemented behavior:** loaded citizens continue working when the owner logs off, as long as the server is running and their chunks remain loaded. Unloaded citizens pause and retain their state. A citizen stranded outside the loaded area while its station is loaded is fetched back; see **Out of range** under [Personal inventories](settlement-guide.md#personal-inventories). This development build does not force-load towns, simulate unloaded production, spawn rival towns, or calculate progress while the server is shut down.

**Next simulation phase:** distant towns should use state-based event checks instead of running every citizen physically. See [the roadmap](ROADMAP.md) for the proposed generator and asynchronous simulation contract.

- Generate starter AI settlements within a configurable distance band around explored player areas. Use seeded cells and persisted identifiers so returning to an area does not repeatedly create settlements.
- An event check considers population, food, materials, technology, relations, nearby routes, recent events, and cooldowns. Examples include a harvest, supply shortage, migration, construction proposal, or convoy departure.
- Randomness chooses among eligible events. It cannot bypass resource costs, prerequisites, or cooldowns. Persist the random step and event history so a restart does not reroll outcomes.
- Snapshot the settlement on the server thread, calculate an immutable result asynchronously, then validate its revision and apply it on the server thread. Never read or mutate Minecraft worlds, entities, or inventories from the calculation worker.
- Transfer authority between detailed and abstract simulation explicitly. A resource or convoy must never be produced by both modes.
- Offline balance should preserve daily autonomy and restrict new major attacks against offline owners by default. The exact war rules, combat logout handling, and technology ceiling remain design decisions.

## Architecture

| Area | Responsibility |
| --- | --- |
| `core` | Minecraft-independent bounds, workforce leases, atomic target reservations, and tunnel/quarry geometry. |
| `settlement` | Claims, block ownership/protection, forestry, saved excavation plans, commands, inventory transfers, guard weapons, bell alarms, and enemy waves. |
| `block` / `item` | Banner, automatic role stations with their upgrade levels in the block state, and station inspection. |
| `entity` | Citizen goals, harvesting, supply trips, food, rest, and entity persistence. |
| `client` / `WWMCClient` | Citizen model/renderer and transient range outlines; dedicated servers do not load rendering classes. |
| `src/main/resources` | Block/item models, language, drops (which keep station upgrades), and recipes. |
| `tools/station_models.py` | Generates the station and banner models from vanilla block textures; rerun it after editing a model. |
| `scripts/generate_progression_assets.py` | Recolors vanilla iron textures for tin/bronze and generates equipment, carcass models, research-station assets, recipes, tags and tin world generation; requires Python, Pillow and the official Minecraft 26.2 client JAR. |

Settlement records are dimension SavedData under `wwmc:settlements`. Placement provenance, planting sites, and excavation progress use a separate `wwmc:world_work` record so older settlement saves remain readable. Normal world saves persist both; temporary crew and target reservations expire and are reconstructed after reload. All current gameplay changes happen on the logical server thread. Persistent IDs keep future diplomacy and military systems independent from entity instances.

`TownProgress` saves a research project and its work ticks. New settlements explicitly save equipment tier zero; saves predating the age fields retain their former equipment access through `legacy_gear_tier`. Owner/accepted-member access is checked across server dimensions. The four `wwmc:requires_*` item tags let datapacks add equipment gates. The server's crafting-result mixin rejects locked output before ingredients are consumed; interaction hooks and armor checks enforce use while allowing storage.

## Metal texture recolors

Bronze tools, armor icons and both worn armor layers are color swaps of vanilla iron textures. Tin materials and storage blocks use the corresponding iron textures; tin ores preserve every stone and deepslate background pixel. Tool handles, dark outlines, texture sizes, transparency and armor UV layouts are retained. [View the comparison](images/metal-recolors.png).

The sources are Mojang's assets in the official Minecraft **26.2** client JAR, identified by its [version metadata](https://piston-meta.mojang.com/v1/packages/d367f3dfbc0b3e14688df2311359deb609b234e3/26.2.json). The generator verifies client SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754` before reading textures. The original client JAR is an input and is not checked into the repository.

With Python and Pillow installed, supply that client JAR from your Minecraft installation or development cache:

```bash
python3 scripts/generate_progression_assets.py --client-jar "/path/to/26.2.jar" --textures-only
```

Omit `--textures-only` to also regenerate the existing models, recipes, tags and world-generation data. Ordinary Gradle builds use the checked-in recolored PNGs and do not require Pillow or a texture-generation step.

## Build and verify

Use a **Java 25 JDK**, not just a Java runtime. The Gradle wrapper and ModDevGradle versions are pinned in the repository.

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

On Windows, use `gradlew.bat build` and `gradlew.bat runClient`. Development servers use `./gradlew runServer`.

The dedicated world suite includes trap and wave-approach scenarios, forest work, bronze recipe discovery and equipment, guard bow/shield use, and multiplayer interactions. The progression cases run a real researcher reaching its lectern, verify paid progress and pauses through a save round trip, check distinct Stone Age starter recipes and locked shift-click crafting, return armor with its original damage, grant research only after invitation acceptance, and revoke it on removal. Research status tests check both banner panels against real interruptions without changing progress, charging supplies or loading distant chunks. They seal and restore routes, obstruct a lectern during travel, and verify that an available researcher takes precedence over a paused colleague. They also place the actual tin feature in stone and deepslate, check its drops and furnace recipe, validate castle furniture and complete-footprint protection, and claim a furnished workshop without losing its supply cache. CI also runs the unit checks and starts, saves and reloads a dedicated server.

The multiplayer fixtures verify exclusive player contracts, real escrow and partial delivery into a full warehouse, saved payment collection, physical caravan contract delivery, warehouse reserves, incoming cargo reservations, NPC neighbour routes, public news privacy, legacy preview refunds, and timed owner-approved outpost capture. `MultiplayerData` saves those records separately from settlement policy. Only accepted outpost opponents bypass claim damage protection; building, storage, citizens and research retain their own checks. Existing expedition outposts transfer identity and supplies instead of being generated again.

The daytime work fixtures fix and pause the Overworld daylight clock so earlier batches cannot make later villagers go to bed. Game ticks and normal AI, meals and research cadence still advance. This setup exists only in the test mod and is excluded from the distributable JAR.

`runGameTestServer` checks the Needs list against a real unstaffed kitchen, and research paid for exactly once from a real warehouse chest. The vein test also checks that mining earns experience. It also checks paved detours, narrow bridge bends, unbridged river rejection, and escape from water, a 43-block walk across open grass, a detour through the only gap in a long wall, and a 72-block walk in several legs. Miners must walk across town and work ore veins touching their station and two blocks away, and a farmer with no crops must keep its job beside an open mine until mining is raised to High. Food tests run real hunting, every courier transfer, butchery and vanilla cooking; fishing from a dry bank with an obstructing station; keeper culling with four breeders preserved and a real newborn; production waiting without a courier; and ten hungry citizens sharing ten loaves without stockpiling. It also runs a 640-block trader delivery and return on dirt paths, beneath a roof, around a wall, and over a wide river on a waterlogged slab bridge, with no nearby players, then checks the live warehouse inventories after chunk reload. A second trader must cross a river by its only bridge, sixty blocks to the side of the straight line, without swimming or crossing anywhere else. A cook whose chunk unloads while its kitchen stays loaded must be fetched back with its job kept. A listed citizen who cannot be found where last seen must leave the roster, with no chunks left loaded. The test mod in `src/gameTest` is excluded from the release JAR. GitHub Actions runs these world tests after `build`.

`build` runs regression checks for experience levels and bonuses, meal fullness and variety, guard roles and patrol routes, research, schematics, map pings and expedition objectives through save round trips, including older saves, and for ranges, beds/storage, overlap ownership, shared crews, atomic target claims, tunnel/quarry geometry, random depth/shift boundaries, name uniqueness, construction-aware tree recognition, sapling conservation, local inventory conservation/backlog, armor transfers, weapon classification/ranking, quarry staircase geometry and persistence, quarry collision tracing, four-block surface/vertical reach, blocked work rays, clear tree approach candidates, persisted natural-tree proof after access clearing, role-specific crafting orders, appliance recipe eligibility, furnace slot/component conservation, independent station guard shifts and alarm rosters, alarm thresholds and all-clear timing, wave sizes/composition/timing, claim widening, save round-trips, legacy migration, recipe/drop decoding with Minecraft's codecs, shared shift armor, full-bag overflow armor returns and occupied-slot conservation, armor upgrades, the exact durability cutoff, material-funded repairs with preserved names/enchantments, healing meal/bowl conservation and cooldowns, guide-page bounds and anvil eligibility, learned crafting against the server's real recipes (shaped layout, any-wood planks, container remainders, paused and stocked orders, ingot/block cycle protection, legacy order migration), job barrel collection rules and courier supply loads, ore vein detection and rarity pacing, screen data network round trips, upgrade prices, emerald payments with change, upgraded ranges and fixed crews, wave threat from population upgrades, enchanting rarity timing, lapis costs, the level cap and item priority against the server's real enchantments, job priority presets, job assignments, crew trimming and their save round-trip, trader route planning (straight routes over open and unseen land, a distant bridge, a cleared way beside a forest, roads, a cliff ramp, avoided cells and a river with no crossing), staggered 30-citizen updates, bounded reachability probes, and shared resource scan expiry/invalidation. GitHub Actions builds with Java 25 and names its artifact **villager-colonies-<mod_version>-neoforge-mc26.2**. Local JARs appear in `build/libs/` as `villager-colonies-<mod_version>.jar`. `mod_archive_name` controls the archive name; `mod_id=wwmc` preserves the registry and saved-data namespace. CI verifies the actual JAR metadata and changelog before uploading it. A change to `mod_version` merged into `main` publishes the tested JAR as a regular GitHub release under `villager-colonies-v<mod_version>`, using that version's section of the changelog. Ordinary commits keep producing Actions artifacts without publishing another release; version changes remain the maintainer's decision.

Automated checks do not replace an in-game playtest. Check previews, border placement, shared station crews, protected player logs, large-tree felling/replanting, tunnel/cave pathfinding, quarry staircase descent and climb-out, quarry obstructions and skipped blocks, craftsman/smelter/cook trips, fuel use and appliance output collection, guard shift changes and shared armor returns, blacksmith pickup/repair/return trips, every screen and its buttons, teaching orders and their sliders, courier trips and job barrel use, ore vein mining, station and population upgrades with emeralds, the Jobs tab and citizens keeping their jobs across days, the new station models, enchanter trips and enchanting, guards answering calls and hunting glowing wave stragglers, guide crafting/reading, hospital recovery and direct feeding, armor upgrades/patrol/combat, weapon scavenging and archery, bell runs and civilian cover, wave spawning, inventory menus, tool breakage, bed use, and save/restart behavior before using this development build in an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, shared crews, real inventory, crop/tree cycles, automatic tunnel/cave mining, quarries, named citizens, inventories, armed guards, bell alarms, the first enemy waves, and emerald upgrades.
2. **Self-sustaining small town:** more food processing, approved housing construction, robust room validation, and migration. Craftsmen, couriers and enchanters are the first steps.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** build on town guards with trained soldiers, squad orders, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** territory, deeper diplomacy, sieges, varied faction technology, and conquest rules. Multiple towns and the first neutral settlements and trader routes are playable in 0.9.0.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](../LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](../TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.

Recolored bronze and tin textures derive from Minecraft assets by Mojang. Their source paths and color palettes are recorded in `scripts/generate_progression_assets.py`; Minecraft's original artwork is not authored by this project.
