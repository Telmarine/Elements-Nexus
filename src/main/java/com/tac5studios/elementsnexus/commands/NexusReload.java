package com.tac5studios.elementsnexus.commands;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.announce.Announcements;
import com.tac5studios.elementsnexus.chat.Chat;
import com.tac5studios.elementsnexus.discord.Discord;
import com.tac5studios.elementsnexus.help.Help;
import com.tac5studios.elementsnexus.holograms.Holograms;
import com.tac5studios.elementsnexus.messages.Messages;
import com.tac5studios.elementsnexus.moderation.Moderation;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.rules.Rules;
import com.tac5studios.elementsnexus.storage.DataStore;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.vote.Vote;
import com.tac5studios.elementsnexus.world.DragonRespawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * /nexus reload: everything except the on/off switches in features.toml and the storage type.
 * - Hand-written files: chat, chat_filter, rules, help, announcements, messages, vote.
 * - Settings files (tablist, teleport, sidepanel, discord, dragon, ...) are read by NeoForge when saved;
 *   things that only used them at startup are applied again here (Discord connection, dragon timer).
 * - Data files in the storage folder: holograms, kits, warps, spawn, ranks, players, homes and the rest.
 */
public final class NexusReload {

    private NexusReload() {}

    /** Run the reload and return the reply text (& colors). */
    public static String run(MinecraftServer server) {
        long start = System.currentTimeMillis();
        StringBuilder reply = new StringBuilder();

        // Hand-written .toml files
        Chat.load();          // chat.toml + chat_filter.toml
        Rules.load();         // rules.toml
        Help.load();          // help.toml
        Announcements.load(); // announcements.toml
        Messages.load();      // messages.toml
        Vote.load();          // vote.toml

        // Data files
        DataStore.ReloadResult data = Storage.reload();
        if (data != null) {
            Ranks.changed();       // permissions are worked out again from the reloaded ranks
            Nick.clearCache();     // nicknames read again
            Holograms.despawnAll(); // respawned from the reloaded data within a second
            Moderation.resync(server);
        }

        // Settings that were only used at startup
        DragonRespawn.reload();
        boolean discord = Discord.reconnectIfChanged(server);

        // Show the new ranks, prefixes and nicknames right away
        Tablist.refresh(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(p); // command list may change with ranks
        }

        long ms = System.currentTimeMillis() - start;
        reply.append("&aReloaded &7(").append(ms).append(" ms)&a.");
        reply.append("\n&7Settings files: chat, chat_filter, rules, help, announcements, messages, vote.");
        if (data != null) {
            reply.append("\n&7Data files: &f").append(data.reloaded().size()).append(" &7read again");
            if (!data.editedByHand().isEmpty()) {
                reply.append("\n&eEdited by hand, read as saved: &f").append(String.join(", ", data.editedByHand()));
            }
            for (Map.Entry<String, String> f : data.failed().entrySet()) {
                reply.append("\n&cCould not reload ").append(f.getKey()).append(": ").append(f.getValue())
                        .append(" &7(kept as it was)");
            }
        }
        if (discord) reply.append("\n&7Discord settings changed: the bot reconnected.");
        reply.append("\n&7Turning features or commands on/off, and changing the storage type, need a restart.");
        ElementsNexus.LOGGER.info("[Nexus] Reloaded in {} ms.", ms);
        return reply.toString();
    }
}
