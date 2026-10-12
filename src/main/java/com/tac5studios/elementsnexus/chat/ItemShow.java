package com.tac5studios.elementsnexus.chat;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TomlFile;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Show an item in chat (bragging rights). Hovering the item in chat shows its full tooltip.
 *   - Type [item] (or [i]) in a chat message: the item in your main hand is shown there.
 *   - Shift + right-click an item in your inventory: posts "Player is showing off [Item]".
 * Settings in chat.toml [item_display].
 */
public final class ItemShow {

    private static final Map<UUID, Long> LAST_SHOW = new HashMap<>();

    private ItemShow() {}

    private static TomlFile file() {
        return Chat.file();
    }

    /** Is the chat keyword part on for this player? */
    public static boolean canUse(ServerPlayer p) {
        return Features.on("chat", "item_display") && Perm.has(p, Perm.CHAT_ITEM);
    }

    /** True if this word is one of the [item] keywords. */
    public static boolean isKeyword(String word) {
        List<String> keys = file().strings("item_display.keywords");
        if (keys.isEmpty()) keys = List.of("[item]", "[i]");
        String w = word.toLowerCase(Locale.ROOT);
        for (String k : keys) if (w.equals(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** How many [item] keywords in one message are swapped for the item (chat.toml item_display.max_per_message). */
    public static int maxPerMessage() {
        return Math.max(0, file().num("item_display.max_per_message", 1));
    }

    /** The item as chat text: [Name] in its rarity color, with the full tooltip on hover, plus the count. */
    public static MutableComponent component(ItemStack stack) {
        MutableComponent out = Component.empty().append(stack.copy().getDisplayName());
        if (stack.getCount() > 1) {
            out.append(Text.color(file().str("item_display.count", " &7x{count}").replace("{count}", String.valueOf(stack.getCount()))));
        }
        return out;
    }

    /** The item as plain text for Discord and the console, e.g. "[Diamond Sword] x2". */
    public static String plain(ItemStack stack) {
        return component(stack).getString();
    }

    /** Swap the [item] keywords in a message for the item's plain name (for Discord). */
    public static String plainMessage(ServerPlayer p, String text) {
        if (!canUse(p) || p.getMainHandItem().isEmpty()) return text;
        String name = plain(p.getMainHandItem());
        String[] words = text.split(" ", -1);
        int left = maxPerMessage();
        for (int i = 0; i < words.length && left > 0; i++) {
            if (isKeyword(words[i])) {
                words[i] = name;
                left--;
            }
        }
        return String.join(" ", words);
    }

    // ---------- shift + right-click in the inventory ----------

    /**
     * Called (from a mixin) when a player shift + right-clicks a slot.
     * Returns true if the click was used to show the item (the normal shift-click is then skipped).
     */
    public static boolean fromInventory(ServerPlayer p, AbstractContainerMenu menu, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !canUse(p)) return false;
        if (!file().bool("item_display.inventory_click", true)) return false;
        if (!(menu instanceof InventoryMenu) && !file().bool("item_display.any_container", false)) return false;

        if (Vanish.isVanished(p)) {
            p.sendSystemMessage(Component.literal("You can't show items while vanished.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (Chat.muted.test(p)) {
            p.sendSystemMessage(Component.literal("You are muted.").withStyle(ChatFormatting.RED));
            return true;
        }
        String blocked = Chat.publicBlocked.apply(p);
        if (blocked != null) {
            p.sendSystemMessage(Text.color(blocked));
            return true;
        }
        boolean staff = Perm.has(p, Perm.CHAT_STAFF);
        long now = System.currentTimeMillis();
        int secs = file().num("item_display.inventory_cooldown_seconds", 10);
        Long last = LAST_SHOW.get(p.getUUID());
        if (!staff && secs > 0 && last != null && now - last < secs * 1000L) {
            long left = (secs * 1000L - (now - last) + 999) / 1000;
            p.sendSystemMessage(Component.literal("Wait " + left + "s before showing another item.").withStyle(ChatFormatting.RED));
            return true;
        }
        LAST_SHOW.put(p.getUUID(), now);

        String fmt = file().str("item_display.show_format", "{rank} &f{player} &7is showing off {item}");
        fmt = fmt.replace("{rank}", Chat.rankPrefix(p)).replace("{player}", com.tac5studios.elementsnexus.nick.Nick.display(p)).trim();
        int at = fmt.indexOf("{item}");
        String before = at < 0 ? fmt : fmt.substring(0, at);
        String after = at < 0 ? "" : fmt.substring(at + "{item}".length());
        MutableComponent line = Component.empty().append(Text.colorNoLinks(Fancy.apply(before, 0)));
        line.append(component(stack));
        line.append(Text.colorNoLinks(Fancy.apply(after, 0)));

        MinecraftServer server = p.server;
        server.sendSystemMessage(line);
        for (ServerPlayer to : server.getPlayerList().getPlayers()) {
            if (to != p && !staff && ChatData.of(to.getUUID()).ignores.contains(p.getUUID().toString())) continue;
            to.sendSystemMessage(line);
        }
        com.tac5studios.elementsnexus.discord.Discord.gameChat(p, "is showing off " + plain(stack));
        return true;
    }

    public static void forget(UUID id) {
        LAST_SHOW.remove(id);
    }
}
