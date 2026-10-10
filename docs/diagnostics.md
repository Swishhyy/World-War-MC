# Server diagnostics

[Project overview](../README.md) · [All guides](README.md) · [Report a bug](https://github.com/Swishhyy/Villager-Colonies/issues)

Villager Colonies writes diagnostics to the hosting game's **console** and **`logs/latest.log`**, including single-player worlds. Search for **`[WWMC]`** to find them; this existing log prefix is retained after the rename.

| Message | What it tells you |
| --- | --- |
| `server-start` | The loaded Villager Colonies version and diagnostic settings. |
| `worker-stalled` | A citizen has kept reporting a missing supply, inaccessible storage, full storage, unavailable hospital bed or blocked trade route. Includes town and citizen names/IDs, dimension, station, current position, activity, target, pantry approach, path destination/completion, path counters, health, meal timer and bag usage. |
| `worker-resumed` | A previously reported citizen has stopped reporting a problem for ten loaded seconds. Check the new activity to see what they are doing. |
| `stuck-rescue` | A citizen tried to walk without moving for 30 seconds. Shows the old location and target, and whether they returned to the banner or lacked standing room. |
| `citizen-recalled` | A citizen returned from outside loaded range. Shows the old/new locations, assigned job and remaining health. Injured citizens can return for hospital care. Recovery uses the actual floor surface, including slabs, dirt paths and carpets. |
| `recall-blocked` | A found citizen could not return. Clear standing room around the station/banner and check their current assignment. Recovery retries after 30 seconds. |
| `citizen-missing` | Searches could not find a citizen. Their roster/job place was freed; they rejoin if found later. |
| `citizen-rejoined` | A citizen previously absent from the roster was seen again. |
| `recipe-error` | A crafting recipe threw an exception while craftsmen indexed it. Includes the recipe ID and stack trace; other recipes remain usable. This always logs, even if routine diagnostics are disabled. |
| `research-start` | A project spent its warehouse supplies once. Includes the town ID, project ID and required work ticks. |
| `research-complete` | A working researcher completed the saved project and granted its unlock once. Includes the town and project IDs. |
| `[contracts]` | A player supply offer reserved payment, was accepted, completed or cancelled. Includes its ID, settlement/player IDs and emerald amount. |
| `[duels]` | A challenge, acceptance or result, including its stake, arena, winner and reason. |
| `[outposts]` | A battle offer, consent, cancellation or capture of the existing outpost. |
| `[multiplayer]` | Restart recovery refunded interrupted duel stakes and cancelled outpost challenges. Player supply contracts and pending payments remain saved. |

Search attempts also use `citizen-search` at **DEBUG** level when the server's logging configuration enables it. Routine idle work, growing crops, replenishing veins, satisfied orders, sleeping citizens and off-duty work do not generate stall warnings. A warning describes an observed condition; it does not automatically mean the mod has a code bug.

## Settings

These flat keys live in the generated `wwmc-server.toml`. In a loaded single-player world, open **Mods → Villager Colonies → Config → Diagnostics**. Multiplayer settings are controlled by the server.

| Setting | Default | Purpose |
| --- | --- | --- |
| `serverDiagnostics` | `true` | Enable worker and recovery diagnostics. |
| `diagnosticStallSeconds` | `120` | Loaded seconds of a sustained worker problem before a WARN message; range 10–3600. |
| `diagnosticRepeatSeconds` | `300` | Minimum seconds between repeat warnings of the same kind for one citizen; range 30–3600. Changing activity or job does not bypass the worker-warning cooldown. |

Brief pauses do not announce a recovery, and unloaded time does not count toward the stall delay. Tracking belongs to each citizen or current recall search, so old worlds and removed citizens do not leave a growing global log cache.

For a bug report, include the relevant `[WWMC]` lines **and nearby exceptions** from `latest.log`, your Minecraft/NeoForge versions, the exact Villager Colonies version or commit, and what happened in game. The `server-start` line shows which Villager Colonies version the game loaded.
