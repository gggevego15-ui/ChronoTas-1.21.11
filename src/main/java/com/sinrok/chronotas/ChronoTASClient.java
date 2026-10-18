package com.sinrok.chronotas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.option.Perspective;
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
    public static double playbackSpeed = 1.0, speedAccumulator;
    public static float gameTickRate = 20.0f;
    public static boolean cinematicCamera = true;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;
    public static String recordingName = "run";
    public static String requestedRecordingName = "My TAS";
    public static String selectedReplay = "";
    public static final List<Frame> frames = new ArrayList<>();
    public static ChronoTASWorldState.Snapshot initialState;
    public static Map<String, String> recordedBlockChanges = new LinkedHashMap<>();
    private static boolean shiftWasDown, f4WasDown, f6WasDown, f7WasDown, f8WasDown, f9WasDown, f10WasDown, f12WasDown;
    private static boolean pausedShiftWasDown, pausedF4WasDown, pausedF7WasDown, pausedF10WasDown, pausedF12WasDown;
    private static int frameToCorrect = -1;
    public static final Map<String, String> currentTickBlockChanges = new LinkedHashMap<>();

    @Override public void onInitializeClient() {
        try { Files.createDirectories(RECORDINGS); Files.createDirectories(STATES); } catch (IOException e) { e.printStackTrace(); }
        ClientTickEvents.START_CLIENT_TICK.register(ChronoTASClient::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(ChronoTASClient::endTick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null || c.world == null) {
                ChronoTASVideo.captureFrame(c);
                return;
            }
            int x = 8, y = 8;
            String timer = playing ? formatTime(playbackIndex) + " / " + formatTime(frames.size())
                    : recording ? formatTime(frames.size()) : "00:00.00";
            String status = playing ? "REPLAY  " + String.format(java.util.Locale.ROOT, "%.2fx", playbackSpeed)
                    : recording ? "● RECORDING" : "CHRONOTAS";
            context.fill(x - 4, y - 4, x + 204, y + 152, 0xC910121B);
            context.fill(x - 4, y - 4, x + 204, y + 15, playing ? 0xEE1F6546 : recording ? 0xEE7A3038 : 0xEE263449);
            context.drawText(c.textRenderer, Text.literal(status), x, y, playing ? 0xFF8DFFB0 : recording ? 0xFFFF7777 : 0xFFFFFFFF, true);
            context.drawText(c.textRenderer, Text.literal(timer), x, y + 20, 0xFFFFFFFF, true);
            context.drawText(c.textRenderer, Text.literal(playing ? "ELAPSED / TOTAL" : recording ? "RECORDING TIME" : "TAS TIMER"), x, y + 34, 0xFF9CCBFF, false);
            context.drawText(c.textRenderer, Text.literal("MOD HOTKEYS"), x, y + 49, 0xFF9CCBFF, false);
            context.drawText(c.textRenderer, Text.literal("F4   MP4 video"), x, y + 63, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F6   Record / save TAS"), x, y + 75, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F7   Play / stop replay"), x, y + 87, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F8   Pause / resume"), x, y + 99, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F9   Step one tick"), x, y + 111, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F10  Save state"), x, y + 123, 0xFFFFFFFF, false);
            context.drawText(c.textRenderer, Text.literal("F12  Restore state"), x, y + 135, 0xFFFFFFFF, false);
            ChronoTASVideo.captureFrame(c);
        });
        System.out.println("[ChronoTAS] Right Shift menu | F4 video | F6 record | F7 playback | F8 pause | F9 step | F10 save | F12 restore");
    }

    private static void drawKey(net.minecraft.client.gui.DrawContext ctx, MinecraftClient c, String label, boolean down, int x, int y) {
        int w = label.length() > 3 ? 38 : 18;
        ctx.fill(x, y, x + w, y + 15, down ? 0xFF2D9E65 : 0xCC333744);
        ctx.drawCenteredTextWithShadow(c.textRenderer, label, x + w / 2, y + 3, 0xFFFFFFFF);
    }
    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%02d", seconds / 60, seconds % 60, (Math.max(0, ticks) % 20) * 5);
    }

    public static boolean shouldSkipSlowPlaybackTick() {
        if (!playing || playbackSpeed >= 1.0) return false;
        speedAccumulator += Math.max(0.05, playbackSpeed);
        if (speedAccumulator >= 1.0) { speedAccumulator -= 1.0; return false; }
        return true;
    }

    public static void handlePausedHotkeys(MinecraftClient c, boolean shift, boolean f4, boolean f7, boolean f10, boolean f12) {
        if (!paused) return;
        if (shift && !pausedShiftWasDown && c.currentScreen == null) c.setScreen(new ChronoTASScreen());
        if (f4 && !pausedF4WasDown) ChronoTASVideo.toggle(c);
        if (f7 && !pausedF7WasDown) togglePlayback(c);
        if (f10 && !pausedF10WasDown) saveQuickState(c);
        if (f12 && !pausedF12WasDown) restoreQuickState(c);
        pausedShiftWasDown = shift; pausedF4WasDown = f4; pausedF7WasDown = f7;
        pausedF10WasDown = f10; pausedF12WasDown = f12;
    }

    private static boolean key(MinecraftClient c, int code) { return InputUtil.isKeyPressed(c.getWindow(), code); }

    private static void startTick(MinecraftClient c) {
        boolean shift = key(c, GLFW.GLFW_KEY_RIGHT_SHIFT), f4 = key(c, GLFW.GLFW_KEY_F4);
        boolean f6 = key(c, GLFW.GLFW_KEY_F6), f7 = key(c, GLFW.GLFW_KEY_F7);
        boolean f8 = key(c, GLFW.GLFW_KEY_F8), f9 = key(c, GLFW.GLFW_KEY_F9);
        boolean f10 = key(c, GLFW.GLFW_KEY_F10), f12 = key(c, GLFW.GLFW_KEY_F12);
        if (shift && !shiftWasDown && c.currentScreen == null) { if (playing) paused = true; c.setScreen(new ChronoTASScreen()); }
        if (f4 && !f4WasDown) ChronoTASVideo.toggle(c);
        if (f6 && !f6WasDown) toggleRecording(c);
        if (f7 && !f7WasDown) togglePlayback(c);
        if (f8 && !f8WasDown) { paused = !paused; stepTicks = 0; if (paused) release(c); }
        if (f9 && !f9WasDown && paused && playing) stepTicks++;
        if (f10 && !f10WasDown) saveQuickState(c);
        if (f12 && !f12WasDown) restoreQuickState(c);
        shiftWasDown = shift; f4WasDown = f4; f6WasDown = f6; f7WasDown = f7;
        f8WasDown = f8; f9WasDown = f9; f10WasDown = f10; f12WasDown = f12;
        if (playing && c.player != null) {
            if (playbackIndex >= frames.size()) stopPlayback(c, "Playback finished.");
            else if (!paused) {
                frameToCorrect = playbackIndex;
                apply(c, frames.get(playbackIndex));
            } else if (stepTicks > 0) {
                frameToCorrect = playbackIndex;
                apply(c, frames.get(playbackIndex));
                playbackIndex++;
                stepTicks--;
            } else release(c);
        }
    }

    private static void endTick(MinecraftClient c) {
        if (recording && c.player != null) frames.add(capture(c));
        // Correct accumulated movement drift using the recorded post-tick transform.
        if (playing && frameToCorrect >= 0 && frameToCorrect < frames.size()) {
            correctRecordedState(c, frames.get(frameToCorrect));
            frameToCorrect = -1;
        }
        if (playing && !paused && !frames.isEmpty()) playbackIndex++;
    }

    private static void correctRecordedState(MinecraftClient c, Frame f) {
        if (c.player == null) return;
        c.player.setPosition(f.x, f.y, f.z);
        c.player.setVelocity(f.playerVx, f.playerVy, f.playerVz);
        c.player.setYaw(f.yaw);
        c.player.setPitch(f.pitch);
        ChronoTASWorldState.restoreEntities(c, f.entities);
        ChronoTASWorldState.restoreFrameBlocks(c, f.blockChanges);
        ChronoTASWorldState.restoreInventory(c, f.inventory, f.selectedSlot);
    }

    public static void toggleRecording(MinecraftClient c) {
        if (recording) {
            recording = false; save(); say(c, "Saved " + recordingName + " (" + frames.size() + " ticks).");
            return;
        }
        if (playing) stopPlayback(c, "Playback stopped.");
        frames.clear(); playbackIndex = 0; recordedBlockChanges.clear(); currentTickBlockChanges.clear();
        initialState = ChronoTASWorldState.capture(c);
        recordingName = uniqueRecordingName(requestedRecordingName);
        requestedRecordingName = recordingName;
        recording = true;
        say(c, "Recording started with world snapshot. F6 again saves inputs + block changes.");
    }

    public static void togglePlayback(MinecraftClient c) {
        if (playing) { stopPlayback(c, "Playback stopped."); return; }
        if (recording) { recording = false; save(); }
        List<String> names = list();
        if (names.isEmpty()) { say(c, "No recordings. Press F6 first."); return; }
        String target = selectedReplay != null && names.contains(selectedReplay) ? selectedReplay : names.get(names.size() - 1);
        if (!load(target)) { say(c, "Could not load recording. File may be damaged."); return; }
        ChronoTASWorldState.restore(c, initialState, recordedBlockChanges);
        playbackIndex = 0; playing = true; paused = false; speedAccumulator = 0;
        if (cinematicCamera) { previousPerspective = c.options.getPerspective(); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); }
        say(c, "Restored initial snapshot; playing " + recordingName + ". F7 stops.");
    }

    public static void cycleSpeed(MinecraftClient c) {
        double[] speeds = {1.0, 0.5, 0.25, 0.1};
        int next = 0;
        for (int i = 0; i < speeds.length; i++) if (Math.abs(playbackSpeed - speeds[i]) < 0.001) next = (i + 1) % speeds.length;
        playbackSpeed = speeds[next]; speedAccumulator = 0;
        say(c, "Replay speed: " + playbackSpeed + "x");
    }

    public static void stopPlayback(MinecraftClient c, String text) {
        playing = false; frameToCorrect = -1; release(c);
        if (cinematicCamera && c != null) c.options.setPerspective(previousPerspective);
        say(c, text);
    }

    public static void cycleGameSpeed(MinecraftClient c) {
        float[] rates = {20.0f, 10.0f, 5.0f, 2.0f, 1.0f};
        int next = 0;
        for (int i = 0; i < rates.length; i++) if (Math.abs(gameTickRate - rates[i]) < 0.01f) next = (i + 1) % rates.length;
        gameTickRate = rates[next];
        if (c != null && c.getServer() != null) c.getServer().getTickManager().setTickRate(gameTickRate);
        say(c, "Game speed: " + gameTickRate + " TPS" + (c != null && c.getServer() == null ? " (open singleplayer world to apply)" : ""));
    }

    public static void toggleCinematicCamera(MinecraftClient c) {
        cinematicCamera = !cinematicCamera;
        if (playing && c != null) {
            if (cinematicCamera) { previousPerspective = c.options.getPerspective(); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); }
            else c.options.setPerspective(previousPerspective);
        }
        say(c, "Replay camera: " + (cinematicCamera ? "cinematic third-person" : "normal perspective"));
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
        set(c.options.inventoryKey, f.inventoryKeyPressed);
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
        f.inventoryKeyPressed = c.options.inventoryKey.isPressed();
        for (int i = 0; i < Math.min(c.options.hotbarKeys.length, f.hotbar.length); i++) f.hotbar[i] = c.options.hotbarKeys[i].isPressed();
        f.yaw = c.player.getYaw(); f.pitch = c.player.getPitch();
        f.x = c.player.getX(); f.y = c.player.getY(); f.z = c.player.getZ();
        f.playerVx = c.player.getVelocity().x;
        f.playerVy = c.player.getVelocity().y;
        f.playerVz = c.player.getVelocity().z;
        f.inventory = ChronoTASWorldState.captureInventory(c);
        f.selectedSlot = c.player.getInventory().getSelectedSlot();
        f.blockChanges = new LinkedHashMap<>(currentTickBlockChanges);
        currentTickBlockChanges.clear();
        // Store only entities present in this frame. Carrying every entity ever seen
        // forward made each frame grow indefinitely and could freeze/crash the client.
        f.entities = ChronoTASWorldState.captureEntities(c, 32);
        return f;
    }

    private static void release(MinecraftClient c) {
        set(c.options.forwardKey, false); set(c.options.backKey, false); set(c.options.leftKey, false);
        set(c.options.rightKey, false); set(c.options.jumpKey, false); set(c.options.sprintKey, false); set(c.options.sneakKey, false);
        set(c.options.attackKey, false); set(c.options.useKey, false); set(c.options.pickItemKey, false);
        set(c.options.dropKey, false); set(c.options.inventoryKey, false);
        for (KeyBinding k : c.options.hotbarKeys) set(k, false);
    }
    private static String safeName(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("[^a-zA-Z0-9._ -]", "_")
                .replaceAll("[. ]+$", "").replaceAll("\\s+", "_");
        if (value.length() > 48) value = value.substring(0, 48);
        return value.isBlank() ? "My_TAS" : value;
    }
    private static String uniqueRecordingName(String raw) {
        String base = safeName(raw), candidate = base;
        int suffix = 2;
        while (Files.exists(RECORDINGS.resolve(candidate + ".json"))) candidate = base + "-" + suffix++;
        return candidate;
    }
    public static boolean renameRecording(String oldFile, String newName) {
        try {
            String clean = safeName(newName);
            Path oldPath = RECORDINGS.resolve(oldFile).normalize();
            if (!oldPath.getParent().equals(RECORDINGS.normalize()) || !oldPath.getFileName().toString().endsWith(".json")
                    || !Files.isRegularFile(oldPath)) return false;
            Path newPath = RECORDINGS.resolve(clean + ".json").normalize();
            if (!newPath.equals(oldPath) && Files.exists(newPath)) return false;
            Recording r = GSON.fromJson(Files.readString(oldPath, StandardCharsets.UTF_8), Recording.class);
            if (r == null || r.frames == null) return false;
            r.name = clean;
            Files.writeString(newPath, GSON.toJson(r), StandardCharsets.UTF_8);
            if (!newPath.equals(oldPath)) Files.delete(oldPath);
            if (oldFile.equals(selectedReplay)) selectedReplay = newPath.getFileName().toString();
            if (oldFile.equals(recordingName + ".json")) recordingName = clean;
            requestedRecordingName = clean;
            return true;
        } catch (Exception e) { return false; }
    }
    private static void save() {
        try {
            Files.createDirectories(RECORDINGS);
            Recording r = new Recording(); r.name = recordingName; r.minecraftVersion = "1.21.11";
            r.frames = new ArrayList<>(frames); r.initialState = initialState;
            r.changedBlocks = new LinkedHashMap<>(recordedBlockChanges);
            Path destination = RECORDINGS.resolve(safeName(recordingName) + ".json");
            Files.writeString(destination,
                    GSON.toJson(r), StandardCharsets.UTF_8);
            selectedReplay = destination.getFileName().toString();
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
    public static void sayPublic(MinecraftClient c, String s) { say(c, s); }
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
        public boolean attack, use, pickItem, drop, inventoryKeyPressed;
        public boolean[] hotbar = new boolean[9];
        public List<ChronoTASWorldState.EntityState> entities = new ArrayList<>();
        public float yaw, pitch; public double x, y, z;
        public double playerVx, playerVy, playerVz;
        public List<String> inventory = new ArrayList<>();
        public int selectedSlot;
        public Map<String, String> blockChanges = new LinkedHashMap<>();
    }
}
