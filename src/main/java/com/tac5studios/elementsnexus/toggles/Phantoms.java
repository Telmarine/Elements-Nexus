package com.tac5studios.elementsnexus.toggles;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TogglesConfig;
import com.tac5studios.elementsnexus.storage.Storage;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;

import java.util.UUID;

/** /phantoms - players choose if phantoms spawn for them. */
public final class Phantoms {

    private static final String COLLECTION = "phantoms"; // uuid -> true/false (only when the player chose)

    private Phantoms() {}

    public static boolean wants(UUID id) {
        Boolean choice = Storage.get().get(COLLECTION, id.toString(), Boolean.class);
        return choice != null ? choice : TogglesConfig.PHANTOMS_DEFAULT_ON.get();
    }

    public static void set(UUID id, boolean on) {
        if (on == TogglesConfig.PHANTOMS_DEFAULT_ON.get()) Storage.get().remove(COLLECTION, id.toString());
        else Storage.get().put(COLLECTION, id.toString(), on);
    }

    public static void onSpawnPhantoms(PlayerSpawnPhantomsEvent event) {
        if (!Features.on("phantoms")) return;
        if (!wants(event.getEntity().getUUID())) event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
    }
}
