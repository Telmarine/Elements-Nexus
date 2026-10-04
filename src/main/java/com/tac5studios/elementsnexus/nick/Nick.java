package com.tac5studios.elementsnexus.nick;

import com.tac5studios.elementsnexus.chat.ChatFilter;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.NickConfig;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.ranks.UserData;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Nicknames. Saved in the "nicknames" store (key = UUID, value = the nickname with & colors). */
public final class Nick {

    private static final String COLLECTION = "nicknames";
    private static final Map<UUID, String> CACHE = new ConcurrentHashMap<>(); // "" = no nickname

    private Nick() {}

    /** The raw nickname (with & colors), or null. */
    public static String get(UUID id) {
        String n = CACHE.computeIfAbsent(id, k -> {
            String s = Storage.get().get(COLLECTION, k.toString(), String.class);
            return s == null ? "" : s;
        });
        return n.isEmpty() ? null : n;
    }

    /** What to show for this player in chat, the Tab list and messages (& colors). */
    public static String display(ServerPlayer p) {
        String n = Features.on("nicknames") ? get(p.getUUID()) : null;
        if (n == null) return p.getGameProfile().getName();
        return NickConfig.MARKER.get() + n + "&r";
    }

    public static void set(ServerPlayer p, String nick) {
        if (nick == null) {
            Storage.get().remove(COLLECTION, p.getUUID().toString());
            CACHE.put(p.getUUID(), "");
        } else {
            Storage.get().put(COLLECTION, p.getUUID().toString(), nick);
            CACHE.put(p.getUUID(), nick);
        }
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    /** Nickname without color codes. */
    public static String plain(String nick) {
        return Text.color(nick).getString();
    }

    /** Null if the nickname is fine, otherwise the reason it isn't. */
    public static String problem(ServerPlayer owner, String nick, boolean colors) {
        if (!colors && nick.contains("&")) return "You can't use colors in nicknames.";
        String plain = plain(nick);
        int min = NickConfig.MIN_LENGTH.get(), max = NickConfig.MAX_LENGTH.get();
        if (plain.length() < min || plain.length() > max) return "Nicknames must be " + min + " to " + max + " characters.";
        if (!plain.matches("[" + NickConfig.ALLOWED.get() + "]+")) return "That nickname has characters that aren't allowed.";
        if (Features.on("chat", "filter") && ChatFilter.check(plain).kind() != ChatFilter.Kind.OK) {
            return "That nickname is not allowed.";
        }
        if (NickConfig.BLOCK_PLAYER_NAMES.get()) {
            String low = plain.toLowerCase(Locale.ROOT);
            if (!low.equals(owner.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
                for (String key : Storage.get().keys(Ranks.USERS)) {
                    UserData u = Storage.get().get(Ranks.USERS, key, UserData.class);
                    if (u != null && low.equals(u.name.toLowerCase(Locale.ROOT))) return "That is another player's name.";
                }
                if (owner.server.getPlayerList().getPlayerByName(plain) != null) return "That is another player's name.";
            }
            for (String key : Storage.get().keys(COLLECTION)) {
                if (key.equals(owner.getUUID().toString())) continue;
                String other = Storage.get().get(COLLECTION, key, String.class);
                if (other != null && plain(other).equalsIgnoreCase(plain)) return "Someone else already has that nickname.";
            }
        }
        return null;
    }

    /** Real names of players whose nickname matches (online and offline). */
    public static List<String> realNames(String nick) {
        List<String> out = new ArrayList<>();
        String want = plain(nick);
        for (String key : Storage.get().keys(COLLECTION)) {
            String n = Storage.get().get(COLLECTION, key, String.class);
            if (n == null || !plain(n).equalsIgnoreCase(want)) continue;
            UserData u = Storage.get().get(Ranks.USERS, key, UserData.class);
            out.add(u != null && !u.name.isEmpty() ? u.name : key);
        }
        return out;
    }

    /** Vanilla uses this name for death, advancement and join messages. */
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Features.on("nicknames")) return;
        if (get(p.getUUID()) == null) return;
        event.setDisplayname(Text.color(display(p)));
    }

    public static void forget(UUID id) {
        CACHE.remove(id);
    }
}
