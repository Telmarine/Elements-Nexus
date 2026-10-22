# Elements: Nexus changelog

## 1.0.3

### Changed
- **Side panel balance from an economy mod.** `{balance}` now shows the player's balance from an installed economy mod, in that mod's own currency names (e.g. "3 Block of Emerald, 5 Emerald"). `source` under `[balance]` in sidepanel.toml: `auto` (economy mod first, then coin items), `economy`, `coins` or `none`. The old `monopoly` option no longer exists; configs that still say `monopoly` are read as `economy`.

### New
- **Side panel balance layout and colors**, all in sidepanel.toml `[balance]`, applied on save with no restart:
  - `stacked = true`: the first currency stays on the Balance line and the rest go on their own lines, lined up under it. `false` = everything on one line.
  - `economy_format`: how each part looks (default `&f{amount} &a{name}`: white amount, green name).
  - `economy_separator`: what goes between parts on one line (default `&7, `).
  - `economy_align` / `economy_indent`: line extra parts up under the first one, or start them with your own indent instead.
- The default Balance line is now `&eBalance: {balance}` (yellow). Existing sidepanel.toml files keep their own line.

## 1.0.2

### New
- **Show items in chat** - type `[item]` (or `[i]`) in a chat message and the item in your main hand shows in its place. Or shift + right-click an item in your inventory to post "Player is showing off [Item]" for everyone. Hovering the item in chat shows its full tooltip (enchantments, lore, mod info). Shift + left-click still moves items as normal. Only the first `[item]` in a message is swapped by default (`max_per_message`), so one message can't carry a pile of item data. Settings in chat.toml `[item_display]` (keywords, max per message, count text, message format, cooldown, other containers). Switch: `item_display` under `[chat]` in features.toml. Permission: `nexus.chat.item` (everyone).
- **/vote** - shows the vote sites from the new `vote.toml` (header, one line per site, footer). Reload with `/nexus reload`. Switch: `[vote]` in features.toml. Permission: `nexus.vote` (everyone).
- **Clickable links everywhere** - web links in any Nexus text (rules, help, announcements, MOTD, join messages, holograms in chat, broadcasts, vote.toml...) can be clicked. Write `https://...` to show the address, or `[Text](https://...)` to show only the text. Player chat keeps following chat.toml's `[links]` rules, and Discord chat in game follows them too.
- **Discord status channel** - a channel's name shows the player count, e.g. `🟢 Players: 15/150`, and switches to `🔴 Server Offline` when the server stops. Set the channel in discord.toml (`status` under `[channels]`) and the text in the new `[status]` section. Vanished staff aren't counted. Discord allows only 2 name changes per channel every 10 minutes, so it updates every 10 minutes by default (`update_minutes`, 5-60). Use a voice channel nobody can join (text channel names can't have spaces or capitals); the bot needs Manage Channels on it. Switch: `status_channel` under `[discord]` in features.toml.
- **No-elytra worlds** - list worlds in `teleport.toml` (`no_elytra_worlds` under `[elytra]`) where elytra gliding is stopped, e.g. a spawn world. Players drop out of the glide and fall; the elytra stays equipped. Switch: `[elytra]` in features.toml. Staff with `nexus.teleport.bypass` ignore it.
- **Ender Dragon auto respawn** - the dragon respawns a set time after it dies (`dragon.toml` `[respawn] minutes`, default 30). Uses the vanilla respawn: the 4 End Crystals are placed on the exit portal, the pillars rebuild and a new gateway opens. Starts once the time is up and a player is in The End; the timer survives restarts. Optional announcement (`message`). Switch: `[dragon] auto_respawn` in features.toml.
- **Entity limits** (off by default) - per-chunk caps in the new `limits.toml`. Mobs: pets, passive mobs, monsters and villagers; once a chunk is full of a kind, breeding, spawn eggs (players and dispensers) and spawners stop adding more. Placed entities: armor stands, item frames, paintings, minecarts and boats, plus a `custom` list of entity ids or tags. Nothing already in the world is removed. Easy NPC entities never count. Switches: `[entity_limits]` in features.toml (`breeding`, `spawn_eggs`, `spawners`, `placed`). Permission: `nexus.limits.bypass` (staff) to place past the limits. When Elements: Claims is installed these limits turn off on their own (overlap guard) and Claims' `limits.toml` is used.

### Changed
- **Side panel location reads Elements: Claims.** `{location}` shows the claim name (admin claims use the `server` line). Used when Open Parties and Claims and FTB Chunks aren't installed.
- **SQLite and MySQL drivers are no longer included**, so the jar is much smaller (about 0.7 MB instead of 14 MB). json (default) and yaml work as before. To use sqlite or mysql, put the driver jar in `<server>/elements_nexus/drivers`: `sqlite-jdbc` (org.xerial) for sqlite, `mariadb-java-client` (org.mariadb.jdbc) for mysql. Without it, Nexus logs which jar is missing and the server stops starting, so no data is lost or split.

### Fixes
- If the storage backend can't start, Nexus now stops the server instead of switching to an empty json store (where ranks, homes and punishments would be missing).

### Upgrading
- chat.toml is not rewritten: copy the `[item_display]` section from the default file to change those settings (the defaults work without it).
- vote.toml is created on first start; fill in your vote sites.
- Servers on sqlite or mysql: add the driver jar to `elements_nexus/drivers` before updating, or the server won't start.

## 1.0.1

### New
- **/pvp** - players turn their own PvP on or off. Only damage between players is blocked (hits, arrows and other projectiles, tamed pets), so riding and other right-clicks between players still work. There's a cooldown between changes, and it can't be changed right after a fight.
- **/pvp server on|off** - staff turn PvP off (or back on) for the whole server.
- **/phantoms** - players turn phantom spawning on or off for themselves.
- **Waystone rules** (off by default) - block placing, first activation, or teleporting to / from waystones per dimension in the new `waystones.toml`. Placing works with any mod's waystone blocks; activation and teleport rules work with the Waystones mod. Staff with `nexus.waystones.bypass` ignore them. `allow_types` lets chosen waystone types through the teleport rules (for example `waystones:warp_plate`), so staff-built warp plates can still link a locked-down dimension.
- **Number permissions** - ranks can now give numbers to other mods, written as `node=value` (for example `openpartiesandclaims.xaero.pac_max_claims=200` for Open Parties and Claims, with its `permissionSystem` set to `permission_api`). Adding a new value replaces the old one, and `/rank check` shows the value.
- **No-home worlds** - list worlds in `teleport.toml` (`no_home_worlds`) where `/sethome` is refused and existing homes stop working, e.g. resource worlds. Switch: `no_home_worlds` under `[homes]` in features.toml. Staff with `nexus.teleport.bypass` ignore it.
- **Rank chat color** - `/rank group chatcolor <rank> <color|none>` sets the color of a rank's chat messages. An active title's chat color still comes first; ranks without one use chat.toml's colors.
- **Title chat color** - with Title Scrolls installed, chat text uses the active title's chat color. Titles without one keep the normal chat colors. Can be turned off in chat.toml (`title_chat_color`).

### Side panel
- More than one coin type now shows one coin per line under the Balance line (`stacked` in sidepanel.toml), using each coin's own label from the coins list (e.g. `Bronze`, `BC`).
- When none of the coins in sidepanel.toml exist, coin items from installed mods are found on their own.
- `claim_styles` gives single claims their own look, by claim name or by dimension (gradients work).
- The player's name follows their rank color, or their title's color when a title is on.

### Fixes
- `/rank group info` shows "none" for an empty rank color.
- `/help` shows commands with no description as just the command.

### Other
- PvP Toggle added to the overlap guard: Nexus PvP turns itself off when that mod is installed (use force_on to keep it).
- New files: `toggles.toml` (PvP and phantom settings) and `waystones.toml`.
