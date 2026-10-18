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
    private int panelX, panelY, panelW, panelH, leftX, rightX, contentY, leftW, rightW;

    public ChronoTASScreen() { super(Text.literal("ChronoTAS")); }

    @Override protected void init() {
        clearChildren();
        panelW = Math.min(600, width - 24);
        panelH = Math.min(410, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        leftX = panelX + 16;
        leftW = Math.max(180, (panelW - 44) / 2);
        rightX = leftX + leftW + 12;
        rightW = panelX + panelW - 16 - rightX;
        contentY = panelY + 66;

        int gap = 6, bw = (leftW - gap) / 2, bh = 23, row = 29;
        addButton(ChronoTASClient.recording ? "■  Stop & save" : "●  Record TAS",
                b -> { if (nameField != null) ChronoTASClient.requestedRecordingName = nameField.getText(); ChronoTASClient.toggleRecording(client); clearAndInit(); },
                leftX, contentY, bw, bh, ChronoTASClient.recording ? 0xFF7A3542 : 0xFF23684B);
        addButton(ChronoTASClient.playing ? "■  Stop replay" : "▶  Play replay",
                b -> { ChronoTASClient.togglePlayback(client); clearAndInit(); },
                leftX + bw + gap, contentY, bw, bh, ChronoTASClient.playing ? 0xFF7A3542 : 0xFF285B91);
        addButton("Replay " + ChronoTASClient.playbackSpeed + "x",
                b -> { ChronoTASClient.cycleSpeed(client); clearAndInit(); },
                leftX, contentY + row, bw, bh, 0xFF303D57);
        addButton("Game " + ChronoTASClient.gameTickRate + " TPS",
                b -> { ChronoTASClient.cycleGameSpeed(client); clearAndInit(); },
                leftX + bw + gap, contentY + row, bw, bh, 0xFF303D57);
        addButton("Camera " + (ChronoTASClient.cinematicCamera ? "ON" : "OFF"),
                b -> { ChronoTASClient.toggleCinematicCamera(client); clearAndInit(); },
                leftX, contentY + row * 2, bw, bh, 0xFF303D57);
        addButton(ChronoTASVideo.isRecording() ? "Stop video" : "Record video",
                b -> { ChronoTASVideo.toggle(client); clearAndInit(); },
                leftX + bw + gap, contentY + row * 2, bw, bh, 0xFF303D57);
        addButton(ChronoTASClient.paused ? "▶  Resume" : "Ⅱ  Pause",
                b -> { ChronoTASClient.paused = !ChronoTASClient.paused; ChronoTASClient.stepTicks = 0; clearAndInit(); },
                leftX, contentY + row * 3, bw, bh, 0xFF303D57);
        addButton("Step 1 tick",
                b -> { if (ChronoTASClient.playing) ChronoTASClient.stepTicks++; },
                leftX + bw + gap, contentY + row * 3, bw, bh, 0xFF303D57);
        addButton("Save state  F10", b -> ChronoTASClient.saveQuickState(client),
                leftX, contentY + row * 4, bw, bh, 0xFF303D57);
        addButton("Restore  F12", b -> ChronoTASClient.restoreQuickState(client),
                leftX + bw + gap, contentY + row * 4, bw, bh, 0xFF303D57);

        nameField = new TextFieldWidget(textRenderer, leftX, contentY + row * 5, leftW, 22, Text.literal("Replay name"));
        nameField.setMaxLength(48);
        nameField.setText(ChronoTASClient.requestedRecordingName);
        nameField.setPlaceholder(Text.literal("Name this TAS recording"));
        nameField.setChangedListener(value -> ChronoTASClient.requestedRecordingName = value);
        addDrawableChild(nameField);
        addButton("Rename selected replay", b -> {
            if (ChronoTASClient.selectedReplay == null || ChronoTASClient.selectedReplay.isBlank()) {
                ChronoTASClient.sayPublic(client, "Select a replay first."); return;
            }
            if (ChronoTASClient.renameRecording(ChronoTASClient.selectedReplay, nameField.getText())) {
                ChronoTASClient.sayPublic(client, "Replay renamed."); clearAndInit();
            } else ChronoTASClient.sayPublic(client, "Rename failed: name exists or replay is damaged.");
        }, leftX, contentY + row * 6, leftW, bh, 0xFF285B91);
        addButton("Refresh list", b -> clearAndInit(),
                leftX, contentY + row * 7, bw, bh, 0xFF303D57);
        addButton("Done  Esc", b -> close(),
                leftX + bw + gap, contentY + row * 7, bw, bh, 0xFF303D57);

        List<String> files = ChronoTASClient.list();
        int listY = contentY + 24, listBottom = panelY + panelH - 16;
        int maxRows = Math.max(1, (listBottom - listY) / 25);
        int start = Math.min(scroll, Math.max(0, files.size() - maxRows));
        for (int i = start; i < Math.min(files.size(), start + maxRows); i++) {
            String file = files.get(i);
            boolean selected = file.equals(ChronoTASClient.selectedReplay);
            String label = (selected ? "◆  " : "   ") + file.replaceFirst("\\.json$", "");
            addButton(label, b -> {
                ChronoTASClient.selectedReplay = file;
                ChronoTASClient.requestedRecordingName = file.replaceFirst("\\.json$", "");
                if (nameField != null) nameField.setText(ChronoTASClient.requestedRecordingName);
                if (ChronoTASClient.load(file)) ChronoTASClient.sayPublic(client, "Selected " + ChronoTASClient.recordingName);
                clearAndInit();
            }, rightX, listY, rightW, 21, selected ? 0xFF285B91 : 0xFF242D3D);
            listY += 25;
        }
    }

    private void addButton(String label, net.minecraft.client.gui.widget.ButtonWidget.PressAction action,
                           int x, int y, int w, int h, int color) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, h).build());
    }

    private void clearAndInit() { clearChildren(); init(); }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xD9080D16);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF4B6388);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0141B29);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 3, 0xFF69B7FF);
        ctx.fill(panelX + 12, panelY + 55, panelX + panelW - 12, panelY + 56, 0xFF35465F);
        ctx.fill(leftX - 5, contentY - 8, leftX + leftW + 5, panelY + panelH - 10, 0x70222D40);
        ctx.fill(rightX - 5, contentY - 8, rightX + rightW + 5, panelY + panelH - 10, 0x70222D40);
        ctx.drawText(textRenderer, "CHRONO", panelX + 17, panelY + 13, 0xFF75C6FF, true);
        ctx.drawText(textRenderer, "TAS", panelX + 84, panelY + 13, 0xFFFFFFFF, true);
        String status = ChronoTASClient.recording ? "● RECORDING" : ChronoTASClient.playing ? "▶ REPLAY" : "READY";
        int statusColor = ChronoTASClient.recording ? 0xFFFF8585 : ChronoTASClient.playing ? 0xFF85F0B0 : 0xFFB6C7E0;
        ctx.drawText(textRenderer, status, panelX + 17, panelY + 34, statusColor, false);
        ctx.drawText(textRenderer, "TICKS  " + ChronoTASClient.frames.size(), panelX + 145, panelY + 34, 0xFFB6C7E0, false);
        ctx.drawText(textRenderer, "CONTROLS", leftX, contentY - 1, 0xFF9FCBFF, false);
        ctx.drawText(textRenderer, "SAVED REPLAYS", rightX, contentY - 1, 0xFF9FCBFF, false);
        if (ChronoTASClient.list().isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No recordings yet", rightX + rightW / 2, contentY + 40, 0xFF9BAAC0);
            ctx.drawCenteredTextWithShadow(textRenderer, "Press Record TAS to start", rightX + rightW / 2, contentY + 54, 0xFF6F819D);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        clearAndInit();
        return true;
    }
    @Override public void close() { if (client != null) client.setScreen(null); }
}
