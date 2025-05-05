package org.bookwormpi.clientsidetesting.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    public static ChunkPos lastPlayerChunk = null;
    private static MinecraftClient lastClient = null;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlockSearchFeature::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        ChunkPos currentChunk = client.player.getChunkPos();
        if (lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk) || client != lastClient) {
            rescanBlocks(client, currentChunk);
            lastPlayerChunk = currentChunk;
            lastClient = client;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        for (BlockPos pos : foundBlocks) {
            float offset = 0.05f;
            double x1 = pos.getX() - cam.x - offset;
            double y1 = pos.getY() - cam.y - offset;
            double z1 = pos.getZ() - cam.z - offset;
            double x2 = x1 + 1 + (offset * 2);
            double y2 = y1 + 1 + (offset * 2);
            double z2 = z1 + 1 + (offset * 2);

            // Draw edges
            drawLine(matrices.peek(), lines, x1, y1, z1, x2, y1, z1);
            drawLine(matrices.peek(), lines, x2, y1, z1, x2, y1, z2);
            drawLine(matrices.peek(), lines, x2, y1, z2, x1, y1, z2);
            drawLine(matrices.peek(), lines, x1, y1, z2, x1, y1, z1);

            drawLine(matrices.peek(), lines, x1, y2, z1, x2, y2, z1);
            drawLine(matrices.peek(), lines, x2, y2, z1, x2, y2, z2);
            drawLine(matrices.peek(), lines, x2, y2, z2, x1, y2, z2);
            drawLine(matrices.peek(), lines, x1, y2, z2, x1, y2, z1);

            drawLine(matrices.peek(), lines, x1, y1, z1, x1, y2, z1);
            drawLine(matrices.peek(), lines, x2, y1, z1, x2, y2, z1);
            drawLine(matrices.peek(), lines, x2, y1, z2, x2, y2, z2);
            drawLine(matrices.peek(), lines, x1, y1, z2, x1, y2, z2);
        }

        immediate.draw();
    }

    private static void drawLine(MatrixStack.Entry matrices, VertexConsumer lines, 
                               double x1, double y1, double z1, 
                               double x2, double y2, double z2) {
        lines.vertex(matrices.getPositionMatrix(), (float)x1, (float)y1, (float)z1)
            .color(1.0f, 0.0f, 0.0f, 1.0f)
            .normal(1.0f, 0.0f, 0.0f);
        lines.vertex(matrices.getPositionMatrix(), (float)x2, (float)y2, (float)z2)
            .color(1.0f, 0.0f, 0.0f, 1.0f)
            .normal(1.0f, 0.0f, 0.0f);
    }

    public static void rescanBlocks(MinecraftClient client, ChunkPos playerChunk) {
        if (!enabled || client.world == null) return;
        foundBlocks.clear();
        int renderDistance = client.options.getViewDistance().getValue();
        
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
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