package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClientsidetestingClient implements ClientModInitializer {
    public static boolean showChunkPlayers = true;
    public static boolean showPlayerBoxes = true;
    public static int chunkCheckRadius = 1; // Customizable chunk radius
    private static KeyBinding openGuiKeybind;
    private final List<String> playersInChunk = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ChunkPlayersHudFeature.register();
        PlayerBoxRenderFeature.register();
        BlockSearchFeature.register();
        openGuiKeybind = new KeyBinding(
                "key.clientsidetesting.open_gui",
                GLFW.GLFW_KEY_G,
                "category.clientsidetesting"
        );
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(openGuiKeybind);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKeybind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClientsideTestingConfigScreen());
                }
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(this::onWorldRender);
        HudRenderCallback.EVENT.register((context, tickDelta) -> onHudRender(context, tickDelta));
    }

    private void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate vertexConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        playersInChunk.clear();
        int localChunkX = client.player.getChunkPos().x;
        int localChunkZ = client.player.getChunkPos().z;

        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue; // Skip local player
            int chunkX = player.getChunkPos().x;
            int chunkZ = player.getChunkPos().z;
            if (chunkX == localChunkX && chunkZ == localChunkZ) {
                playersInChunk.add(player.getName().getString());
            }
            if (showPlayerBoxes) {
                drawPlayerBox(player, matrices, cameraPos, vertexConsumers);
            }
        }
        vertexConsumers.draw(); // Flush after drawing all boxes
    }

    private void drawPlayerBox(PlayerEntity player, MatrixStack matrices, Vec3d cameraPos, VertexConsumerProvider vertexConsumers) {
        Vec3d pos = player.getPos();
        double x = pos.x - cameraPos.x;
        double y = pos.y - cameraPos.y;
        double z = pos.z - cameraPos.z;

        double width = 0.5;
        double depth = 0.5;
        double height = 1.8; // Make the box taller
        Box box = new Box(
            x - width / 2, y, z - depth / 2,
            x + width / 2, y + height, z + depth / 2
        );

        drawBox(matrices, box, 1.0f, 0.0f, 0.0f, 0.7f, vertexConsumers); // Red, 70% transparent
    }

    private void drawBox(MatrixStack matrices, Box box, float r, float g, float b, float a, VertexConsumerProvider vertexConsumers) {
        RenderSystem.lineWidth(4.0F); // Make lines thicker
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        drawBoxLines(box, entry, consumer, r, g, b, a);
        RenderSystem.lineWidth(1.0F); // Reset line width
    }

    private void drawBoxLines(Box box, MatrixStack.Entry entry, VertexConsumer consumer, float r, float g, float b, float a) {
        // 12 edges of the box
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, r, g, b, a);

        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);

        drawLine(entry, consumer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, r, g, b, a);

        drawLine(entry, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);

        drawLine(entry, consumer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }

    private void drawLine(MatrixStack.Entry entry, VertexConsumer consumer,
                          double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          float r, float g, float b, float a) {
        consumer.vertex(entry.getPositionMatrix(), (float)x1, (float)y1, (float)z1)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);
        consumer.vertex(entry.getPositionMatrix(), (float)x2, (float)y2, (float)z2)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);
    }

    // HUD overlay for players in the same chunk
    private void onHudRender(DrawContext context, RenderTickCounter tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (showChunkPlayers) {
            int x = 5;
            int y = 5;
            context.drawText(client.textRenderer, Text.literal("Players in your chunk:"), x, y, 0xFFFFFF, true);
            int offset = 12;
            for (String name : playersInChunk) {
                context.drawText(client.textRenderer, Text.literal(name), x, y + offset, 0xFFAAAA, true);
                offset += 12;
            }
        }
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            Vec3d playerPos = player.getPos().add(0, player.getStandingEyeHeight(), 0);
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            double dx = playerPos.x - cameraPos.x;
            double dy = playerPos.y - cameraPos.y;
            double dz = playerPos.z - cameraPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.01) continue;
        }
    }
}