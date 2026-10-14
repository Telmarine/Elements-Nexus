package com.tac5studios.elementsnexus.limits;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.LimitsConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Predicate;

/**
 * Per-chunk entity limits (features.toml [entity_limits], numbers in limits.toml).
 * <p>
 * Mobs: pets, passive mobs, monsters and villagers. Breeding, spawn eggs and spawners stop adding a kind
 * once the chunk is full. Placed entities: armor stands, item frames, paintings, minecarts, boats and the
 * custom list. Nothing already in the world is removed. Players with nexus.limits.bypass ignore the
 * limits they place themselves.
 * <p>
 * Elements: Claims has the same limits; when it's installed the overlap guard turns these off.
 */
@EventBusSubscriber(modid = ElementsNexus.MOD_ID, value = Dist.DEDICATED_SERVER)
@SuppressWarnings({"unused", "resource"}) // handlers are called by the event bus; levels belong to the server
public final class EntityLimits {

    private static final String F = "entity_limits";

    private enum Mob {
        PETS("pets"), PASSIVE("passive mobs"), MONSTERS("monsters"), VILLAGERS("villagers");

        final String label;

        Mob(String label) {
            this.label = label;
        }

        int max() {
            return switch (this) {
                case PETS -> LimitsConfig.PETS.get();
                case PASSIVE -> LimitsConfig.PASSIVE.get();
                case MONSTERS -> LimitsConfig.MONSTERS.get();
                case VILLAGERS -> LimitsConfig.VILLAGERS.get();
            };
        }
    }

    private EntityLimits() {}

    // ---------- what counts as what ----------

    private static boolean excluded(EntityType<?> type) {
        return "easy_npc".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    private static Mob kind(Entity e) {
        if (excluded(e.getType())) return null;
        if (e instanceof AbstractVillager) return Mob.VILLAGERS;
        if (e.getType().getCategory() == MobCategory.MISC) return null;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return Mob.PETS;
        if (e instanceof Enemy) return Mob.MONSTERS;
        return Mob.PASSIVE;
    }

    /** The kind a new mob of this type would count as (pets can't be known before taming). */
    private static Mob kind(EntityType<?> type) {
        if (excluded(type)) return null;
        if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) return Mob.VILLAGERS;
        MobCategory c = type.getCategory();
        if (c == MobCategory.MONSTER) return Mob.MONSTERS;
        if (c == MobCategory.MISC) return null;
        return Mob.PASSIVE;
    }

    // ---------- counting ----------

    private static int count(Level level, BlockPos pos, Predicate<Entity> test) {
        ChunkPos cp = new ChunkPos(pos);
        AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        return level.getEntities((Entity) null, box, test).size();
    }

    /** null when one more mob of this kind fits in the chunk, else the refusal text. */
    private static String mobRefusal(Level level, BlockPos pos, Mob kind) {
        if (kind == null) return null;
        int max = kind.max();
        if (max < 0 || count(level, pos, e -> kind(e) == kind) < max) return null;
        return LimitsConfig.MSG_MOBS.get().replace("{limit}", kind.label).replace("{max}", String.valueOf(max));
    }

