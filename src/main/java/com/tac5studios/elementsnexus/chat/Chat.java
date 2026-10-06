package com.tac5studios.elementsnexus.chat;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TomlFile;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Public chat: format, rank tag, title, filter, cooldown, mentions, links. */
public final class Chat {

    private static final TomlFile FILE = new TomlFile("chat.toml");
    private static final Pattern URL = Pattern.compile("(?i)^(https?://|www\\.)\\S+\\.\\S+$");

    private static final Map<UUID, Long> LAST_TIME = new HashMap<>();
    private static final Map<UUID, String> LAST_TEXT = new HashMap<>();
    /** Players who turned on staff-chat mode with /sc (memory only). */
    public static final Set<UUID> STAFF_MODE = new HashSet<>();

    /** Set by the moderation feature. Returns true if a player may not chat. */
    public static Predicate<ServerPlayer> muted = p -> false;
    /** Set by the moderation feature. A message to show if the player may not use public chat (jail), or null. */
    public static java.util.function.Function<ServerPlayer, String> publicBlocked = p -> null;

    private Chat() {}

    public static void load() {
        FILE.load();
        ChatFilter.load();
    }

    public static TomlFile file() {
        return FILE;
    }

    // ---------- public chat ----------

    public static void onChat(ServerChatEvent event) {
        if (!Features.on("chat")) return;
        ServerPlayer p = event.getPlayer();
        String raw = event.getRawText();

        // Staff-chat mode: normal chat goes to staff chat instead.
        if (STAFF_MODE.contains(p.getUUID()) && Features.on("staffchat") && Perm.has(p, Perm.STAFFCHAT)) {
            event.setCanceled(true);
            StaffChat.send(p.server, p, raw);
            return;
        }
        if (muted.test(p)) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.literal("You are muted.").withStyle(ChatFormatting.RED));
            return;
        }
        String blocked = publicBlocked.apply(p);
        if (blocked != null) {
            event.setCanceled(true);
            p.sendSystemMessage(Text.color(blocked));
            return;
        }

        boolean staff = Perm.has(p, Perm.CHAT_STAFF);
        if (!staff && !passesCooldown(p, raw)) {
            event.setCanceled(true);
            return;
        }

        // Filter
        String text = raw;
        if (Features.on("chat", "filter") && !Perm.has(p, ChatFilter.bypassNode())) {
            ChatFilter.Result r = ChatFilter.check(text);
            if (r.kind() == ChatFilter.Kind.BLOCK) {
                event.setCanceled(true);
                p.sendSystemMessage(Component.literal("That message was not sent. Please keep chat friendly.")
                        .withStyle(ChatFormatting.RED));
                if (ChatFilter.alertStaff()) StaffChat.alert(p.server, "&c[Filter] &f" + p.getGameProfile().getName() + "&7: " + raw);
                return;
            }
            text = r.text();
        }

        // Links
        if (!staff && !FILE.bool("links.players_can_post", true) && hasLink(text)) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.literal("You can't post links.").withStyle(ChatFormatting.RED));
            return;
        }

        if (!Features.on("chat", "format")) {
            if (!text.equals(raw)) event.setMessage(Component.literal(text)); // keep vanilla look, but filtered
            com.tac5studios.elementsnexus.discord.Discord.gameChat(p, text);
            return;
        }

        // Our own format: cancel vanilla and send the line ourselves.
        event.setCanceled(true);
        Component line = build(p, text, staff, false);
        // Staff see the real name when they hover over a nickname.
        Component staffLine = com.tac5studios.elementsnexus.nick.Nick.get(p.getUUID()) != null ? build(p, text, staff, true) : line;
        MinecraftServer server = p.server;
        server.sendSystemMessage(line); // console
        for (ServerPlayer to : server.getPlayerList().getPlayers()) {
            if (to != p && !staff && ChatData.of(to.getUUID()).ignores.contains(p.getUUID().toString())) continue;
            to.sendSystemMessage(Perm.has(to, Perm.NICK_REALNAME) ? staffLine : line);
        }
        if (FILE.bool("mentions.enabled", true) && FILE.bool("mentions.sound", true)) pingMentioned(server, p, text);
        com.tac5studios.elementsnexus.discord.Discord.gameChat(p, text);
    }

    private static boolean passesCooldown(ServerPlayer p, String raw) {
        long now = System.currentTimeMillis();
        int secs = FILE.num("cooldown.seconds", 2);
        Long last = LAST_TIME.get(p.getUUID());
        if (secs > 0 && last != null && now - last < secs * 1000L) {
            p.sendSystemMessage(Component.literal("Please wait a moment before chatting again.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (FILE.bool("cooldown.block_repeats", true) && raw.equalsIgnoreCase(LAST_TEXT.get(p.getUUID()))) {
            p.sendSystemMessage(Component.literal("Please don't repeat the same message.").withStyle(ChatFormatting.RED));
            return false;
        }
        LAST_TIME.put(p.getUUID(), now);
        LAST_TEXT.put(p.getUUID(), raw);
        return true;
    }

    // ---------- building the line ----------

    private static Component build(ServerPlayer p, String text, boolean staff, boolean realNameHover) {
        String fmt = FILE.str("format.line", "{rank} &7[{title}&7] &f{player}&7: &f{message}");
        String title = Features.on("chat", "title_scrolls_hook") ? TitleHook.title(p) : "";
        if (title.isEmpty() && FILE.bool("format.hide_empty_title", true)) fmt = dropEmptyTitle(fmt);
        fmt = fmt.replace("{rank}", rankPrefix(p))
                .replace("{title}", title)
                .replace("{player}", com.tac5studios.elementsnexus.nick.Nick.display(p));

        int at = fmt.indexOf("{message}");
        String before = at < 0 ? fmt : fmt.substring(0, at);
        String after = at < 0 ? "" : fmt.substring(at + "{message}".length());
        String msgColor = staff ? FILE.str("format.staff_message_color", "&f") : FILE.str("format.player_message_color", "&7");
        // The rank's own chat color replaces the staff / player color.
        String rc = rankChatColor(p);
        if (!rc.isEmpty()) msgColor = rc;
        // Title Scrolls installed: the active title's chat color replaces Nexus's message color.
        // Titles without a chat color keep the Nexus color.
        if (FILE.bool("format.title_chat_color", true) && Features.on("chat", "title_scrolls_hook")) {
            String tc = TitleHook.chatColor(p);
            if (!tc.isEmpty()) msgColor = tc;
        }

        MutableComponent out = Component.empty();
        MutableComponent head = Text.color(Fancy.apply(before, 0));
        if (realNameHover) {
            Component real = Text.color("&7Real name: &f" + p.getGameProfile().getName());
            head = Component.empty().append(head).withStyle(s -> s.withHoverEvent(
                    new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, real)));
        }
        out.append(head);
        out.append(message(p.server, msgColor, text, staff));
        out.append(Text.color(Fancy.apply(after, 0)));
        return out;
    }

    /** The rank's chat tag, followed by a space only if the tag doesn't already end with one. */
    private static String rankPrefix(ServerPlayer p) {
        if (!Features.on("ranks") || !Features.on("chat", "prefixes")) return "";
        Rank r = Ranks.rank(Ranks.rankOf(p.getUUID()));
        return r == null ? "" : r.prefix.stripTrailing();
    }

    /** The rank's chat color, or "" if it has none. */
    private static String rankChatColor(ServerPlayer p) {
        if (!Features.on("ranks")) return "";
        Rank r = Ranks.rank(Ranks.rankOf(p.getUUID()));
        return r == null || r.chatColor == null ? "" : r.chatColor.trim();
    }

    /** Remove "[{title}]" (with its colors and one space) when there is no title. */
    static String dropEmptyTitle(String fmt) {
        int i = fmt.indexOf("{title}");
        if (i < 0) return fmt;
        int l = fmt.lastIndexOf('[', i);
        int r = fmt.indexOf(']', i);
        if (l < 0 || r < 0) return fmt.replace("{title}", "");
        if (l >= 2 && fmt.charAt(l - 2) == '&') l -= 2;     // color code right before "["
        int end = r + 1;
        if (end < fmt.length() && fmt.charAt(end) == ' ') end++; // the space after "]"
        else if (l > 0 && fmt.charAt(l - 1) == ' ') l--;          // or the space before
        return fmt.substring(0, l) + fmt.substring(end);
    }

    /** The message part: base color, clickable links, highlighted names. Staff may use & colors. */
    public static MutableComponent message(MinecraftServer server, String color, String text, boolean allowColors) {
        MutableComponent out = Component.empty();
        String carry = color;
        boolean clickable = FILE.bool("links.clickable", true);
        boolean mentions = FILE.bool("mentions.enabled", true);
        String mentionColor = FILE.str("mentions.color", "&e");
        String[] words = text.split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String shown = allowColors ? w : w.replace("&", "&\u200B"); // stop player color codes
            MutableComponent part;
            if (clickable && URL.matcher(w).matches()) {
                String url = w.toLowerCase(Locale.ROOT).startsWith("www.") ? "https://" + w : w;
                part = Component.literal(w).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
            } else if (mentions && isOnlineName(server, w)) {
                part = Text.color(mentionColor + w);
            } else {
                part = Text.color(carry + shown);
                if (allowColors) carry = carry + codes(w);
            }
            out.append(part);
            if (i < words.length - 1) out.append(Text.color(carry + " "));
        }
        return out;
    }

    /** All &-codes in a word, so colors carry over to the next word. */
    private static String codes(String w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < w.length(); i++) {
            if (w.charAt(i) != '&') continue;
            if (w.charAt(i + 1) == '#' && i + 8 <= w.length()) {
                sb.append(w, i, i + 8);
                i += 7;
            } else {
                sb.append(w, i, i + 2);
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean hasLink(String text) {
        for (String w : text.split(" ")) if (URL.matcher(w).matches()) return true;
        return false;
    }

    private static boolean isOnlineName(MinecraftServer server, String word) {
        String name = word.replaceAll("[^A-Za-z0-9_]", "");
        ServerPlayer p = name.length() >= 3 ? server.getPlayerList().getPlayerByName(name) : null;
        return p != null && !com.tac5studios.elementsnexus.vanish.Vanish.isVanished(p.getUUID());
    }

    private static void pingMentioned(MinecraftServer server, ServerPlayer from, String text) {
        for (String w : text.split(" ")) {
            ServerPlayer to = server.getPlayerList().getPlayerByName(w.replaceAll("[^A-Za-z0-9_]", ""));
            if (to != null && to != from && !com.tac5studios.elementsnexus.vanish.Vanish.isVanished(to.getUUID())) to.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    /** Forget a player's cooldown when they leave. */
    public static void forget(UUID id) {
        LAST_TIME.remove(id);
        LAST_TEXT.remove(id);
        STAFF_MODE.remove(id);
    }
}
