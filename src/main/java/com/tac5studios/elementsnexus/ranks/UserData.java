package com.tac5studios.elementsnexus.ranks;

import java.util.ArrayList;
import java.util.List;

/** One player as saved in the store ("users" collection, key = UUID). */
public class UserData {
    /** Last known player name. */
    public String name = "";
    /** Rank name. Empty = default rank. */
    public String rank = "";
    /** Last IP address the player joined from (for /banip on offline players). */
    public String lastIp = "";
    /** Extra nodes for just this player. Same rules as rank nodes. */
    public List<String> permissions = new ArrayList<>();
}
