package org.bookwormpi.clientsidetesting.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;

public class BlockSearchFeature {
    public static boolean enabled = false;
    public static Block blockToSearch = Blocks.DIAMOND_BLOCK;
    public static int scanDistance = -1; // -1 means use render distance by default
    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    public static ChunkPos lastPlayerChunk = null;
    private static MinecraftClient lastClient = null;
    private static long lastScanTime = 0;
    private static boolean scanRequested = false;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlockSearchFeature::onWorldRender);
        // Listen for chunk load/unload and trigger rescan
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (enabled && MinecraftClient.getInstance().player != null) {
                rescanBlocks(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (enabled && MinecraftClient.getInstance().player != null) {
                rescanBlocks(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            }
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (enabled && !world.isClient) return;
            scanRequested = true;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (enabled && world.isClient) {
                scanRequested = true;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || blockToSearch == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        long now = client.world.getTime();
        if (scanRequested && now - lastScanTime >= 20) {
            rescanBlocks(client, client.player.getChunkPos());
            lastScanTime = now;
            scanRequested = false;
        }

        ChunkPos currentChunk = client.player.getChunkPos();
        if (lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk) || client != lastClient) {
            rescanBlocks(client, currentChunk);
            lastPlayerChunk = currentChunk;
            lastClient = client;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer quads = immediate.getBuffer(RenderLayer.getGuiOverlay());

        float r = 1.0F, g = 0.0F, b = 0.0F, a = 0.3F;
        double min = 0.375, max = 0.625;
        for (BlockPos pos : foundBlocks) {
            double x = pos.getX() - cam.x;
            double y = pos.getY() - cam.y;
            double z = pos.getZ() - cam.z;
            // Bottom face (y = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, max - min, 0, 0, 0, 0, max - min, r, g, b, a, 0, -1, 0);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, max - min, 0, 0, 0, 0, max - min, r, g, b, a, 0, 1, 0);
            // Top face (y = max)
            drawQuad(matrices, quads, x + min, y + max, z + min, max - min, 0, 0, 0, 0, max - min, r, g, b, a, 0, 1, 0);
            drawQuadReversed(matrices, quads, x + min, y + max, z + min, max - min, 0, 0, 0, 0, max - min, r, g, b, a, 0, -1, 0);
            // North face (z = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, max - min, 0, 0, 0, max - min, 0, r, g, b, a, 0, 0, -1);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, max - min, 0, 0, 0, max - min, 0, r, g, b, a, 0, 0, 1);
            // South face (z = max)
            drawQuad(matrices, quads, x + min, y + min, z + max, max - min, 0, 0, 0, max - min, 0, r, g, b, a, 0, 0, 1);
            drawQuadReversed(matrices, quads, x + min, y + min, z + max, max - min, 0, 0, 0, max - min, 0, r, g, b, a, 0, 0, -1);
            // West face (x = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, 0, max - min, 0, 0, 0, max - min, r, g, b, a, -1, 0, 0);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, 0, max - min, 0, 0, 0, max - min, r, g, b, a, 1, 0, 0);
            // East face (x = max)
            drawQuad(matrices, quads, x + max, y + min, z + min, 0, max - min, 0, 0, 0, max - min, r, g, b, a, 1, 0, 0);
            drawQuadReversed(matrices, quads, x + max, y + min, z + min, 0, max - min, 0, 0, 0, max - min, r, g, b, a, -1, 0, 0);
        }
        immediate.draw();
    }

    private static void drawQuad(MatrixStack matrices, VertexConsumer buffer,
                                double x, double y, double z,
                                double dx1, double dy1, double dz1,
                                double dx2, double dy2, double dz2,
                                float r, float g, float b, float a,
                                float nx, float ny, float nz) {
        MatrixStack.Entry entry = matrices.peek();
        buffer.vertex(entry.getPositionMatrix(), (float)x, (float)y, (float)z).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx1), (float)(y + dy1), (float)(z + dz1)).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx1 + dx2), (float)(y + dy1 + dy2), (float)(z + dz1 + dz2)).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx2), (float)(y + dy2), (float)(z + dz2)).color(r, g, b, a).normal(nx, ny, nz);
    }

    private static void drawQuadReversed(MatrixStack matrices, VertexConsumer buffer,
                                double x, double y, double z,
                                double dx1, double dy1, double dz1,
                                double dx2, double dy2, double dz2,
                                float r, float g, float b, float a,
                                float nx, float ny, float nz) {
        MatrixStack.Entry entry = matrices.peek();
        buffer.vertex(entry.getPositionMatrix(), (float)x, (float)y, (float)z).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx2), (float)(y + dy2), (float)(z + dz2)).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx1 + dx2), (float)(y + dy1 + dy2), (float)(z + dz1 + dz2)).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(entry.getPositionMatrix(), (float)(x + dx1), (float)(y + dy1), (float)(z + dz1)).color(r, g, b, a).normal(nx, ny, nz);
    }

    private static boolean isFaceVisible(Vec3d cam, double fx, double fy, double fz, double nx, double ny, double nz) {
        double dx = cam.x - fx;
        double dy = cam.y - fy;
        double dz = cam.z - fz;
        double dot = dx * nx + dy * ny + dz * nz;
        return dot < 0;
    }

    public static void rescanBlocks(MinecraftClient client, ChunkPos playerChunk) {
        if (!enabled || client.world == null) return;
        foundBlocks.clear();
        int distance = scanDistance > 0 ? scanDistance : (client.options != null ? client.options.getViewDistance().getValue() : 8);
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                if (!client.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) continue;
                
                var chunk = client.world.getChunk(chunkPos.x, chunkPos.z);
                var chunkSections = chunk.getSectionArray();
                int bottomY = chunk.getBottomY();
                
                for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                    var section = chunkSections[sectionY];
                    if (section == null || section.isEmpty()) continue;
                    
                    int yOffset = (sectionY * 16) + bottomY;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                if (section.getBlockState(x, y, z).isOf(blockToSearch)) {
                                    foundBlocks.add(new BlockPos(
                                        chunkPos.getStartX() + x,
                                        yOffset + y,
                                        chunkPos.getStartZ() + z
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}