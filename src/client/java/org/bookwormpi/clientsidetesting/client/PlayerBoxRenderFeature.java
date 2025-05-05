package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;

public class PlayerBoxRenderFeature {
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PlayerBoxRenderFeature::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!ClientsidetestingClient.showPlayerBoxes) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate vertexConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            Vec3d pos = player.getPos();
            double x = pos.x - cameraPos.x;
            double y = pos.y - cameraPos.y;
            double z = pos.z - cameraPos.z;
            double width = 0.5;
            double depth = 0.5;
            double height = 1.8;
            Box box = new Box(
                    x - width / 2, y, z - depth / 2,
                    x + width / 2, y + height, z + depth / 2
            );
            drawBox(matrices, box, 1.0f, 0.0f, 0.0f, 0.7f, vertexConsumers);
        }
        vertexConsumers.draw();
    }

    private static void drawBox(MatrixStack matrices, Box box, float r, float g, float b, float a, VertexConsumerProvider vertexConsumers) {
        RenderSystem.lineWidth(4.0F);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        drawBoxLines(box, entry, consumer, r, g, b, a);
        RenderSystem.lineWidth(1.0F);
    }

    private static void drawBoxLines(Box box, MatrixStack.Entry entry, VertexConsumer consumer, float r, float g, float b, float a) {
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        drawLine(entry, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
    }

    private static void drawLine(MatrixStack.Entry entry, VertexConsumer consumer,
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
}