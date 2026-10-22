package com.tac5studios.elementsnexus.hooks;

import com.tac5studios.elementsnexus.ElementsNexus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a player's balance from whatever economy mod provides money through Elements: Vault (for example
 * Elements: Economy). Uses reflection only, so Nexus never needs Vault installed and never touches its classes
 * when it isn't there.
 */
public final class EconomyBalance {

    private static boolean looked;
    private static Method get, primaryCurrency, balance, format;
    private static boolean failedOnce;

    private EconomyBalance() {}

    /** True when Elements: Vault is installed and an economy mod has registered as its provider. */
    public static boolean available() {
        return service() != null;
    }

    /** The player's balance in the provider's main currency, e.g. "3 Block of Emerald, 5 Emerald" or "1,250 Coins". */
    public static String balance(ServerPlayer p) {
        Object svc = service();
        if (svc == null) return "";
        try {
            Object currency = primaryCurrency.invoke(svc);
            if (currency == null) return "";
            Object money = balance.invoke(svc, p.getUUID(), currency);
            Object text = format.invoke(currency, money);
            return text instanceof Component c ? c.getString() : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                ElementsNexus.LOGGER.warn("[Nexus] Could not read the balance from the economy mod: {}", e.toString());
            }
            return "";
        }
    }

    /** The balance split into its parts, e.g. ["3 Block of Emerald", "5 Emerald"]. One part for a plain amount. */
    public static java.util.List<String> parts(ServerPlayer p) {
        String text = balance(p);
        if (text.isEmpty()) return java.util.List.of();
        return java.util.Arrays.stream(text.split(", ")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Each part in the economy_format colors, e.g. "&f3 &aBlock of Emerald". */
    public static java.util.List<String> formatted(ServerPlayer p) {
        String fmt = com.tac5studios.elementsnexus.config.SidePanelConfig.ECONOMY_FORMAT.get();
        return parts(p).stream().map(part -> format(fmt, part)).toList();
    }

    /** "3 Block of Emerald" -> amount "3", name "Block of Emerald". A part without a leading number is all name. */
    private static String format(String fmt, String part) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([^\\s]*\\d[^\\s]*)\\s+(.+)$").matcher(part);
        String amount = m.matches() ? m.group(1) : "";
        String name = m.matches() ? m.group(2) : part;
        if (amount.isEmpty()) fmt = fmt.replace("{amount} ", "").replace("{amount}", "");
        return fmt.replace("{amount}", amount).replace("{name}", name);
    }

    private static Object service() {
        if (!ModList.get().isLoaded("elements_vault")) return null;
        if (!looked) {
            looked = true;
            try {
                Class<?> api = Class.forName("com.tac5studios.elementsvault.EconomyAPI");
                Class<?> service = Class.forName("com.tac5studios.elementsvault.EconomyService");
                Class<?> currency = Class.forName("com.tac5studios.elementsvault.Currency");
                Class<?> money = Class.forName("com.tac5studios.elementsvault.Money");
                get = api.getMethod("get");
                primaryCurrency = service.getMethod("primaryCurrency");
                balance = service.getMethod("balance", UUID.class, currency);
                format = currency.getMethod("format", money);
            } catch (ReflectiveOperationException | LinkageError e) {
                ElementsNexus.LOGGER.warn("[Nexus] Elements: Vault is installed but its API could not be read: {}", e.toString());
                get = null;
            }
        }
        if (get == null) return null;
        try {
            return get.invoke(null) instanceof Optional<?> o ? o.orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
