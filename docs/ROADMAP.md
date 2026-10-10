# Simulation and warfare roadmap

[Project overview](../README.md) · [All guides](README.md)

This document describes future design. It is not a list of implemented features.

The **Villager Colonies 0.1.0.0 development build** now adds Stone, Bronze and Iron progression, research shared by accepted settlement members, tin and bronze equipment, five traps, and furnished exploration ruins. Researchers spend time and supplies; higher-tier found gear waits for its unlock. Advanced machine blocks and later ages remain future work. See [ages and research](settlement-guide.md#ages-and-research) for the current system.

## Distant settlement event model

Each town needs a persistent ID, generator cell, faction, development stage, population, workforce, resource ledger, relations, recent event history, revision, last simulation time, and random-step counter. Event definitions contain prerequisites, resource inputs/outputs, weights, cooldowns, and outcome rules.

Suggested first events:

| Event | Preconditions | Bounded result |
| --- | --- | --- |
| Harvest | Farm workforce and available farmland | Food limited by labor, time, and capacity. |
| Production | Workers, facilities, resources, and tools | Inputs consumed before outputs are credited. |
| Shortage | Reserve below target | Priority changes and a future trade request. |
| Migration | Food and a free housing bed | A small population change within capacity. |
| Construction | Approved plan, materials, labor | Plan progress; completed structures materialize once. |
| Convoy departure | Route, available goods, and transport | Reserve cargo and create one persistent convoy ID. |

Previous events influence eligibility and weight. A food shortage can encourage farming; a recent raid can increase escort demand. These are rules over town state, with controlled randomness, rather than unrelated dice rolls.

Generation should use a configurable distance band around players' explored areas, deterministic seed/cell identifiers, spacing rules, and a persisted generated-cell index. Initial suggestions are a 256–768 block band and sparse cells, subject to playtesting. Persist the actual placed position after checking terrain and existing claims on the server thread. Do not force-load unexplored terrain to satisfy a spawn roll.

## Async contract

1. Server thread creates a snapshot containing only plain values and a revision.
2. A bounded worker queue computes a proposed event result using a deterministic random stream.
3. Server thread checks that the town still exists, its revision still matches, and costs remain affordable.
4. Apply the result once, update its event/random counters, mark saved data dirty, and append a bounded history record.
5. Discard stale results and stop/drain workers when the server stops.

Use a bounded simulation interval and capped catch-up window. Towns should not receive days of instantaneous population/resource growth after a long outage. Offline owners' towns must remain inviting to return to, with explicit policy for new attacks and ongoing wars.

## Detailed/abstract handoff

A town has one authoritative simulation mode. Entering player range freezes abstract scheduling, commits outstanding accepted events, then materializes population, structures, and cargo without granting them again. Leaving range snapshots the detailed result before abstract scheduling resumes. Loaded NPCs and abstract worker counts cannot both produce resources for the same interval.

The first alpha intentionally pauses unloaded towns. Keep that safe behavior until the accounting and handoff are verified.

## Structure and room progression

Furniture stations and farms currently declare use within an automatic 7×7×7 range. Lumber stations find roots in that range, fell whole recognized trees, and plant actual saplings. Mines placed near an exposed ore work it as an endless vein; other mines choose and persist a random depth, dig descending access and branch tunnels, and collect reachable cave ores; quarries excavate one neighboring chunk. Barrels at work stations hold each job's supplies and goods, and couriers move goods to the warehouse; inter-town convoys can reuse that hauling. Emeralds buy wider station ranges, more crew and a larger population limit; a future economy can price trade and taxes in the same currency. Shared crews whose citizens keep their stations, job priorities, personal inventories with saved overflow, player-placement protection, floor support, and saved excavation progress are implemented. Citizen names and paired guard day/night posts persist; guards patrol and take actual missing armor from stands. Better forestry terrain handling, tunnel reinforcement/lighting, liquid management, and physical quarry machinery remain future work.

Complete beds and deterministic overlap ownership are implemented; roofs, connected floor space, detailed room quality, and comprehensive reachability validation are future work. Keep the immediate placement preview as richer room validation is added. Later a builder consumes materials against an approved blueprint and changes individual blocks; proposed autonomous expansion still obeys player-selected limits.

Hospital capacity is separate from permanent population capacity. Barracks housing later links to military recruitment. Medical care consumes supplies and uses patient reservations. Owners should be able to authorize some routine construction while reserving major changes for their approval.

## Warfare and world progression

The open development branch adds player supply contracts, staked nonlethal duels and mutually approved expedition outpost capture. These are the first player-versus-player interactions. Full settlement wars, rival-player targeting by guards/traps, convoy raids, peace treaties, tribute and tournaments remain future work. See [multiplayer rules](multiplayer-campaign.md#contested-resource-outposts) for the implemented outpost battle limits.

Town guards currently wear visible armor, find their own swords, spears, bows, and arrows, patrol day/night posts, answer civilians' calls about hostiles, and ring the town bell when a large force appears. Population-scaled monster waves test those defenses; buying a larger population makes them grow and brings pillagers and vindicators, and stragglers glow and are hunted down. Training and coordinated squads come next after the economy works reliably; raids by rival settlements will reuse the alarm and wave machinery. Soldiers consume equipment and food from actual production. Convoys have persistent IDs, source/destination, real reserved cargo, escort strength, and progress. Visible convoys instantiate that same cargo; raiding them consumes or transfers it once and affects the recipient's economy.

Independent settlements can grow into countries composed of multiple towns and shared territory. Local factions may have simpler technology alongside different terrain knowledge, motives, and combat strengths. Technology research should unlock actual production capabilities; its final ceiling is not decided yet.

Open design choices: endgame technology; conquest versus looting; relations and diplomacy; offline war protection; maximum simulation population; whether a player can own multiple countries; and rebuilding after defeats.
