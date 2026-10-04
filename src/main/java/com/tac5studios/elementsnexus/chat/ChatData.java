package com.tac5studios.elementsnexus.chat;

import com.tac5studios.elementsnexus.storage.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Saved chat settings for one player ("chat" collection, key = UUID). */
public class ChatData {
    /** Players this player has ignored (UUIDs). */
    public List<String> ignores = new ArrayList<>();
    /** Social spy on (staff only). */
    public boolean spy = false;

    public static ChatData of(UUID id) {
        ChatData d = Storage.get().get("chat", id.toString(), ChatData.class);
        return d != null ? d : new ChatData();
    }

    public void save(UUID id) {
        Storage.get().put("chat", id.toString(), this);
    }
}
