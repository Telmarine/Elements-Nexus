package com.tac5studios.elementsnexus.ranks;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** One rank as saved in the store ("ranks" collection, key = rank name). */
public class Rank {
    /** Shown before the player's name in chat, e.g. "&6[VIP] ". */
    public String prefix = "";
    /** Rank color for the tab list and nametags, e.g. "&6" or "&#FFD700". */
    public String color = "&7";
    /** Higher = more important. Used for tab list order. */
    public int priority = 0;
    /** Ranks this one gets permissions from. */
    public List<String> inherits = new ArrayList<>();
    /** Permission nodes. "-node" = denied. "feature.*" = everything under it. */
    public List<String> permissions = new ArrayList<>();
    /** Commands run (as the console) when a player ranks up to this rank. {player} = their name. */
    @SerializedName("on_promote")
    public List<String> onPromote = new ArrayList<>();
    /** Players with no rank get the default rank. */
    @SerializedName("default")
    public boolean isDefault = false;
}
