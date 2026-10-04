package com.tac5studios.elementsnexus.messages;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TomlFile;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Join, leave, first-join and MOTD messages from messages.toml. */
public final class Messages {

    private static final TomlFile FILE = new TomlFile("messages.toml");

    private Messages() {}

    public static void load() {
        FILE.load();
    }

    /** True the first time a player ever joins (no play time recorded yet). */
    public static boolean firstJoin(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0;
    }

    /**
     * The join line to broadcast instead of vanilla's, or the vanilla line if this feature is off.
     * Returns null to send nothing.
     */
    public static Component join(ServerPlayer p, Component vanilla) {
        if (!Features.on("messages")) return vanilla;
        if (Features.on("messages", "first_join") && firstJoin(p)) {
            String fmt = FILE.str("first_join.broadcast", "");
            if (isVanilla(fmt)) return vanilla;
            if (!fmt.isEmpty()) return line(p, fmt);
        }
        if (!Features.on("messages", "join_leave")) return vanilla;
        String fmt = FILE.str("join.message", "");
        if (isVanilla(fmt)) return vanilla;
        return fmt.isEmpty() ? null : line(p, fmt);
    }

    public static Component leave(ServerPlayer p, Component vanilla) {
        if (!Features.on("messages", "join_leave")) return vanilla;
        String fmt = FILE.str("leave.message", "");
        if (isVanilla(fmt)) return vanilla;
        return fmt.isEmpty() ? null : line(p, fmt);
    }

    /** "vanilla" = keep Minecraft's own line. */
    private static boolean isVanilla(String fmt) {
        return fmt.trim().equalsIgnoreCase("vanilla");
    }

    /** The rank-up announcement ("" = off). */
    public static String rankUp() {
        return FILE.str("rank_up.message", "&6✦ &f{player} &eranked up to {rank}&e!");
    }

    /** Private lines after login: first-join welcome, then the MOTD. */
    public static void onLogin(ServerPlayer p) {
        if (!Features.on("messages")) return;
        if (Features.on("messages", "first_join") && firstJoin(p)) {
            for (String s : FILE.strings("first_join.private")) p.sendSystemMessage(line(p, s));
        }
        if (Features.on("messages", "motd")) {
            for (String s : FILE.strings("motd.lines")) p.sendSystemMessage(line(p, s));
        }
    }

    private static Component line(ServerPlayer p, String fmt) {
        return Text.color(Fancy.apply(com.tac5studios.elementsnexus.util.Placeholders.apply(fmt, p), 0));
    }
}
