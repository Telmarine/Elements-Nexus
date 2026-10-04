# Elements: Nexus — v1 design notes

- Folder: TAC5-Studios/elements-nexus · Mod ID: elements_nexus
- Replaces: TAB + NeoEssentials


## STANDING RULE — keep it simple
- Every setting, switch, and toggle has a short, plain name that says exactly what it does (e.g. `homes`, `tpa_accept`, `join_message`).
- Every `#` comment is one short, direct sentence. No jargon, no side notes, no "note that…".
- If a setting needs more than one line to explain, every line follows the same rule: plain words, short sentences, easy to understand.

## REQUIRED: master control file
`config/elements_nexus/features.toml` (TOML so each switch can carry a comment explaining it) — one boolean per feature and per sub-feature, all in one place, so owners/admins/devs can curate exactly what runs.
- Disabled module = its commands, listeners, permission nodes, and data files are not registered/loaded at all (zero overhead), not just hidden.
- Granular: top-level module switch + sub-switches (e.g. `chat.enabled`, `chat.socialspy`, `chat.staffchat`; `discord.events.join_leave`, `discord.chat_bridge.to_discord`, `discord.chat_bridge.to_game`).
- Covers every item below: permissions, tablist (tags / sorting / header-footer), chat, homes, kits, afk, moderation (each action), staff teleports, rules, console_name, discord (link, role sync, each event, chat bridge both directions), migration importer.
- **Every subcommand / function gets its own switch** (not just per feature), so devs can keep a feature but drop individual pieces. Hierarchy: feature switch off = everything under it off; feature on = each child switch decides. Example:
```json  (structure shown compactly; the real file is TOML with a comment above every switch)
{
  "homes":      { "enabled": true, "home": true, "sethome": true, "delhome": true, "homes_list": true, "visit_others": false },
  "tpa":        { "enabled": false, "tpa": true, "tpa_here": true, "accept": true, "deny": true },
  "teleport":   { "enabled": true, "tp_player": true, "tp_coords": true },
  "spawn":      { "enabled": true, "spawn": false, "spawn_set": true },
  "back":       { "enabled": false },
  "messaging":  { "enabled": true, "msg": true, "reply": true, "ignore": true, "socialspy": true },
  "staffchat":  { "enabled": true },
  "afk":        { "enabled": true, "auto_detect": true, "afk_command": true, "afk_kick": false },
  "kits":       { "enabled": true, "kit": true, "kit_list": true, "kit_create": true, "kit_delete": true, "kit_give": true },
  "warps":      { "enabled": true, "warp": true, "warps_list": true, "warp_set": true, "warp_delete": true },
  "teleport_safety": { "enabled": true, "warmup": true, "combat_lock": true },
  "vanish":     { "enabled": true, "hide_everywhere": true },
  "help":       { "enabled": true },
  "sidepanel":  { "enabled": true, "player_toggle": true },
  "broadcast":  { "enabled": true, "chat": true, "screen": true },
  "announcements": { "enabled": true, "restart_warnings": true },
  "nicknames":  { "enabled": true, "self_nick": false, "nick_others": true },
  "rank_check": { "enabled": true },
  "temp_ranks": { "enabled": false },
  "rules":      { "enabled": true, "view": true, "edit": true },
  "moderation": { "enabled": true, "warn": true, "mute": true, "kick": true, "ban": true, "tempban": true, "freeze": true, "jail": true, "vanish": true, "inv": true, "inv_edit": true, "history": true },
  "ranks":      { "enabled": true, "permission_handler": true, "rank_set": true, "rank_info": true, "group_admin": true },
  "tablist":    { "enabled": true, "rank_tags": true, "nametags": true, "sorting": true, "header_footer": true, "afk_marker": true },
  "chat":       { "enabled": true, "format": true, "prefixes": true, "title_scrolls_hook": true },
  "console_name": { "enabled": true, "name": "Server" },
  "discord":    { "enabled": false, "link": true, "role_sync": true, "role_to_rank": false,
                  "events": { "start_stop": true, "crash": true, "join_leave": true, "rank_up": true, "moderation": true },
                  "chat_bridge": { "to_discord": true, "to_game": true, "staff_chat": true } },
  "migration":  { "enabled": true, "neoessentials": true, "ftb_ranks": true, "ftb_essentials": true, "luckperms": true, "tab": true, "vanilla": true }
}
```
- A disabled child's command node and permission node are not registered at all.
- Safe defaults on first run; file regenerated with missing keys added (never wiping existing values) on update.
- `/nexus reload` applies changes that don't need a restart; the file notes which switches need a restart.

