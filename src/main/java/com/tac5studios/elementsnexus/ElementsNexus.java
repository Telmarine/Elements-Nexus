package com.tac5studios.elementsnexus;

import com.tac5studios.elementsnexus.afk.Afk;
import com.tac5studios.elementsnexus.chat.Chat;
import com.tac5studios.elementsnexus.commands.AfkCommand;
import com.tac5studios.elementsnexus.commands.ChatCommands;
import com.tac5studios.elementsnexus.commands.HomeCommand;
import com.tac5studios.elementsnexus.commands.KitCommand;
import com.tac5studios.elementsnexus.commands.ModCommands;
import com.tac5studios.elementsnexus.commands.TeleportCommands;
import com.tac5studios.elementsnexus.commands.VanishCommand;
import com.tac5studios.elementsnexus.commands.RankCommand;
import com.tac5studios.elementsnexus.config.AfkConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.ModerationConfig;
import com.tac5studios.elementsnexus.config.StorageConfig;
import com.tac5studios.elementsnexus.config.TablistConfig;
import com.tac5studios.elementsnexus.config.TeleportConfig;
import com.tac5studios.elementsnexus.moderation.Moderation;
import com.tac5studios.elementsnexus.perms.NexusPermissionHandler;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.teleport.Teleports;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dedicated servers only: the mod and its mixins never load on a client or in singleplayer. */
@Mod(value = ElementsNexus.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class ElementsNexus {

    public static final String MOD_ID = "elements_nexus";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElementsNexus");

    public ElementsNexus(IEventBus modEventBus, ModContainer container) {
        // config/elements_nexus/features.toml and storage.toml
        container.registerConfig(ModConfig.Type.COMMON, Features.SPEC, MOD_ID + "/features.toml");
        container.registerConfig(ModConfig.Type.COMMON, StorageConfig.SPEC, MOD_ID + "/storage.toml");
        container.registerConfig(ModConfig.Type.COMMON, TablistConfig.SPEC, MOD_ID + "/tablist.toml");
        container.registerConfig(ModConfig.Type.COMMON, TeleportConfig.SPEC, MOD_ID + "/teleport.toml");
        container.registerConfig(ModConfig.Type.COMMON, AfkConfig.SPEC, MOD_ID + "/afk.toml");
        container.registerConfig(ModConfig.Type.COMMON, ModerationConfig.SPEC, MOD_ID + "/moderation.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.CommandsConfig.SPEC, MOD_ID + "/commands.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.BroadcastConfig.SPEC, MOD_ID + "/broadcast.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.SidePanelConfig.SPEC, MOD_ID + "/sidepanel.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.DiscordConfig.SPEC, MOD_ID + "/discord.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.NickConfig.SPEC, MOD_ID + "/nicknames.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.TogglesConfig.SPEC, MOD_ID + "/toggles.toml");
        container.registerConfig(ModConfig.Type.COMMON, com.tac5studios.elementsnexus.config.WaystonesConfig.SPEC, MOD_ID + "/waystones.toml");

        // Storage starts before anything else and stops after everything else.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerAboutToStartEvent e) -> {
            Storage.start();
            if (Features.on("ranks")) Ranks.ensureDefault();
            Chat.load();
            com.tac5studios.elementsnexus.rules.Rules.load();
            com.tac5studios.elementsnexus.help.Help.load();
            com.tac5studios.elementsnexus.announce.Announcements.load();
            com.tac5studios.elementsnexus.messages.Messages.load();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Storage.tick();
            Tablist.tick(e.getServer());
            Teleports.tick(e.getServer());
            Afk.tick(e.getServer());
            Moderation.tick(e.getServer());
            com.tac5studios.elementsnexus.sidepanel.SidePanel.tick(e.getServer());
            com.tac5studios.elementsnexus.holograms.Holograms.tick(e.getServer());
            if (Features.on("announcements")) com.tac5studios.elementsnexus.announce.Announcements.tick(e.getServer());
        });
        Moderation.setup();
        NeoForge.EVENT_BUS.addListener(Teleports::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, com.tac5studios.elementsnexus.toggles.Pvp::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(com.tac5studios.elementsnexus.toggles.Phantoms::onSpawnPhantoms);
        com.tac5studios.elementsnexus.hooks.WaystoneRules.setup();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Teleports.rememberBack(p); // /back after dying
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
            com.tac5studios.elementsnexus.holograms.Holograms.despawnAll();
            com.tac5studios.elementsnexus.discord.Discord.stop();
        });
        NeoForge.EVENT_BUS.addListener(com.tac5studios.elementsnexus.holograms.Holograms::onJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStoppedEvent e) -> Storage.stop());

        // Permissions
        NeoForge.EVENT_BUS.addListener(Perm::onGatherNodes);
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Handler e) -> {
            if (Features.on("ranks", "permission_handler")) {
                e.addPermissionHandler(NexusPermissionHandler.ID, NexusPermissionHandler::new);
            }
        });

        // Commands
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            RankCommand.register(e.getDispatcher());
            ChatCommands.register(e.getDispatcher());
            HomeCommand.register(e.getDispatcher());
            KitCommand.register(e.getDispatcher());
            AfkCommand.register(e.getDispatcher());
            ModCommands.register(e.getDispatcher());
            TeleportCommands.register(e.getDispatcher());
            VanishCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.ListCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.NexusCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.SidePanelCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.HoloCommand.register(e.getDispatcher());
            if (Features.on("discord")) com.tac5studios.elementsnexus.commands.LinkCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.NickCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.RulesCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.HelpCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.BroadcastCommand.register(e.getDispatcher());
            com.tac5studios.elementsnexus.commands.ToggleCommands.register(e.getDispatcher());
            if (Features.on("announcements")) com.tac5studios.elementsnexus.commands.RestartWarnCommand.register(e.getDispatcher());
        });

        // Staff-only commands: runs last so every mod's commands exist already.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RegisterCommandsEvent e) -> {
            if (!Features.on("staff_only_commands")) return;
            for (String name : com.tac5studios.elementsnexus.config.CommandsConfig.STAFF_ONLY.get()) {
                com.tac5studios.elementsnexus.util.Brig.restrict(e.getDispatcher(), name.replaceFirst("^/", "").trim(),
                        s -> Perm.has(s, Perm.COMMANDS_RESTRICTED));
            }
        });

        // Chat
        NeoForge.EVENT_BUS.addListener(Chat::onChat);
        // Nexus chat turned off (or another mod handles chat): still send chat to Discord.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerChatEvent e) -> {
            if (!Features.on("chat")) com.tac5studios.elementsnexus.discord.Discord.gameChat(e.getPlayer(), e.getRawText());
        });

        // AFK: chatting and commands count as activity (except /afk itself)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerChatEvent e) -> Afk.active(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((CommandEvent e) -> {
            if (e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p
                    && !e.getParseResults().getReader().getString().trim().split("\\s+", 2)[0].equalsIgnoreCase("afk")) {
                Afk.active(p);
            }
        });

        // Tab list
        NeoForge.EVENT_BUS.addListener(Tablist::onTabListName);
        NeoForge.EVENT_BUS.addListener(com.tac5studios.elementsnexus.nick.Nick::onNameFormat);

        // Players
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (Features.on("ranks")) Ranks.rememberName(e.getEntity().getUUID(), e.getEntity().getGameProfile().getName(),
                    e.getEntity() instanceof ServerPlayer sp0 ? sp0.getIpAddress() : null);
            if (e.getEntity().getServer() != null) Tablist.refresh(e.getEntity().getServer());
            if (e.getEntity() instanceof ServerPlayer sp) {
                Moderation.onJoin(sp);
                Vanish.onJoin(sp);
                com.tac5studios.elementsnexus.messages.Messages.onLogin(sp);
                com.tac5studios.elementsnexus.sidepanel.SidePanel.onJoin(sp);
                com.tac5studios.elementsnexus.discord.Discord.join(sp);
                com.tac5studios.elementsnexus.discord.Discord.syncRoles(sp.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) com.tac5studios.elementsnexus.discord.Discord.leave(sp);
            com.tac5studios.elementsnexus.sidepanel.SidePanel.forget(e.getEntity().getUUID());
            Chat.forget(e.getEntity().getUUID());
            Teleports.forget(e.getEntity().getUUID());
            Afk.forget(e.getEntity().getUUID());
            Moderation.forget(e.getEntity().getUUID());
            Vanish.forget(e.getEntity().getUUID());
            com.tac5studios.elementsnexus.nick.Nick.forget(e.getEntity().getUUID());
            com.tac5studios.elementsnexus.toggles.Pvp.forget(e.getEntity().getUUID());
        });

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        long on = Features.all().entrySet().stream()
                .filter(e -> e.getKey().endsWith(".enabled") && e.getValue().get())
                .count();
        LOGGER.info("[Nexus] Loaded. {} features turned on in features.toml.", on);

        // Load the console-name class now. If the mod jar is replaced while the server runs,
        // classes not loaded yet can't be found, and the console (and "stop") would break.
        com.tac5studios.elementsnexus.util.ConsoleName.plain();

        com.tac5studios.elementsnexus.config.Overlap.logWarnings();
        com.tac5studios.elementsnexus.discord.Discord.start(event.getServer());

        // Leftover teams from last run (or the feature was turned off): start clean.
        Tablist.clearTeams(event.getServer());

        if (Features.on("ranks", "permission_handler")
                && !NexusPermissionHandler.ID.equals(PermissionAPI.getActivePermissionHandler())) {
            LOGGER.warn("[Nexus] Other mods can't see Nexus ranks yet. In config/neoforge-server.toml set: "
                    + "permissionHandler = \"{}\" then restart. (Now using: {})",
                    NexusPermissionHandler.ID, PermissionAPI.getActivePermissionHandler());
        }
    }
}
