package com.sinrok.chronotas;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class ChronoTASScreen extends Screen {
    private int scroll;
    public ChronoTASScreen() { super(Text.literal("ChronoTAS")); }

    @Override protected void init() {
        int x = width / 2 - 105, y = height / 2 - 92;
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.recording ? "Stop & Save (F6)" : "Start Recording (F6)"),
                b -> ChronoTASClient.toggleRecording(client)).dimensions(x, y, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.playing ? "Stop Playback (F7)" : "Play Latest (F7)"),
                b -> ChronoTASClient.togglePlayback(client)).dimensions(x, y + 25, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(ChronoTASClient.paused ? "Resume (F8)" : "Pause (F8)"),
                b -> { ChronoTASClient.paused = !ChronoTASClient.paused; ChronoTASClient.stepTicks = 0; clearAndInit(); })
                .dimensions(x, y + 50, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Step one tick (F9)"),
                b -> ChronoTASClient.stepTicks++).dimensions(x, y + 75, 210, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh list"), b -> clearAndInit())
                .dimensions(x, y + 100, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x + 108, y + 100, 102, 20).build());

        List<String> files = ChronoTASClient.list();
        int rowY = y + 134, maxRows = Math.max(1, (height - rowY - 16) / 22);
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
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, "CHRONOTAS / TAS TOOLKIT", width / 2, 18, 0xFFFFFF);
        String status = ChronoTASClient.recording ? "RECORDING" : ChronoTASClient.playing ? "PLAYING" : "IDLE";
        context.drawCenteredTextWithShadow(textRenderer, "Status: " + status + " | recorded ticks: " + ChronoTASClient.frames.size(),
                width / 2, 34, 0xB9D7FF);
        context.drawCenteredTextWithShadow(textRenderer, "F6 Record | F7 Play | F8 Pause | F9 Step | Right Shift Menu",
                width / 2, 48, 0xDDDDDD);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical)); clearAndInit(); return true;
    }
    @Override public void close() { if (client != null) client.setScreen(null); }
}
