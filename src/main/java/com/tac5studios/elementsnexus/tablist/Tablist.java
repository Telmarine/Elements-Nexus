package com.tac5studios.elementsnexus.tablist;

import com.tac5studios.elementsnexus.afk.Afk;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TablistConfig;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab list header/footer, each player's row, sort order and nametags.
 * Sorting and nametags use scoreboard teams named "nx_...". Minecraft sorts the
 * Tab list by team name, so the team name carries the rank priority.
 */
public final class Tablist {

    private static final String TEAM_PREFIX = "nx_";
    private static int ticks;
    private static double phase;

    private Tablist() {}

    // ---------- timer ----------

    public static void tick(MinecraftServer server) {
        if (!Features.on("tablist")) return;
        if (++ticks < TablistConfig.REFRESH.get() * 20) return;
        ticks = 0;
        phase = (phase + 0.08) % 1.0;
        refresh(server);
    }

    /** Update everything for every player now. */
    public static void refresh(MinecraftServer server) {
        if (!Features.on("tablist")) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean headerFooter = Features.on("tablist", "header_footer");
        boolean teams = Features.on("tablist", "sorting") || Features.on("tablist", "nametags");

        for (ServerPlayer p : players) {
            if (headerFooter) {
                int online = Vanish.visibleCount(server, p); // vanished staff aren't counted for players
                p.setTabListHeaderFooter(lines(TablistConfig.HEADER.get(), p, online),
                        lines(TablistConfig.FOOTER.get(), p, online));
            }
            p.refreshTabListName(); // only sends an update when the row actually changed
            if (teams) placeInTeam(server, p);
        }
        if (teams) removeEmptyTeams(server);
    }

    // ---------- player row ----------

    /** NeoForge asks for each player's Tab list name here. */
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!Features.on("tablist", "rank_tags")) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean afk = Features.on("tablist", "afk_marker") && Afk.isAfk(p);
        String line = (afk ? TablistConfig.AFK_ENTRY.get() : TablistConfig.ENTRY.get())
                .replace("{rank}", afk ? plainRank(p) : rankTag(p))
                .replace("{player}", com.tac5studios.elementsnexus.nick.Nick.display(p));
        if (Vanish.isVanished(p)) line = "&7[V] " + line; // only staff who can see vanished players see this row
        event.setDisplayName(Text.color(Fancy.apply(line, phase)));
    }

    /** The colored rank name, e.g. "&6ᴠɪᴘ". */
    public static String rankTag(ServerPlayer p) {
        if (!Features.on("ranks")) return "";
        String name = Ranks.rankOf(p.getUUID());
        if (name.isEmpty()) return "";
        Rank r = Ranks.rank(name);
        String shown = TablistConfig.RANK_SMALL_CAPS.get() ? Fancy.smallCaps(name) : name;
        return (r == null ? "&7" : r.color) + shown;
    }

    /** The colored tag for any rank name. */
    public static String rankTagOf(String name) {
        if (name == null || name.isEmpty()) return "";
        Rank r = Ranks.rank(name);
        String shown = TablistConfig.RANK_SMALL_CAPS.get() ? Fancy.smallCaps(name) : name;
        return (r == null ? "&7" : r.color) + shown;
    }

    /** The rank name with no color (for AFK rows). */
    private static String plainRank(ServerPlayer p) {
        if (!Features.on("ranks")) return "";
        String name = Ranks.rankOf(p.getUUID());
        return TablistConfig.RANK_SMALL_CAPS.get() ? Fancy.smallCaps(name) : name;
    }

    // ---------- header / footer ----------

    private static Component lines(List<? extends String> raw, ServerPlayer p, int online) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < raw.size(); i++) {
            if (i > 0) out.append("\n");
            String line = com.tac5studios.elementsnexus.util.Placeholders.apply(
                    raw.get(i).replace("{online}", String.valueOf(online)), p);
            out.append(Text.color(Fancy.apply(line, phase)));
        }
        return out;
    }

    // ---------- teams (sorting + nametags) ----------

    private static void placeInTeam(MinecraftServer server, ServerPlayer p) {
        ServerScoreboard sb = server.getScoreboard();
        String rank = Features.on("ranks") ? Ranks.rankOf(p.getUUID()) : "";
        Rank r = rank.isEmpty() ? null : Ranks.rank(rank);

        String teamName;
        if (Features.on("tablist", "sorting") && "by_rank".equals(TablistConfig.SORTING.get())) {
            int inverted = 1000 - Math.max(0, Math.min(1000, r == null ? 0 : r.priority));
            teamName = TEAM_PREFIX + String.format("%04d", inverted) + cut(rank, 9);
        } else {
            teamName = TEAM_PREFIX + cut(rank, 13);
        }

        PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
            team.setSeeFriendlyInvisibles(false);
        }
        styleTeam(team, p);

        String who = p.getScoreboardName();
        PlayerTeam current = sb.getPlayersTeam(who);
        if (current != team) sb.addPlayerToTeam(who, team); // also takes them out of their old team
    }

    /** Nametag above the head: the part of the format before {player} and after it. */
    private static void styleTeam(PlayerTeam team, ServerPlayer p) {
        Component prefix = Component.empty();
        Component suffix = Component.empty();
        ChatFormatting nameColor = ChatFormatting.RESET;
        if (Features.on("tablist", "nametags")) {
            String fmt = TablistConfig.NAMETAG.get().replace("{rank}", rankTag(p));
            int at = fmt.indexOf("{player}");
            String before = at < 0 ? fmt : fmt.substring(0, at);
            String after = at < 0 ? "" : fmt.substring(at + "{player}".length());
            prefix = Text.color(Fancy.apply(before, 0));
            suffix = Text.color(Fancy.apply(after, 0));
            nameColor = lastColor(before);
        }
        if (!team.getPlayerPrefix().equals(prefix)) team.setPlayerPrefix(prefix);
        if (!team.getPlayerSuffix().equals(suffix)) team.setPlayerSuffix(suffix);
        if (team.getColor() != nameColor) team.setColor(nameColor);
    }

    /** The last plain color code (&0-&f) in some text. The name itself takes this color. */
    private static ChatFormatting lastColor(String s) {
        ChatFormatting found = ChatFormatting.RESET;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '&') continue;
            ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(i + 1)));
            if (f != null && f.isColor()) found = f;
        }
        return found;
    }

    private static void removeEmptyTeams(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        List<PlayerTeam> remove = new ArrayList<>();
        for (PlayerTeam t : sb.getPlayerTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX) && t.getPlayers().isEmpty()) remove.add(t);
        }
        remove.forEach(sb::removePlayerTeam);
    }

    /** Remove every Nexus team (used when the feature is off, and at startup). */
    public static void clearTeams(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        List<PlayerTeam> remove = new ArrayList<>();
        for (PlayerTeam t : sb.getPlayerTeams()) if (t.getName().startsWith(TEAM_PREFIX)) remove.add(t);
        remove.forEach(sb::removePlayerTeam);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
