package com.sinrok.chronotas;

import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryOps;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChronoTASWorldState {
    public static boolean restoring;
    private ChronoTASWorldState() {}

    public static Snapshot capture(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        Snapshot s = new Snapshot();
        s.worldKey = client.world.getRegistryKey().getValue().toString();
        s.playerX = client.player.getX(); s.playerY = client.player.getY(); s.playerZ = client.player.getZ();
        s.playerYaw = client.player.getYaw(); s.playerPitch = client.player.getPitch();
        s.inventory = captureInventory(client);
        s.playerState = capturePlayerState(client);
        s.selectedSlot = client.player.getInventory().getSelectedSlot();
        BlockPos center = client.player.getBlockPos();
        int minY = Math.max(client.world.getBottomY(), center.getY() - 8);
        int maxY = Math.min(client.world.getBottomY() + client.world.getHeight() - 1, center.getY() + 8);
        for (int x = center.getX() - 8; x <= center.getX() + 8; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = center.getZ() - 8; z <= center.getZ() + 8; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    s.blocks.put(Long.toString(pos.asLong()), encode(client.world.getBlockState(pos)));
                }
        s.entities.addAll(captureEntities(client, 64));
        return s;
    }


    public static PlayerState capturePlayerState(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        PlayerState s = new PlayerState();
        s.health = client.player.getHealth();
        s.absorption = client.player.getAbsorptionAmount();
        s.food = client.player.getHungerManager().getFoodLevel();
        s.saturation = client.player.getHungerManager().getSaturationLevel();
        s.air = client.player.getAir();
        s.fireTicks = client.player.getFireTicks();
        s.experienceLevel = client.player.experienceLevel;
        s.totalExperience = client.player.totalExperience;
        s.experienceProgress = client.player.experienceProgress;
        var ops = RegistryOps.of(JsonOps.INSTANCE, client.world.getRegistryManager());
        for (StatusEffectInstance effect : client.player.getStatusEffects()) {
            try { s.effects.add(ChronoTASClient.GSON.toJson(StatusEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow())); }
            catch (Exception ignored) { }
        }
        return s;
    }

    public static void restorePlayerState(MinecraftClient client, PlayerState state) {
        if (client.player == null || client.world == null || state == null) return;
        var player = client.player;
        player.setHealth(Math.max(0.0f, Math.min(state.health, player.getMaxHealth())));
        player.setAbsorptionAmount(Math.max(0.0f, state.absorption));
        player.getHungerManager().setFoodLevel(Math.max(0, Math.min(20, state.food)));
        player.getHungerManager().setSaturationLevel(Math.max(0.0f, Math.min(state.saturation, state.food)));
        player.setAir(state.air);
        player.setFireTicks(Math.max(0, state.fireTicks));
        player.experienceLevel = Math.max(0, state.experienceLevel);
        player.totalExperience = Math.max(0, state.totalExperience);
        player.experienceProgress = Math.max(0.0f, Math.min(1.0f, state.experienceProgress));
        player.clearStatusEffects();
        var ops = RegistryOps.of(JsonOps.INSTANCE, client.world.getRegistryManager());
        if (state.effects != null) for (String encoded : state.effects) {
            try {
                StatusEffectInstance effect = StatusEffectInstance.CODEC.parse(ops, JsonParser.parseString(encoded)).result().orElse(null);
                if (effect != null) player.addStatusEffect(effect);
            } catch (Exception ignored) { }
        }
    }

    public static List<EntityState> captureEntities(MinecraftClient client, double radius) {
        List<EntityState> result = new ArrayList<>();
        if (client.world == null || client.player == null) return result;
        Box area = new Box(client.player.getBlockPos()).expand(radius);
        for (Entity e : client.world.getEntitiesByClass(Entity.class, area, entity -> !(entity instanceof PlayerEntity))) {
            // Keep recording bounded in mob-heavy areas to avoid huge TAS files and memory spikes.
            if (result.size() >= 64) break;
            EntityState es = new EntityState();
            es.uuid = e.getUuidAsString();
            es.type = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            es.x = e.getX(); es.y = e.getY(); es.z = e.getZ();
            es.vx = e.getVelocity().x; es.vy = e.getVelocity().y; es.vz = e.getVelocity().z;
            es.yaw = e.getYaw(); es.pitch = e.getPitch();
            es.health = e instanceof LivingEntity le ? le.getHealth() : -1;
            es.hurtTime = e instanceof LivingEntity le ? le.hurtTime : 0;
            es.maxHurtTime = e instanceof LivingEntity le ? le.maxHurtTime : 0;
            result.add(es);
        }
        return result;
    }

    public static void restoreEntities(MinecraftClient client, List<EntityState> states) {
        if (client.world == null || states == null) return;
        for (EntityState es : states) {
            try {
                UUID uuid = UUID.fromString(es.uuid);
                Entity entity = client.world.getEntity(uuid);
                if (entity == null) {
                    Identifier id = Identifier.tryParse(es.type);
                    if (id == null) continue;
                    var type = Registries.ENTITY_TYPE.get(id);
                    if (type == null) continue;
                    entity = type.create(client.world, SpawnReason.LOAD);
                    if (entity == null) continue;
                    entity.setUuid(uuid);
                    entity.refreshPositionAndAngles(es.x, es.y, es.z, es.yaw, es.pitch);
                    if (!client.world.spawnEntity(entity)) continue;
                }
                if (entity instanceof PlayerEntity) continue;
                entity.refreshPositionAndAngles(es.x, es.y, es.z, es.yaw, es.pitch);
                entity.setVelocity(es.vx, es.vy, es.vz);
                if (entity instanceof LivingEntity le) {
                    if (es.health >= 0) le.setHealth(Math.min(es.health, le.getMaxHealth()));
                    le.hurtTime = es.hurtTime;
                    le.maxHurtTime = es.maxHurtTime;
                }
            } catch (RuntimeException ignored) { }
        }
    }

    public static List<String> captureInventory(MinecraftClient client) {
        List<String> result = new ArrayList<>();
        if (client.player == null) return result;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            try {
                result.add(ChronoTASClient.GSON.toJson(ItemStack.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, client.world.getRegistryManager()),
                        client.player.getInventory().getStack(i)).getOrThrow()));
            } catch (Exception e) {
                result.add("{}");
            }
        }
        return result;
    }

    public static void restoreInventory(MinecraftClient client, List<String> inventory, int selectedSlot) {
        if (client.player == null || inventory == null || inventory.isEmpty()) return;
        var playerInventory = client.player.getInventory();
        int count = Math.min(playerInventory.size(), inventory.size());
        for (int i = 0; i < count; i++) {
            try {
                ItemStack stack = ItemStack.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE, client.world.getRegistryManager()), JsonParser.parseString(inventory.get(i)))
                        .result().orElse(ItemStack.EMPTY);
                playerInventory.setStack(i, stack);
            } catch (Exception ignored) { }
        }
        if (selectedSlot >= 0 && selectedSlot < 9) playerInventory.setSelectedSlot(selectedSlot);
    }

    public static void restoreFrameBlocks(MinecraftClient client, Map<String, String> changes) {
        if (client.world == null || changes == null || changes.isEmpty()) return;
        restoring = true;
        try {
            for (Map.Entry<String, String> e : changes.entrySet()) restoreBlock(client.world, e.getKey(), e.getValue());
        } finally { restoring = false; }
    }

    public static void recordBlockAfterChange(World world, BlockPos pos, boolean changed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!changed || restoring || !ChronoTASClient.recording || client.world != world) return;
        String key = Long.toString(pos.asLong());
        ChronoTASClient.currentTickBlockChanges.put(key, encode(world.getBlockState(pos)));
    }

    public static void recordBlockBeforeChange(World world, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (restoring || !ChronoTASClient.recording || client.world != world) return;
        String key = Long.toString(pos.asLong());
        ChronoTASClient.recordedBlockChanges.putIfAbsent(key, encode(world.getBlockState(pos)));
    }

    public static void restore(MinecraftClient client, Snapshot snapshot, Map<String, String> changed) {
        if (client.world == null || client.player == null || snapshot == null) return;
        if (!snapshot.worldKey.equals(client.world.getRegistryKey().getValue().toString())) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[ChronoTAS] State belongs to a different dimension; block restore skipped."), false);
            return;
        }
        restoring = true;
        try {
            for (Map.Entry<String, String> e : snapshot.blocks.entrySet()) restoreBlock(client.world, e.getKey(), e.getValue());
            if (changed != null) for (Map.Entry<String, String> e : changed.entrySet()) restoreBlock(client.world, e.getKey(), e.getValue());
            client.player.refreshPositionAndAngles(snapshot.playerX, snapshot.playerY, snapshot.playerZ, snapshot.playerYaw, snapshot.playerPitch);
            restoreInventory(client, snapshot.inventory, snapshot.selectedSlot);
            restorePlayerState(client, snapshot.playerState);
            restoreEntities(client, snapshot.entities);
        } finally { restoring = false; }
    }

    private static void restoreBlock(World world, String packed, String encoded) {
        try {
            BlockPos pos = BlockPos.fromLong(Long.parseLong(packed));
            BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(encoded)).result().orElse(null);
            if (state != null) world.setBlockState(pos, state, 3);
        } catch (Exception ignored) { }
    }
    private static String encode(BlockState state) {
        try { return ChronoTASClient.GSON.toJson(BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()); }
        catch (Exception e) { return "{\"Name\":\"minecraft:air\"}"; }
    }

    public static final class Snapshot {
        public String worldKey = "";
        public double playerX, playerY, playerZ;
        public float playerYaw, playerPitch;
        public Map<String, String> blocks = new LinkedHashMap<>();
        public List<String> inventory = new ArrayList<>();
        public int selectedSlot;
        public List<EntityState> entities = new ArrayList<>();
        public PlayerState playerState = new PlayerState();
    }
    public static final class PlayerState {
        public float health, absorption, saturation, experienceProgress;
        public int food = 20, air = 300, fireTicks, experienceLevel, totalExperience;
        public List<String> effects = new ArrayList<>();
    }
    public static final class EntityState {
        public String uuid, type;
        public double x, y, z, vx, vy, vz;
        public float yaw, pitch, health;
        public int hurtTime, maxHurtTime;
    }
}
