package com.sinrok.chronotas;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class ChronoTASScreen extends Screen {
    private int scroll;
    private TextFieldWidget nameField;
    public ChronoTASScreen() { super(Text.literal("ChronoTAS")); }

    @Override protected void init() {
        int x = width / 2 - 105, y = height / 2 - 105;
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.recording ? "Stop & Save (F6)" : "Start Recording (F6)"),
                b -> ChronoTASClient.toggleRecording(client)).dimensions(x, y, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.playing ? "Stop Playback (F7)" : "Play Selected / Latest (F7)"),
                b -> ChronoTASClient.togglePlayback(client)).dimensions(x, y + 24, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Replay speed: " + ChronoTASClient.playbackSpeed + "x (cycle)"),
                b -> { ChronoTASClient.cycleSpeed(client); clearAndInit(); }).dimensions(x, y + 48, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASVideo.isRecording() ? "Stop video (F4)" : "Record MP4 video (F4)"),
                b -> { ChronoTASVideo.toggle(client); clearAndInit(); }).dimensions(x, y + 72, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.paused ? "Resume (F8)" : "Pause (F8)"),
                b -> { ChronoTASClient.paused = !ChronoTASClient.paused; ChronoTASClient.stepTicks = 0; clearAndInit(); })
                .dimensions(x, y + 96, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Step (F9)"), b -> ChronoTASClient.stepTicks++)
                .dimensions(x + 108, y + 96, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh list"), b -> clearAndInit())
                .dimensions(x, y + 120, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x + 108, y + 120, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save State (F10)"), b -> ChronoTASClient.saveQuickState(client))
                .dimensions(x, y + 144, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Restore (F11)"), b -> ChronoTASClient.restoreQuickState(client))
                .dimensions(x + 108, y + 144, 102, 20).build());

        List<String> files = ChronoTASClient.list();
        int rowY = y + 178, maxRows = Math.max(1, (height - rowY - 16) / 22);
        int start = Math.min(scroll, Math.max(0, files.size() - maxRows));
        for (int i = start; i < Math.min(files.size(), start + maxRows); i++) {
            String file = files.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal("Load: " + file),
                    b -> { if (ChronoTASClient.load(file) && client.player != null)
                        client.player.sendMessage(Text.literal("[ChronoTAS] Loaded " + file), false); })
                    .dimensions(x, rowY, 210, 20).build());
            rowY += 22;
        }
    }

    protected void clearAndInit() { clearChildren(); init(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid expensive in-world blur; use a simple dark panel for responsive rendering on low-end GPUs.
        context.fill(0, 0, width, height, 0xE610121B);
        context.fill(width / 2 - 125, 10, width / 2 + 125, height - 10, 0xAA202633);
        context.drawCenteredTextWithShadow(textRenderer, "CHRONOTAS / TAS TOOLKIT", width / 2, 18, 0xFFFFFF);
        String status = ChronoTASClient.recording ? "RECORDING" : ChronoTASClient.playing ? "PLAYING" : "IDLE";
        context.drawCenteredTextWithShadow(textRenderer, "Status: " + status + " | ticks: " + ChronoTASClient.frames.size(),
                width / 2, 34, 0xFFB9D7FF);
        context.drawCenteredTextWithShadow(textRenderer, "F4 Video | F6 Record | F7 Replay | F8 Pause | F9 Step | F10 Save | F11 Restore",
                width / 2, 48, 0xFFDDDDDD);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical)); clearAndInit(); return true;
    }
    @Override public void close() { if (client != null) client.setScreen(null); }
}
