package com.tac5studios.elementsnexus.chat;

import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Staff-only chat channel. */
public final class StaffChat {

    private StaffChat() {}

    /** Send a staff chat line from a player (or the console when 'from' is null). */
    public static void send(MinecraftServer server, ServerPlayer from, String text) {
        String fmt = Chat.file().str("staff_chat.line", "&c[Staff] {rank} &f{player}&7: &f{message}");
        String rank = "";
        if (from != null) {
            Rank r = Ranks.rank(Ranks.rankOf(from.getUUID()));
            rank = r == null ? "" : r.prefix.stripTrailing();
        }
        fmt = fmt.replace("{rank}", rank).replace("{player}", from == null ? com.tac5studios.elementsnexus.util.ConsoleName.raw() : com.tac5studios.elementsnexus.nick.Nick.display(from));
        int at = fmt.indexOf("{message}");
        String before = at < 0 ? fmt : fmt.substring(0, at);
        String after = at < 0 ? "" : fmt.substring(at + "{message}".length());
        MutableComponent line = Component.empty()
                .append(Text.color(Fancy.apply(before, 0)))
                .append(Chat.message(server, "&f", text, true))
                .append(Text.color(Fancy.apply(after, 0)));
        deliver(server, line);
        com.tac5studios.elementsnexus.discord.Discord.staffChat(
                from == null ? com.tac5studios.elementsnexus.util.ConsoleName.raw() : com.tac5studios.elementsnexus.nick.Nick.display(from), text);
    }

    /** A staff chat line that came from Discord (not sent back to Discord). */
    public static void fromDiscord(MinecraftServer server, Component line) {
        deliver(server, line);
    }

    /** A notice to staff only (filter alerts etc.). */
    public static void alert(MinecraftServer server, String text) {
        deliver(server, Text.color(text));
    }

    private static void deliver(MinecraftServer server, Component line) {
        server.sendSystemMessage(line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Perm.has(p, Perm.STAFFCHAT)) p.sendSystemMessage(line);
        }
    }
}
