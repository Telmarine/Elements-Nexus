package com.tac5studios.elementsnexus.sidepanel;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.SidePanelConfig;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Placeholders;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The side panel (scoreboard sidebar), drawn separately for each player with packets,
 * so every player sees their own name, rank and title. Nothing is added to the world's scoreboard.
 */
public final class SidePanel {

    private static final String NAME = "nexus_panel";
    private static final String COLLECTION = "sidepanel"; // uuid -> true/false (only when the player chose)
    private static final Scoreboard DUMMY = new Scoreboard();

    /** What each player currently sees: title + lines. */
    private static final Map<UUID, List<String>> SHOWN = new HashMap<>();
    private static int ticks;

    private SidePanel() {}

    // ---------- on / off ----------

    public static boolean wants(ServerPlayer p) {
        Boolean choice = Storage.get().get(COLLECTION, p.getUUID().toString(), Boolean.class);
        return choice != null ? choice : SidePanelConfig.DEFAULT_ON.get();
    }

    /** /sidepanel - returns the new state. */
    public static boolean toggle(ServerPlayer p) {
        boolean now = !wants(p);
        if (now == SidePanelConfig.DEFAULT_ON.get()) Storage.get().remove(COLLECTION, p.getUUID().toString());
        else Storage.get().put(COLLECTION, p.getUUID().toString(), now);
        if (now) update(p);
        else hide(p);
        return now;
    }

    public static void onJoin(ServerPlayer p) {
        if (Features.on("sidepanel") && wants(p)) update(p);
    }

    public static void forget(UUID id) {
        SHOWN.remove(id);
    }

    public static void tick(MinecraftServer server) {
        if (!Features.on("sidepanel")) return;
        if (++ticks < SidePanelConfig.REFRESH.get() * 20) return;
        ticks = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (wants(p)) update(p);
            else if (SHOWN.containsKey(p.getUUID())) hide(p);
        }
    }

    // ---------- drawing ----------

    private static Objective objective(Component title) {
        return new Objective(DUMMY, NAME, ObjectiveCriteria.DUMMY, title, ObjectiveCriteria.RenderType.INTEGER,
                false, BlankFormat.INSTANCE);
    }

    private static List<String> build(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        out.add(SidePanelConfig.TITLE.get());
        boolean balance = Placeholders.hasBalance();
        boolean location = Placeholders.hasLocation();
        for (String line : SidePanelConfig.LINES.get()) {
            if (out.size() > 16) break;
            if (line.contains("{balance}") && !balance) continue;
            if (line.contains("{location}") && !location) continue;
            String s = line;
            if (s.contains("{title}")) {
                String t = Features.on("chat", "title_scrolls_hook")
                        ? com.tac5studios.elementsnexus.chat.TitleHook.title(p) : "";
                s = s.replace("{title}", t.isEmpty() ? SidePanelConfig.NO_TITLE.get() : t);
            }
            String line2 = Placeholders.apply(s, p);
            String sep = SidePanelConfig.SEPARATOR.get();
            // A separator between two info lines (never at the top, bottom, or next to an empty line).
            if (!sep.isEmpty() && !line2.isBlank() && out.size() > 1 && !out.get(out.size() - 1).isBlank()) out.add(sep);
            out.add(line2);
        }
        while (out.size() > 16) out.remove(out.size() - 1);
        return out;
    }

    /** Send only what changed since last time. */
    public static void update(ServerPlayer p) {
        List<String> now = build(p);
        List<String> before = SHOWN.get(p.getUUID());
        Objective obj = objective(Text.color(Fancy.apply(now.get(0), 0)));

        if (before == null) {
            p.connection.send(new ClientboundSetObjectivePacket(obj, 0));
            p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
        } else if (!before.get(0).equals(now.get(0))) {
            p.connection.send(new ClientboundSetObjectivePacket(obj, 2));
        }

        int lines = now.size() - 1;
        for (int i = 1; i < now.size(); i++) {
            boolean same = before != null && before.size() == now.size() && before.get(i).equals(now.get(i));
            if (same) continue;
            Component text = Text.color(Fancy.apply(now.get(i), 0));
            p.connection.send(new ClientboundSetScorePacket("nx" + i, NAME, lines - i + 1, Optional.of(text), Optional.empty()));
        }
        if (before != null) {
            for (int i = now.size(); i < before.size(); i++) {
                p.connection.send(new ClientboundResetScorePacket("nx" + i, NAME));
            }
        }
        SHOWN.put(p.getUUID(), now);
    }

    public static void hide(ServerPlayer p) {
        if (SHOWN.remove(p.getUUID()) == null) return;
        p.connection.send(new ClientboundSetObjectivePacket(objective(Component.empty()), 1));
    }
}
