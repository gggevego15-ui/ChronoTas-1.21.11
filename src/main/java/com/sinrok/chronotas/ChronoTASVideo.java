package com.sinrok.chronotas;

import net.minecraft.client.MinecraftClient;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.lwjgl.opengl.GL11;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class ChronoTASVideo {
    private static final BufferedImage STOP = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    private static volatile boolean recording;
    private static volatile Path output;
    private static BlockingQueue<BufferedImage> queue;
    private static Thread encoderThread;
    private static long lastFrameNanos;
    private static int droppedFrames;
    private static ByteBuffer pixelBuffer;
    private static int bufferWidth, bufferHeight;

    private ChronoTASVideo() {}

    public static boolean isRecording() { return recording; }
    public static Path outputPath() { return output; }
    public static int droppedFrames() { return droppedFrames; }

    public static void toggle(MinecraftClient client) {
        if (recording) stop(client);
        else start(client);
    }

    public static void start(MinecraftClient client) {
        if (recording) return;
        try {
            Path dir = Path.of("config", "chronotas", "videos");
            Files.createDirectories(dir);
            output = dir.resolve("chronotas-" + System.currentTimeMillis() + ".mp4");
            queue = new ArrayBlockingQueue<>(12);
            droppedFrames = 0;
            lastFrameNanos = 0;
            recording = true;
            Path target = output;
            BlockingQueue<BufferedImage> targetQueue = queue;
            encoderThread = new Thread(() -> encode(target, targetQueue), "ChronoTAS-video-encoder");
            encoderThread.setDaemon(true);
            encoderThread.start();
            ChronoTASClient.sayPublic(client, "Video capture started (MP4, 20 FPS). F4 stops.");
        } catch (IOException e) {
            recording = false;
            ChronoTASClient.sayPublic(client, "Video start failed: " + e.getMessage());
        }
    }

    public static void stop(MinecraftClient client) {
        if (!recording) return;
        recording = false;
        ChronoTASClient.sayPublic(client, "Saving video: " + output.getFileName() + " (encoding in background).");
    }

    public static void captureFrame(MinecraftClient client) {
        if (!recording || client == null || client.getWindow() == null) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0 && now - lastFrameNanos < 50_000_000L) return;
        lastFrameNanos = now;
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        if (width < 2 || height < 2) return;
        try {
            int bytes = width * height * 4;
            if (pixelBuffer == null || bufferWidth != width || bufferHeight != height) {
                pixelBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
                bufferWidth = width;
                bufferHeight = height;
            }
            pixelBuffer.clear();
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < height; y++) {
                int sourceRow = (height - 1 - y) * width * 4;
                int targetRow = y * width;
                for (int x = 0, i = sourceRow; x < width; x++, i += 4) {
                    int r = pixelBuffer.get(i) & 255;
                    int g = pixelBuffer.get(i + 1) & 255;
                    int b = pixelBuffer.get(i + 2) & 255;
                    rgb[targetRow + x] = (r << 16) | (g << 8) | b;
                }
            }
            if (!queue.offer(image)) droppedFrames++;
        } catch (Throwable e) {
            recording = false;
            System.err.println("[ChronoTAS] Video capture failed: " + e.getMessage());
        }
    }

    private static void encode(Path target, BlockingQueue<BufferedImage> frames) {
        AWTSequenceEncoder encoder = null;
        int count = 0, width = -1, height = -1;
        try {
            while (recording || !frames.isEmpty()) {
                BufferedImage image = frames.poll(200, TimeUnit.MILLISECONDS);
                if (image == null) continue;
                if (image == STOP) break;
                if (encoder == null) {
                    width = image.getWidth(); height = image.getHeight();
                    encoder = AWTSequenceEncoder.createSequenceEncoder(target.toFile(), 20);
                }
                if (image.getWidth() != width || image.getHeight() != height) {
                    BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = resized.createGraphics();
                    g.drawImage(image, 0, 0, width, height, null);
                    g.dispose();
                    image = resized;
                }
                encoder.encodeImage(image);
                count++;
            }
            if (encoder != null) encoder.finish();
            System.out.println("[ChronoTAS] Video saved: " + target + " (" + count + " frames)");
        } catch (Exception e) {
            System.err.println("[ChronoTAS] Video encode failed: " + e.getMessage());
            if (encoder != null) try { encoder.finish(); } catch (Exception ignored) {}
        }
    }
}
