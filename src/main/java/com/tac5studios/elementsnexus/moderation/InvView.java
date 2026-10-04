package com.tac5studios.elementsnexus.moderation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * /inv: look at (or edit) another player's inventory or ender chest in a chest screen.
 * Inventory layout (6 rows): rows 1-3 = main inventory, row 4 = hotbar,
 * row 5 = helmet, chestplate, leggings, boots, offhand, row 6 = empty.
 */
public final class InvView {

    private InvView() {}

    public static void openInventory(ServerPlayer staff, ServerPlayer target, boolean canEdit) {
        Container view = new PlayerInv(target);
        staff.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menu(MenuType.GENERIC_9x6, id, inv, view, 6, canEdit),
                Component.literal(target.getGameProfile().getName() + "'s inventory" + (canEdit ? "" : " (view only)"))));
    }

    public static void openEnderChest(ServerPlayer staff, ServerPlayer target, boolean canEdit) {
        Container view = target.getEnderChestInventory();
        staff.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menu(MenuType.GENERIC_9x3, id, inv, view, 3, canEdit),
                Component.literal(target.getGameProfile().getName() + "'s ender chest" + (canEdit ? "" : " (view only)"))));
    }

    /** A chest screen that ignores every click when it's view-only, and never lets filler panes be taken. */
    private static class Menu extends ChestMenu {
        private final boolean canEdit;
        private final Container view;

        Menu(MenuType<?> type, int id, Inventory own, Container view, int rows, boolean canEdit) {
            super(type, id, own, view, rows);
            this.canEdit = canEdit;
            this.view = view;
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            boolean filler = view instanceof PlayerInv pv && slot >= 0 && slot < 54 && pv.isFiller(slot);
            if (!canEdit || filler) {
                sendAllDataToRemote(); // undo whatever the client thinks happened
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return canEdit ? super.quickMoveStack(player, slot) : ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return view.stillValid(player);
        }
    }

    /** A live window onto a player's real inventory slots. */
    private static class PlayerInv implements Container {
        private final ServerPlayer target;
        private final ItemStack filler;

        PlayerInv(ServerPlayer target) {
            this.target = target;
            this.filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            this.filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        }

        /** Chest slot -> player inventory slot, or -1 for filler. */
        private int map(int slot) {
            if (slot < 27) return slot + 9;            // main inventory
            if (slot < 36) return slot - 27;           // hotbar
            if (slot < 40) return 39 - (slot - 36);    // helmet, chest, legs, boots
            if (slot == 40) return 40;                 // offhand
            return -1;
        }

        boolean isFiller(int slot) {
            return map(slot) < 0;
        }

        @Override public int getContainerSize() { return 54; }
        @Override public boolean isEmpty() { return target.getInventory().isEmpty(); }

        @Override
        public ItemStack getItem(int slot) {
            int s = map(slot);
            return s < 0 ? filler.copy() : target.getInventory().getItem(s);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            int s = map(slot);
            return s < 0 ? ItemStack.EMPTY : target.getInventory().removeItem(s, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int s = map(slot);
            return s < 0 ? ItemStack.EMPTY : target.getInventory().removeItemNoUpdate(s);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int s = map(slot);
            if (s >= 0) target.getInventory().setItem(s, stack);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return map(slot) >= 0;
        }

        @Override
        public void setChanged() {
            target.getInventory().setChanged();
            target.containerMenu.broadcastChanges();
        }

        @Override
        public boolean stillValid(Player player) {
            return !target.hasDisconnected();
        }

        @Override
        public void clearContent() {
            // never clear a player's inventory from here
        }
    }
}
