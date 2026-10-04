package com.tac5studios.elementsnexus.kits;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/** One kit as saved in the store ("kits" collection, key = kit name). */
public class Kit {
    /** Seconds between claims. 0 = no wait. -1 = can only be claimed once, ever. */
    public long cooldown = 0;
    /** true = only players with nexus.kit.<name> can claim it. false = everyone can. */
    public boolean locked = false;
    /** The items, saved with all their data (names, enchantments, etc.). */
    public List<JsonElement> items = new ArrayList<>();
}
