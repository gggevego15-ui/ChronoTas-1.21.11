package com.sinrok.chronotas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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
        BlockPos center = client.player.getBlockPos();
        int minY = Math.max(client.world.getBottomY(), center.getY() - 8);
        int maxY = Math.min(client.world.getBottomY() + client.world.getHeight() - 1, center.getY() + 8);
        for (int x = center.getX() - 8; x <= center.getX() + 8; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - 8; z <= center.getZ() + 8; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    s.blocks.put(Long.toString(pos.asLong()), encode(client.world.getBlockState(pos)));
                }
            }
        }
        s.entities.addAll(captureEntities(client, 64));
        return s;
    }

    public static List<EntityState> captureEntities(MinecraftClient client, double radius) {
        List<EntityState> result = new ArrayList<>();
        if (client.world == null || client.player == null) return result;
        Box area = new Box(client.player.getBlockPos()).expand(radius);
        for (Entity e : client.world.getEntitiesByClass(Entity.class, area,
                entity -> !(entity instanceof PlayerEntity))) {
            EntityState es = new EntityState();
            es.uuid = e.getUuidAsString();
            es.type = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
            es.x = e.getX(); es.y = e.getY(); es.z = e.getZ();
            es.vx = e.getVelocity().x; es.vy = e.getVelocity().y; es.vz = e.getVelocity().z;
            es.yaw = e.getYaw(); es.pitch = e.getPitch();
            es.health = e instanceof LivingEntity le ? le.getHealth() : -1;
            result.add(es);
        }
        return result;
    }

    public static void restoreEntities(MinecraftClient client, List<EntityState> states) {
        if (client.world == null || states == null) return;
        for (EntityState es : states) {
            try {
                Entity entity = client.world.getEntity(UUID.fromString(es.uuid));
                if (entity == null || entity instanceof PlayerEntity) continue;
                entity.refreshPositionAndAngles(es.x, es.y, es.z, es.yaw, es.pitch);
                entity.setVelocity(es.vx, es.vy, es.vz);
                if (entity instanceof LivingEntity le && es.health >= 0) le.setHealth(Math.min(es.health, le.getMaxHealth()));
            } catch (RuntimeException ignored) { }
        }
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
            for (Map.Entry<String, String> e : snapshot.blocks.entrySet()) {
                restoreBlock(client.world, e.getKey(), e.getValue());
            }
            if (changed != null) {
                for (Map.Entry<String, String> e : changed.entrySet()) restoreBlock(client.world, e.getKey(), e.getValue());
            }
            client.player.refreshPositionAndAngles(snapshot.playerX, snapshot.playerY, snapshot.playerZ,
                    snapshot.playerYaw, snapshot.playerPitch);
            restoreEntities(client, snapshot.entities);
        } finally {
            restoring = false;
        }
    }

    private static void restoreBlock(World world, String packed, String encoded) {
        try {
            BlockPos pos = BlockPos.fromLong(Long.parseLong(packed));
            BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(encoded))
                    .result().orElse(null);
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
        public List<EntityState> entities = new ArrayList<>();
    }
    public static final class EntityState {
        public String uuid, type;
        public double x, y, z, vx, vy, vz;
        public float yaw, pitch, health;
    }
}
