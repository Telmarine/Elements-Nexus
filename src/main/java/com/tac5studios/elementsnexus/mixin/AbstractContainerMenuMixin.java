package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.chat.ItemShow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift + right-click on an item shows it in chat (see ItemShow).
 * When used, the normal shift-click move is skipped; the server then sends the real
 * slot contents back, so the player's screen corrects itself.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void nexus$showItem(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (type != ClickType.QUICK_MOVE || button != 1 || !(player instanceof ServerPlayer sp)) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= self.slots.size()) return;
        if (ItemShow.fromInventory(sp, self, self.slots.get(slotId).getItem())) ci.cancel();
    }
}
