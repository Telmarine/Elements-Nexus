# Elements: Nexus changelog

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