## v1 scope
1. Ranks/permissions — groups, inheritance, priority, prefixes, nodes; registers as NeoForge PermissionAPI handler (other mods see ranks)
2. Tab list / nametags — rank tags + sorting built in; header/footer
3. Chat — format, prefixes, Title Scrolls {active_title} hook, msg/reply/ignore, socialspy, staff chat
4. Homes w/ per-rank limits
5. Kits (one-time that actually works)
6. AFK detection
7. Moderation — kick, warn, mute, tempban, freeze, jail, vanish, invsee/enderchest
8. Staff teleports — tp, tphere, tppos, jump
9. /rules
10. Custom console name — `enabled` boolean + `name` string in config

Not included — moved to separate project **project:monopoly**: economy, auction house, chest shops, NPC shops, holograms, web dashboard, resource packs, vault API

## Commands & permissions (lean: one root per feature, subcommands, no alias spam)
Node pattern: `nexus.<feature>.<action>` · `nexus.<feature>.*` wildcard · `-node` to deny.
Default: P = all players, S = staff (helper+), A = admin/owner.

| Feature | Command | Node | Default |
|---|---|---|---|
| Homes | /home [name] · /sethome [name] · /delhome <name> · /homes | nexus.home.use | P |
| | (limit) | nexus.home.limit.<n> | per rank |
| | /home <player>:<name> (visit others) | nexus.home.others | S |
| Teleport requests | /tpa <player> · /tpa here <player> · /tpa accept · /tpa deny | nexus.tpa.use | P |
| Staff teleport | /tp <player> [target] · /tp <x y z> | nexus.tp.use | S |
| Spawn | /spawn · /spawn set | nexus.spawn.use · .spawn.set | P · A |
| Back | /back | nexus.back.use | P |
| Messaging | /msg <player> <msg> · /r <msg> · /ignore <player> | nexus.msg.use | P |
| | /msg spy (socialspy toggle) | nexus.msg.spy | S |
| Staff chat | /sc <msg> (or /sc to toggle) | nexus.staffchat.use | S |
| AFK | /afk | nexus.afk.use | P |
| | (exempt from AFK kick) | nexus.afk.exempt | S |
| Kits | /kit [name] · /kit list | nexus.kit.use + nexus.kit.<name> | P |
| | /kit create <name> · /kit delete <name> · /kit give <name> <player> | nexus.kit.admin | A |
| Warps | /warp <name> · /warps | nexus.warp.use | P |
| | /warp set <name> · /warp delete <name> | nexus.warp.admin | S |
| Help | /help (lists only commands you can use) | nexus.help | P |
| Side panel | /sidepanel (toggle for yourself) | nexus.sidepanel.toggle | P |
| Broadcast | /broadcast <msg> (chat, console name style) | nexus.broadcast | S |
| | /broadcast screen <msg> [subtitle] (big text in the center of every player's screen) | nexus.broadcast | S |
| Nicknames | /nick <name> · /nick off | nexus.nick.self | off by default |
| | /nick <player> <name> | nexus.nick.others | S |
| Rules | /rules | nexus.rules.view | P |
| | /rules edit (add/remove/set line) | nexus.rules.admin | A |
| Moderation | /warn · /mute [time] · /unmute · /kick · /ban [time] · /unban | nexus.mod.<action> | S (ban: mod+) |
| | /freeze · /jail [time] · /unjail | nexus.mod.freeze · .jail | S |
| | /vanish | nexus.mod.vanish | S |
| | /inv <player> (inventory + ender chest tabs, view; edit with node) | nexus.mod.inv · .inv.edit | S · A |
| | /history <player> (warns/mutes/bans log) | nexus.mod.history | S |
| Ranks | /rank set <player> <rank> · /rank info <player> | nexus.rank.set · .rank.info | A · S |
| | /rank group create/delete/perm add/perm remove/prefix/inherit | nexus.rank.admin | A |
| | /rank check <player> <node> (yes/no + which rank gives it) | nexus.rank.check | S |
| Discord | /link · /unlink | nexus.discord.link | P |
| Admin | `/nexus reload` · `/nexus migrate [source] [preview]` (see Migration) | nexus.admin | A |

Notes:
- Gamemode, give, enchant etc. stay vanilla (OP) — not duplicated.
- Every command above sits behind its feature switch in features.toml.

## Discord integration
- Account link: /link in game → code → enter on Discord
- Rank → role sync, real time (optional role → rank direction)

### Event feed (each event: on/off + channel ID; no raw console mirroring)
| Event | Default channel |
|---|---|
| Server start / stop | staff |
| Crash / watchdog alert | staff |
| Player join / leave | public |
| Rank-up (vote promotion or staff-set) | public |
| Moderation actions (kick, warn, mute, tempban, ban, jail, freeze) | staff |
| In-game chat → Discord | chat channel |
| Discord → in-game chat | chat channel (shows as [Discord] Name: msg) |
| Staff chat ↔ Discord staff channel | staff |

Chat bridge rules: respects mutes both ways; strips @everyone/@here and role pings from in-game messages; Discord messages filtered through the same chat filter.

## Migration (multi-source, optional)
- `/nexus migrate` — auto-detects which supported admin/permission mods' data exists on the server and lists them; `/nexus migrate <source>` runs one. Only offered if that source's data is actually present.
- Planned sources (each needs its data format verified against the 1.21.1 version before building): NeoEssentials, FTB Ranks, FTB Essentials, LuckPerms (NeoForge; via its export file), TAB (groups.yml prefixes/sorting), vanilla (ops.json, banned-players/ips.json).
- Each importer maps what it can (ranks, user→rank, nodes, prefixes, homes, warps, spawn, kits, mutes/bans) and prints a report of anything it skipped.
- Dry-run first (`migrate <source> preview`), originals never modified, backup of current Nexus data taken before writing.

### NeoEssentials specifics
- /neoessentials/store/: permission_groups, permission_users, kits, kit_usages, warps, spawn, afk_data, back locations
- /config/neoessentials/: rules_data.json, chat formats
- Bans: vanilla banned-players.json / banned-ips.json (no conversion)
- Homes: NE's shared DataStore (JSON backend) → `/neoessentials/store/playerdata_homes.json` (same pattern as playerdata_back_locations.json). Older NE versions kept `config/neoessentials/playerdata/<uuid>/homes.json` — importer checks both.
- Mutes: same DataStore → `/neoessentials/store/mutes.json`, `mute_history.json`, `ip_mutes.json`, `ip_mute_history.json`.
- These files are only created on first write, so a server may not have them yet.
- NE can also run SQLite/MySQL/YAML backends — importer reads the JSON backend only in v1 (report anything else as skipped).

## Tab window customization (config/elements_nexus/tablist.toml)
All config files use TOML (supports comments), so every setting carries a plain-English note saying what it does, with the value to change right below it. Example:

```toml
# ============================================================
#  TAB WINDOW  —  what players see when they hold the Tab key
# ============================================================

[header]
# Lines shown ABOVE the player list. One entry = one line.
# Colors: &a, &6 ... or hex like &#FFD700
lines = [
  "&6✦ MY SERVER ✦",
  "&7Welcome, {player}!"
]

[player_entry]
# How EACH player's row looks in the list.
# Available: {rank} {player}
format = "{rank} &f{player}"

[sorting]
# Order of the player list, top to bottom.
# by_rank  = highest rank first (uses rank priority)
# by_name  = alphabetical
mode = "by_rank"

[footer]
# Lines shown BELOW the player list.
# {online} = number of players online
lines = [
  "&7Players online: &a{online}"
]

[refresh]
# How often the tab window updates, in seconds.
# Lower = more up-to-date, slightly more work for the server.
seconds = 5
```
- features.toml switches decide WHETHER each part runs; tablist.toml decides HOW it looks.

## Announcements (config/elements_nexus/announcements.toml)
- A list of messages posted in chat on a timer, in order or random.
- Settings: `interval_minutes`, `order` (`in_order` / `random`), `messages = [ ... ]`.

## Center-screen broadcasts
- `/broadcast screen <msg> [subtitle]` shows big text in the middle of every player's screen (Minecraft title + subtitle), plus a chat copy so nobody misses it.
- Settings in announcements.toml: `screen_fade_in`, `screen_stay`, `screen_fade_out` (seconds), `screen_sound` (sound played with it, empty = none).
- Announcement entries can pick where they show: `chat` or `screen`.
- Built for things like: "Server restarting in 5 minutes".

## Restart warnings
- A list of times before the daily restart, shown center-screen + chat. Example: 10, 5, 1 minutes.
- Settings in announcements.toml: `restart_time` (e.g. "00:00"), `restart_timezone`, `restart_warnings_minutes = [10, 5, 1]`, message text in messages.toml.
- Only posts warnings — it does not restart the server (the panel does that).

## Nicknames
- Shown in chat, tab list, nametag, side panel and Discord, with the real name on hover in chat.
- Player self-nick is off by default (node not given to anyone until an admin adds it).

## Mute coverage
- A muted player cannot: chat, use /msg or /r, send to Discord through the bridge, write on signs, or sign books.
- Staff chat is not blocked for staff who are muted only in public chat (setting: `mute_blocks_staffchat`, default off).

## V2 (later — test for conflicts first)
- Temporary ranks and permissions that expire on their own (`/rank set <player> <rank> 7d`). Switch exists in features.toml, default **off**.

## Teleport safety (homes, warps, spawn, tpa, back)
- Warm-up: teleport starts after a short wait. Default 3 seconds. Moving or taking damage cancels it.
- Combat lock: no player teleports for a set time after hitting or being hit by a player. Default 10 seconds.
- Staff with `nexus.teleport.bypass` skip both.
- Settings: `warmup_seconds`, `combat_lock_seconds` (0 = off).

## Vanish (hidden everywhere)
- A vanished staff member is hidden from: the world, tab list, `{online}` count, side panel, join/leave messages, and the Discord feed.
- Other staff with `nexus.mod.vanish.see` still see them, marked `[V]`.

## AFK marker
- AFK players show `[AFK]` before their name in the tab list.
- Switch: `afk.tab_marker`. Text set in tablist.toml.

## Nametags (section in tablist.toml)
```toml
[nametag]
# What shows above a player's head.
# Available: {rank} {player}
format = "{rank} {player}"
```

## Build & release basics
- Target: NeoForge 21.1.x / Minecraft 1.21.1 only.
- License: same custom license as Title Scrolls (free to use, credit Telmarine, no resale).
- Every feature has a short test checklist. All checks pass on a test server before release.

## Side panel (config/elements_nexus/sidepanel.toml)
- Shows: player name · rank · active chat title · balance (balance only with project:monopoly).
- Same commented-TOML style as tablist.toml: `[title]`, `[lines]` (order + format), `[refresh] seconds`.
- Player toggle: `/sidepanel` (on/off for yourself, remembered across logins) — node `nexus.sidepanel.toggle` (P). Server default on/off set in sidepanel.toml.

## Messages file (config/elements_nexus/messages.toml)
- Every player-facing message in one commented file: errors, confirmations, mod notices ("You have been muted for {time}"), help text.
- Each entry: comment saying when it shows + the placeholders it accepts. Server owners can reword or translate anything.
- Empty string = message disabled (documented in the file header).

## Join / leave / first-join messages & MOTD (in messages.toml + motd section)
- `join`, `leave`, `first_join` (once per new player), each with its own switch; empty = off; `vanilla` keyword = keep Minecraft's default line.
- MOTD shown on login (multi-line, placeholders).
- MUST actually work — acceptance tests before release:
  1. Custom join/leave text replaces (not duplicates) the vanilla line.
  2. Empty value hides the message entirely (no literal "none"/blank line).
  3. first_join fires exactly once per new player, never again after relog/restart.
  4. Vanished staff produce no join/leave line.
  5. Changes apply after `/nexus reload` without restart.

## Rank-ups & vote integration
- `/rank set <player> <rank>` is the single entry point Votifier (or any mod/datapack) calls — works from console, offline players included.
- Optional per-rank `on_promote` command list in ranks config (rewards, announcements); `{player}` placeholder; runs at console level.
- Per-rank `announce` switch (`/rank group announce <rank> on|off`): off = no rank-up broadcast or Discord post for that rank (on_promote still runs).
- Rank-up event fires: announcement (messages.toml), tab/nametag refresh, Discord rank_up event, staff log entry.

## Staff action log (world/elements_nexus/staff_log.jsonl)
- One line per action: time, staff member (or console), action, target, reason, duration.
- Covers: warn, mute/unmute, kick, ban/unban, tempban, freeze, jail, vanish, inv edit, rank set, group/permission edits, kit create/delete, config reload.
- Feeds `/history <player>` and the Discord staff feed. Append-only; never edited by the mod. Optional size-based rollover (setting in features.toml).

## Deferred (decide later)
- Chat config — filter DONE: see chat_filter.toml (block list ~27 words, hide list 9 mild words, allow list, trick-catching). Chat format DONE: see chat.toml (format, private messages, staff chat, cooldown, mentions, links).
- Temp ranks — possible future feature update, only if decided.

## Data storage (same model as NeoEssentials, dev picks the backend)
- One shared data store, organized by key (ranks, users, homes, mutes, kits, warps, spawn, AFK, back locations, Discord links, staff log).
- All player data lives in the store too (not inside player save files), so staff can see and edit offline players.
- Writes are batched: changed entries are marked dirty and flushed on a timer and on shutdown.

### storage.toml
```toml
# Where Nexus saves its data.
# json   = plain files, lightest, best for most servers (default)
# yaml   = plain files, same as json but YAML format
# sqlite = one database file, good for big servers
# mysql  = external database, for networks sharing data between servers
backend = "json"

# Folder for json/yaml files and the sqlite file.
folder = "elements_nexus/store"

# Seconds between saving changed data to disk.
save_interval_seconds = 60

[mysql]
# Only used when backend = "mysql".
host = "localhost"
port = 3306
database = "elements_nexus"
username = "user"
password = "change_me"
```
- If the chosen backend fails to start, Nexus falls back to json, logs a clear warning, and keeps running.
- `/nexus storage convert <backend>` copies all data from the current backend to another.
- Default and lightest: json. SQLite and MySQL drivers are only loaded when selected.

## Placeholders (only these — no extras)
| Surface | Shows |
|---|---|
| Side panel (scoreboard sidebar) | player name · rank · active chat title · balance (balance only when project:monopoly is installed) |
| Tab list | every online player listed as rank · player name; online player count in the tab footer (bottom of the tab window) |
| Discord (chat bridge + event feed) | rank · chat title · player name |

Placeholder keys: `{player}` `{rank}` `{title}` `{balance}` `{online}`. `{online}` = the online player count (a number, e.g. `12`), used in the tab footer. Each surface has its own switch in features.toml.

## Running alongside other mods (overlap guard)
- On startup, Nexus checks for mods that do the same jobs: NeoEssentials, TAB, LuckPerms, FTB Ranks, FTB Essentials.
- For each one found, it turns off its own matching features and logs a clear warning, e.g. "TAB found — Nexus tab list turned off."
  - Permission mods found → Nexus permission handler off.
  - TAB found → tab list, nametags, sorting off.
  - Essentials mods found → overlapping commands (homes, msg, kits, etc.) off.
- Any of these can be forced back on in features.toml: `force_on = ["tablist", "homes"]`.
- This lets Nexus be tested on a live server before the old mods are removed.

## Independence & interoperability (REQUIRED)
- Elements: Nexus and project:monopoly must each run fully on their own AND together. Neither lists the other as a required dependency.
- neoforge.mods.toml: the other mod is declared `type = "optional"` only (load ordering hint), never `required`.
- Integration is soft: detected at runtime with `ModList.get().isLoaded("project_monopoly" / "elements_nexus")`; integration classes are only touched when the other mod is present (no hard class references in shared code paths), so a missing mod can never crash startup.
- Permissions go through NeoForge's PermissionAPI nodes — Monopoly works with Nexus as the permission handler, with any other handler, or with plain vanilla OP levels.
- Planned touch-points when both are installed (each behind its own switch in features.toml, default on):
  - Balance placeholder from Monopoly usable in Nexus chat / tab list
  - Nexus rank available to Monopoly (e.g. rank-based shop limits)
  - Discord event feed (Nexus) can post Monopoly events (auction sold, etc.)
- Each mod ships with its own config, data, and commands; removing one never breaks the other's saved data.