    /** null when one more entity of this type may be placed, else the refusal text. Checks the built-in and custom lists. */
    private static String placedRefusal(Level level, BlockPos pos, EntityType<?> type) {
        String label = null;
        int max = -1;
        Predicate<Entity> test = null;
        if (type == EntityType.ARMOR_STAND) {
            label = "armor stands";
            max = LimitsConfig.ARMOR_STANDS.get();
            test = e -> e.getType() == EntityType.ARMOR_STAND;
        } else if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            label = "item frames";
            max = LimitsConfig.ITEM_FRAMES.get();
            test = e -> e.getType() == EntityType.ITEM_FRAME || e.getType() == EntityType.GLOW_ITEM_FRAME;
        } else if (type == EntityType.PAINTING) {
            label = "paintings";
            max = LimitsConfig.PAINTINGS.get();
            test = e -> e.getType() == EntityType.PAINTING;
        } else if (isMinecart(type)) {
            label = "minecarts";
            max = LimitsConfig.MINECARTS.get();
            test = e -> e instanceof AbstractMinecart;
        } else if (isBoat(type)) {
            label = "boats";
            max = LimitsConfig.BOATS.get();
            test = e -> e instanceof Boat;
        }
        if (test != null && max >= 0 && count(level, pos, test) >= max) return placedText(label, max);
        return customRefusal(level, pos, type);
    }

    private static String customRefusal(Level level, BlockPos pos, EntityType<?> type) {
        for (String entry : LimitsConfig.CUSTOM.get()) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            String target = entry.substring(0, eq).trim();
            int max;
            try {
                max = Integer.parseInt(entry.substring(eq + 1).trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (max < 0 || !matches(type, target)) continue;
            if (count(level, pos, e -> matches(e.getType(), target)) >= max) return placedText(target, max);
        }
        return null;
    }

    private static boolean matches(EntityType<?> type, String target) {
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            return id != null && type.is(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        return target.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    private static String placedText(String label, int max) {
        return LimitsConfig.MSG_PLACED.get().replace("{limit}", label).replace("{max}", String.valueOf(max));
    }

    private static boolean isMinecart(EntityType<?> t) {
        return t == EntityType.MINECART || t == EntityType.CHEST_MINECART || t == EntityType.FURNACE_MINECART
                || t == EntityType.HOPPER_MINECART || t == EntityType.TNT_MINECART || t == EntityType.SPAWNER_MINECART
                || t == EntityType.COMMAND_BLOCK_MINECART;
    }

    private static boolean isBoat(EntityType<?> t) {
        return t == EntityType.BOAT || t == EntityType.CHEST_BOAT;
    }

    // ---------- players placing things ----------

    /** Spawn eggs and entity-placing items used on a block. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? Direction.UP : e.getFace());
        if (refuse(p, at, e.getItemStack())) {
            e.setUseItem(TriState.FALSE);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** A spawn egg used on a mob makes a baby. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getItemStack().getItem() instanceof SpawnEggItem)) return;
        if (refuse(p, e.getTarget().blockPosition(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** Boats and spawn eggs used on water or in the air. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseInAir(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Item item = e.getItemStack().getItem();
        if (!(item instanceof BoatItem) && !(item instanceof SpawnEggItem)) return;
        if (refuse(p, p.blockPosition(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    private static boolean refuse(ServerPlayer p, BlockPos pos, ItemStack stack) {
        if (!Features.on(F) || Perm.has(p, Perm.LIMITS_BYPASS)) return false;
        Item item = stack.getItem();
        String why;
        if (item instanceof SpawnEggItem egg) {
            if (!Features.on(F, "spawn_eggs")) return false;
            EntityType<?> type = egg.getType(stack);
            why = mobRefusal(p.level(), pos, kind(type));
            if (why == null) why = customRefusal(p.level(), pos, type);
        } else {
            EntityType<?> type = placedType(item);
            if (type == null || !Features.on(F, "placed")) return false;
            why = placedRefusal(p.level(), pos, type);
        }
        if (why == null) return false;
        p.displayClientMessage(Text.color(why), true);
        return true;
    }

    private static EntityType<?> placedType(Item item) {
        if (item instanceof ArmorStandItem) return EntityType.ARMOR_STAND;
        if (item == Items.ITEM_FRAME) return EntityType.ITEM_FRAME;
        if (item == Items.GLOW_ITEM_FRAME) return EntityType.GLOW_ITEM_FRAME;
        if (item == Items.PAINTING) return EntityType.PAINTING; // only the vanilla painting
        if (item instanceof MinecartItem) return EntityType.MINECART;
        if (item instanceof BoatItem) return EntityType.BOAT;
        return null;
    }

    // ---------- breeding and spawners ----------

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent e) {
        if (!Features.on(F, "breeding")) return;
        var parent = e.getParentA();
        if (!(parent.level() instanceof ServerLevel level)) return;
        AgeableMob child = e.getChild();
        Mob kind = child != null ? kind(child) : kind(parent);
        if (mobRefusal(level, parent.blockPosition(), kind) != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent e) {
        MobSpawnType t = e.getSpawnType();
        boolean spawner = t == MobSpawnType.SPAWNER || t == MobSpawnType.TRIAL_SPAWNER;
        boolean dispenser = t == MobSpawnType.DISPENSER;
        if (!spawner && !dispenser) return;
        if (!Features.on(F, spawner ? "spawners" : "spawn_eggs")) return;
        var mob = e.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        BlockPos pos = BlockPos.containing(e.getX(), e.getY(), e.getZ());
        if (mobRefusal(level, pos, kind(mob)) != null || customRefusal(level, pos, mob.getType()) != null) {
            e.setSpawnCancelled(true);
        }
    }
}
