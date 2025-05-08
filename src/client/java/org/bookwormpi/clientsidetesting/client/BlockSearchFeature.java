package org.bookwormpi.clientsidetesting.client;

import net.minecraft.client.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockSearchFeature {
    public static boolean enabled = false;
    public static Block blockToSearch = Blocks.DIAMOND_BLOCK;
    public static int maxRenderedBlocks = 256;
    public static int scanIntervalTicks = 5;
    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    public static ChunkPos lastPlayerChunk = null;
    private static MinecraftClient lastClient = null;
    private static long lastScanTime = 0;
    private static long lastFixedScanTime = 0;
    private static boolean scanRequested = false;
    private static final int MAX_SCAN_DISTANCE = 16;
    private static final AtomicBoolean scanning = new AtomicBoolean(false);

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlockSearchFeature::onWorldRender);
        // Listen for chunk load/unload and trigger rescan
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (enabled && MinecraftClient.getInstance().player != null) {
                requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (enabled && MinecraftClient.getInstance().player != null) {
                requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            }
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (enabled && world.isClient) {
                scanRequested = true;
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (enabled && world.isClient) {
                scanRequested = true;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public static void requestScan(MinecraftClient client, ChunkPos playerChunk) {
        if (scanning.get()) return; // Prevent concurrent scans
        scanning.set(true);
        CompletableFuture.runAsync(() -> {
            List<BlockPos> results = scanBlocks(client, playerChunk);
            client.execute(() -> {
                foundBlocks.clear();
                foundBlocks.addAll(results);
                scanning.set(false);
            });
        });
    }

    private static List<BlockPos> scanBlocks(MinecraftClient client, ChunkPos playerChunk) {
        List<BlockPos> results = new ArrayList<>();
        int distance = (client.options != null ? Math.min(client.options.getViewDistance().getValue(), MAX_SCAN_DISTANCE) : 8);
        Vec3d playerPos = client.player.getPos();
        java.util.PriorityQueue<BlockPos> closestBlocks = new java.util.PriorityQueue<>(
            (a, b) -> {
                double da = a.getSquaredDistance(playerPos.x, playerPos.y, playerPos.z);
                double db = b.getSquaredDistance(playerPos.x, playerPos.y, playerPos.z);
                return Double.compare(db, da); // max-heap: farthest first
            }
        );
        // Collect all chunk positions in range
        List<ChunkPos> chunkPositions = new ArrayList<>();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                chunkPositions.add(new ChunkPos(playerChunk.x + dx, playerChunk.z + dz));
            }
        }
        // Sort chunk positions by minimum squared distance to player
        chunkPositions.sort((a, b) -> {
            double da = minChunkDistanceSq(a, playerPos);
            double db = minChunkDistanceSq(b, playerPos);
            return Double.compare(da, db);
        });
        for (ChunkPos chunkPos : chunkPositions) {
            // Early culling: skip chunk if its min distance is farther than farthest in queue
            if (closestBlocks.size() >= maxRenderedBlocks) {
                double farthestSq = closestBlocks.peek().getSquaredDistance(playerPos.x, playerPos.y, playerPos.z);
                double chunkMinSq = minChunkDistanceSq(chunkPos, playerPos);
                if (chunkMinSq > farthestSq) continue;
            }
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
                                BlockPos pos = new BlockPos(
                                    chunkPos.getStartX() + x,
                                    yOffset + y,
                                    chunkPos.getStartZ() + z
                                );
                                closestBlocks.add(pos);
                                if (closestBlocks.size() > maxRenderedBlocks) {
                                    closestBlocks.poll(); // remove farthest
                                }
                            }
                        }
                    }
                }
            }
        }
        results.addAll(closestBlocks);
        // Sort results by distance ascending
        results.sort((a, b) -> {
            double da = a.getSquaredDistance(playerPos.x, playerPos.y, playerPos.z);
            double db = b.getSquaredDistance(playerPos.x, playerPos.y, playerPos.z);
            return Double.compare(da, db);
        });
        return results;
    }

    // Helper to compute minimum squared distance from a chunk to the player
    private static double minChunkDistanceSq(ChunkPos chunkPos, Vec3d playerPos) {
        int minX = chunkPos.getStartX();
        int minZ = chunkPos.getStartZ();
        int maxX = chunkPos.getEndX();
        int maxZ = chunkPos.getEndZ();
        double dx = Math.max(0, Math.max(minX - playerPos.x, playerPos.x - maxX));
        double dz = Math.max(0, Math.max(minZ - playerPos.z, playerPos.z - maxZ));
        // Y is not considered for chunk culling, as we scan all Y
        return dx * dx + dz * dz;
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || blockToSearch == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        long now = client.world.getTime();
        // Fixed interval rescan every scanIntervalTicks
        if (now - lastFixedScanTime >= scanIntervalTicks && !scanning.get()) {
            requestScan(client, client.player.getChunkPos());
            lastFixedScanTime = now;
        }
        if (scanRequested && now - lastScanTime >= 20 && !scanning.get()) {
            requestScan(client, client.player.getChunkPos());
            lastScanTime = now;
            scanRequested = false;
        }

        ChunkPos currentChunk = client.player.getChunkPos();
        if (lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk) || client != lastClient) {
            requestScan(client, currentChunk);
            lastPlayerChunk = currentChunk;
            lastClient = client;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer quads = immediate.getBuffer(RenderLayer.getGuiOverlay());

        double min = 0.25, max = 0.75; // 0.5 the size of a cube
        // Synchronized RGB color based on world time
        float hue = ((MinecraftClient.getInstance().world.getTime() % 200) / 200.0f);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float a = 0.5F;
        // Limit the number of rendered blocks, prioritizing closest to camera
        int maxBlocks = Math.max(1, maxRenderedBlocks);
        List<BlockPos> sortedBlocks = foundBlocks.stream()
            .sorted((aPos, bPos) -> {
                double da = aPos.getSquaredDistance(cam.x, cam.y, cam.z);
                double db = bPos.getSquaredDistance(cam.x, cam.y, cam.z);
                return Double.compare(da, db);
            })
            .limit(maxBlocks)
            .toList();
        for (BlockPos pos : sortedBlocks) {
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

    public static void rescanBlocks(MinecraftClient client, ChunkPos playerChunk) {
        // Deprecated: use requestScan instead
    }
}