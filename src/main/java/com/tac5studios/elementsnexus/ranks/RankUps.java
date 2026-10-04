package com.tac5studios.elementsnexus.ranks;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.discord.Discord;
import com.tac5studios.elementsnexus.messages.Messages;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Runs when a player's rank changes (from /rank set, the console, a vote plugin or a datapack).
 * A rank-up = the new rank has a higher priority than the old one.
 */
public final class RankUps {

    private RankUps() {}

    public static void changed(MinecraftServer server, UUID id, String name, String oldRank, String newRank) {
        Ranks.changed();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            server.getCommands().sendCommands(online);
            online.refreshTabListName();
        }
        Tablist.refresh(server);
        Discord.syncRoles(id);

        Rank before = oldRank == null || oldRank.isEmpty() ? null : Ranks.rank(oldRank);
        Rank after = Ranks.rank(newRank);
        if (after == null) return;
        boolean up = before == null || after.priority > before.priority;
        if (!up || !Features.on("rank_ups")) return;

        String shownName = online != null ? Nick.display(online) : name;
        String tag = Tablist.rankTagOf(newRank);

        if (Features.on("rank_ups", "announce")) {
            String msg = Messages.rankUp();
            if (!msg.isEmpty()) {
                var line = Text.color(msg.replace("{player}", shownName).replace("{name}", name).replace("{rank}", tag));
                server.getPlayerList().broadcastSystemMessage(line, false);
            }
        }
        if (Features.on("rank_ups", "vote_promotions")) {
            for (String cmd : after.onPromote) {
                String run = cmd.replace("{player}", name);
                if (run.startsWith("/")) run = run.substring(1);
                try {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), run);
                } catch (Exception e) {
                    ElementsNexus.LOGGER.warn("[Nexus] on_promote command failed for {}: {}", newRank, run, e);
                }
            }
        }
        Discord.rankUp(name, newRank, Text.color(tag).getString());
    }
}
