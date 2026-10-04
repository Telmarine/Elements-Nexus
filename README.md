# Elements: Nexus

![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange) ![Server-side](https://img.shields.io/badge/side-server-lightgrey) ![License: Custom](https://img.shields.io/badge/license-Custom-blue)

Elements: Nexus is a Server-side essentials all-in-one mod. Nexus covers ranks, the tab list, chat, homes, kits, moderation and more. It fixes alot of issue that, in my opinion, had either never been addressed or updated, despite their options being available in the config files.

In this mod, I have added a Master Control File. Every feature and subcommand has its own on/off switch in `config/elements_nexus/features.toml`. A feature that is switched off doesn't load at all.

## Features

- **Ranks & permissions:** groups, inheritance, prefixes and `nexus.*` nodes. Nexus registers as the NeoForge permission handler, so other mods see your ranks. Rank-ups can run commands on promotion.
- **Tab list:** rank tags, nametags, sorting, a header and footer, and an AFK marker.
- **Side panel:** shows name, rank, balance (with auto-detected coin mods) and claim location (OPAC / FTB Chunks).
- **Chat:** format, filter, mentions and links, plus `/msg`, `/r`, `/ignore`, social spy and staff chat. A Title Scrolls `{title}` hook is included.
- **Travel:** homes with per-rank limits, warps, spawn, back, teleport requests and staff teleports, with warmup and a combat lock.
- **Kits:** one-time and cooldown kits.
- **AFK:** automatic AFK detection.
- **Moderation:** warn, mute, kick, ban, tempban, IP ban, freeze and jail, plus inventory view/edit, history and a rolling staff log. Vanish hides staff everywhere.
- **Messages:** nicknames, `/rules`, `/help` (lists only the commands a player can use), `/broadcast`, announcements, `/restartwarn`, and join, leave, first-join and MOTD messages.
- **Holograms:** vanilla text displays with lines, animation, scale and facing options.
- **Discord bridge:** event feed, two-way chat, staff chat, `/link` and rank-to-role sync.
- **Migration:** imports from NeoEssentials, FTB Ranks, FTB Essentials, LuckPerms and TAB, with a preview before anything is written.
- **Storage:** JSON (default), YAML, SQLite or MySQL. Switch between them with `/nexus storage convert`.
- **Overlap guard:** turns off Nexus features that another installed mod already provides.

## Install

1. Drop `elements_nexus-<version>.jar` into the server's `mods/` folder. Use the full jar, not `-slim`.
2. Start the server once to generate `config/elements_nexus/`.
3. In `config/neoforge-server.toml`, set `permissionHandler = "elements_nexus:permissions"`.
4. Restart the server.

To use the rank permissions, set the handler in step 3.

## Commands

| Command | What it does |
|---|---|
| `/nexus version \| reload` | Show the version or reload configs. |
| `/nexus migrate <source> [confirm]` | Import data from another mod. |
| `/nexus storage convert <type>` | Move data to another storage type. |
| `/rank set \| info \| check \| group …` | Manage ranks and groups. |

The full command and permission list is in [docs/SPEC.md](docs/SPEC.md).

## Building

Requires Java 21. Open the project in IntelliJ, or run `./gradlew build` (`gradlew.bat build` on Windows). The jar is written to `build/libs/`.

## License

Free to use in any modpack, paid or free, with credit to **Telmarine**. It may not be resold or repackaged as a paid product on its own. See [LICENSE](LICENSE).
