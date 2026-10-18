package com.sinrok.chronotas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChronoTASClient implements ClientModInitializer {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path RECORDINGS = Path.of("config", "chronotas", "recordings");
    public static final Path STATES = Path.of("config", "chronotas", "states");
    public static boolean paused, recording, playing;
    public static int stepTicks, playbackIndex;
    public static String recordingName = "run";
    public static final List<Frame> frames = new ArrayList<>();
    public static ChronoTASWorldState.Snapshot initialState;
    public static Map<String, String> recordedBlockChanges = new LinkedHashMap<>();
    private static boolean shiftWasDown, f6WasDown, f7WasDown, f10WasDown, f11WasDown;
    private static boolean pausedShiftWasDown, pausedF7WasDown, pausedF10WasDown, pausedF11WasDown;

    public static void handlePausedHotkeys(MinecraftClient c, boolean shift, boolean f7, boolean f10, boolean f11) {
        if (!paused) return;
        if (shift && !pausedShiftWasDown && c.currentScreen == null) c.setScreen(new ChronoTASScreen());
        if (f7 && !pausedF7WasDown) togglePlayback(c);
        if (f10 && !pausedF10WasDown) saveQuickState(c);
        if (f11 && !pausedF11WasDown) restoreQuickState(c);
        pausedShiftWasDown = shift; pausedF7WasDown = f7;
        pausedF10WasDown = f10; pausedF11WasDown = f11;
    }

    @Override public void onInitializeClient() {
        try { Files.createDirectories(RECORDINGS); Files.createDirectories(STATES); } catch (IOException e) { e.printStackTrace(); }
        ClientTickEvents.START_CLIENT_TICK.register(ChronoTASClient::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(ChronoTASClient::endTick);
        System.out.println("[ChronoTAS] Right Shift menu | F6 record | F7 playback | F8 pause | F9 step | F10 save state | F11 restore state");
    }

    private static boolean key(MinecraftClient c, int code) { return InputUtil.isKeyPressed(c.getWindow(), code); }

    private static void startTick(MinecraftClient c) {
        boolean shift = key(c, GLFW.GLFW_KEY_RIGHT_SHIFT), f6 = key(c, GLFW.GLFW_KEY_F6), f7 = key(c, GLFW.GLFW_KEY_F7);
        boolean f10 = key(c, GLFW.GLFW_KEY_F10), f11 = key(c, GLFW.GLFW_KEY_F11);
        if (shift && !shiftWasDown && c.currentScreen == null) c.setScreen(new ChronoTASScreen());
        if (f6 && !f6WasDown) toggleRecording(c);
        if (f7 && !f7WasDown) togglePlayback(c);
        if (f10 && !f10WasDown) saveQuickState(c);
        if (f11 && !f11WasDown) restoreQuickState(c);
        shiftWasDown = shift; f6WasDown = f6; f7WasDown = f7; f10WasDown = f10; f11WasDown = f11;
        if (playing && c.player != null) {
            if (playbackIndex >= frames.size()) stopPlayback(c, "Playback finished.");
            else apply(c, frames.get(playbackIndex));
        }
    }

    private static void endTick(MinecraftClient c) {
        if (recording && c.player != null) frames.add(capture(c));
        if (playing && !frames.isEmpty()) playbackIndex++;
    }

    public static void toggleRecording(MinecraftClient c) {
        if (recording) {
            recording = false; save(); say(c, "Saved " + recordingName + " (" + frames.size() + " ticks).");
            return;
        }
        if (playing) stopPlayback(c, "Playback stopped.");
        frames.clear(); playbackIndex = 0; recordedBlockChanges.clear();
        initialState = ChronoTASWorldState.capture(c);
        recordingName = "run-" + System.currentTimeMillis(); recording = true;
        say(c, "Recording started with world snapshot. F6 again saves inputs + block changes.");
    }

    public static void togglePlayback(MinecraftClient c) {
        if (playing) { stopPlayback(c, "Playback stopped."); return; }
        if (recording) { recording = false; save(); }
        List<String> names = list();
        if (names.isEmpty()) { say(c, "No recordings. Press F6 first."); return; }
        if (!load(names.get(names.size() - 1))) { say(c, "Could not load recording."); return; }
        ChronoTASWorldState.restore(c, initialState, recordedBlockChanges);
        playbackIndex = 0; playing = true; paused = false;
        say(c, "Restored initial snapshot; playing " + recordingName + ". F7 stops.");
    }

    public static void stopPlayback(MinecraftClient c, String text) {
        playing = false; release(c); say(c, text);
    }

    public static void saveQuickState(MinecraftClient c) {
        ChronoTASWorldState.Snapshot snapshot = ChronoTASWorldState.capture(c);
        if (snapshot == null) { say(c, "Load a world before saving a state."); return; }
        try {
            Files.createDirectories(STATES);
            Files.writeString(STATES.resolve("quick-state.json"), GSON.toJson(snapshot), StandardCharsets.UTF_8);
            say(c, "Quick state saved (nearby blocks + entity/player positions).");
        } catch (IOException e) { say(c, "Could not save state: " + e.getMessage()); }
    }

    public static void restoreQuickState(MinecraftClient c) {
        Path file = STATES.resolve("quick-state.json");
        try {
            if (!Files.isRegularFile(file)) { say(c, "No quick state. Press F10 first."); return; }
            var snapshot = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ChronoTASWorldState.Snapshot.class);
            ChronoTASWorldState.restore(c, snapshot, Map.of());
            say(c, "Quick state restored.");
        } catch (Exception e) { say(c, "Could not restore state: " + e.getMessage()); }
    }

    private static void set(KeyBinding k, boolean down) { k.setPressed(down); }
    private static void apply(MinecraftClient c, Frame f) {
        if (c.player == null) return;
        set(c.options.forwardKey, f.forward); set(c.options.backKey, f.back);
        set(c.options.leftKey, f.left); set(c.options.rightKey, f.right);
        set(c.options.jumpKey, f.jump); set(c.options.sprintKey, f.sprint); set(c.options.sneakKey, f.sneak);
        set(c.options.attackKey, f.attack); set(c.options.useKey, f.use);
        set(c.options.pickItemKey, f.pickItem); set(c.options.dropKey, f.drop);
        set(c.options.inventoryKey, f.inventory);
        for (int i = 0; i < Math.min(c.options.hotbarKeys.length, f.hotbar.length); i++) set(c.options.hotbarKeys[i], f.hotbar[i]);
        ChronoTASWorldState.restoreEntities(c, f.entities);
        c.player.setYaw(f.yaw); c.player.setPitch(f.pitch);
    }
    private static Frame capture(MinecraftClient c) {
        Frame f = new Frame();
        f.forward = c.options.forwardKey.isPressed(); f.back = c.options.backKey.isPressed();
        f.left = c.options.leftKey.isPressed(); f.right = c.options.rightKey.isPressed();
        f.jump = c.options.jumpKey.isPressed(); f.sprint = c.options.sprintKey.isPressed();
        f.sneak = c.options.sneakKey.isPressed();
        f.attack = c.options.attackKey.isPressed(); f.use = c.options.useKey.isPressed();
        f.pickItem = c.options.pickItemKey.isPressed(); f.drop = c.options.dropKey.isPressed();
        f.inventory = c.options.inventoryKey.isPressed();
        for (int i = 0; i < Math.min(c.options.hotbarKeys.length, f.hotbar.length); i++) f.hotbar[i] = c.options.hotbarKeys[i].isPressed();
        f.yaw = c.player.getYaw(); f.pitch = c.player.getPitch();
        f.x = c.player.getX(); f.y = c.player.getY(); f.z = c.player.getZ();
        f.entities = ChronoTASWorldState.captureEntities(c, 64); return f;
    }
    private static void release(MinecraftClient c) {
        set(c.options.forwardKey, false); set(c.options.backKey, false); set(c.options.leftKey, false);
        set(c.options.rightKey, false); set(c.options.jumpKey, false); set(c.options.sprintKey, false); set(c.options.sneakKey, false);
        set(c.options.attackKey, false); set(c.options.useKey, false); set(c.options.pickItemKey, false);
        set(c.options.dropKey, false); set(c.options.inventoryKey, false);
        for (KeyBinding k : c.options.hotbarKeys) set(k, false);
    }
    private static void save() {
        try {
            Files.createDirectories(RECORDINGS);
            Recording r = new Recording(); r.name = recordingName; r.minecraftVersion = "1.21.11";
            r.frames = new ArrayList<>(frames); r.initialState = initialState;
            r.changedBlocks = new LinkedHashMap<>(recordedBlockChanges);
            Files.writeString(RECORDINGS.resolve(recordingName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json"),
                    GSON.toJson(r), StandardCharsets.UTF_8);
        } catch (IOException e) { e.printStackTrace(); }
    }
    public static List<String> list() {
        try (var p = Files.list(RECORDINGS)) {
            return p.filter(x -> x.getFileName().toString().endsWith(".json")).map(x -> x.getFileName().toString()).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }
    public static boolean load(String name) {
        try {
            Path file = RECORDINGS.resolve(name).normalize();
            if (!file.getParent().equals(RECORDINGS.normalize()) || !Files.isRegularFile(file)) return false;
            Recording r = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Recording.class);
            if (r == null || r.frames == null || r.frames.isEmpty()) return false;
            frames.clear(); frames.addAll(r.frames); recordingName = r.name == null ? name : r.name;
            initialState = r.initialState; recordedBlockChanges = r.changedBlocks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(r.changedBlocks);
            playbackIndex = 0; return true;
        } catch (Exception e) { return false; }
    }
    private static void say(MinecraftClient c, String s) {
        if (c.player != null) c.player.sendMessage(Text.literal("[ChronoTAS] " + s), false);
        else System.out.println("[ChronoTAS] " + s);
    }
    public static final class Recording {
        public String name, minecraftVersion;
        public List<Frame> frames = new ArrayList<>();
        public ChronoTASWorldState.Snapshot initialState;
        public Map<String, String> changedBlocks = new LinkedHashMap<>();
    }
    public static final class Frame {
        public boolean forward, back, left, right, jump, sprint, sneak;
        public boolean attack, use, pickItem, drop, inventory;
        public boolean[] hotbar = new boolean[9];
        public List<ChronoTASWorldState.EntityState> entities = new ArrayList<>();
        public float yaw, pitch; public double x, y, z;
    }
}
