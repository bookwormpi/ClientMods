package org.bookwormpi.clientsidetesting.client;

import net.minecraft.client.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
    public static int scanDistance = -1; // -1 means use render distance by default
    public static int maxRenderedBlocks = 256;
    public static int scanIntervalTicks = 5;
    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    public static ChunkPos lastPlayerChunk = null;
    private static MinecraftClient lastClient = null;
    private static final int MAX_SCAN_DISTANCE = 16;
    private static final AtomicBoolean scanning = new AtomicBoolean(false);
    private static long lastScanTick = 0;

    public static void register() {
        // Register the world render event!
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
                scanning.set(true);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (enabled && world.isClient) {
                scanning.set(true);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled || blockToSearch == null || foundBlocks.isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            var textRenderer = client.textRenderer;
            var stack = new net.minecraft.item.ItemStack(blockToSearch);
            BlockPos closest = foundBlocks.get(0);
            double minDist = client.player.getPos().squaredDistanceTo(closest.getX() + 0.5, closest.getY() + 0.5, closest.getZ() + 0.5);
            for (BlockPos pos : foundBlocks) {
                double dist = client.player.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < minDist) {
                    minDist = dist;
                    closest = pos;
                }
            }
            int x = client.getWindow().getScaledWidth();
            // Calculate offset for status effect icons
            int effectCount = client.player.getStatusEffects().size();
            int effectIconHeight = 18;
            int effectIconGap = 1;
            int y;
            if (effectCount > 0 && effectCount <= 5) {
                y = 4 + effectIconHeight + effectIconGap;
            } else if (effectCount > 5) {
                y = 4 + effectCount * (effectIconHeight + effectIconGap);
            } else {
                y = 4;
            }
            int iconSize = 16;
            String blockName = stack.getName().getString();
            int nameWidth = textRenderer.getWidth(blockName);
            // Place name right-aligned with 8px margin, icon to the left of name
            int nameX = x - nameWidth - 8;
            int nameY = y + 4;
            int iconX = nameX - iconSize - 6;
            int iconY = y;
            // Draw the block icon in the HUD using DrawContext
            drawContext.drawItem(stack, iconX, iconY);
            drawContext.drawTextWithShadow(textRenderer, blockName, nameX, nameY, 0xFFFFFF);
            // Render colored coordinates (x=red, y=green, z=blue)
            String coords = String.format("[§c%d§r,§a%d§r,§b%d§r]", closest.getX(), closest.getY(), closest.getZ());
            int coordsWidth = textRenderer.getWidth(coords.replaceAll("§.", ""));
            int coordsX = iconX - coordsWidth - 8;
            int coordsY = nameY;
            drawContext.drawTextWithShadow(textRenderer, coords, coordsX, coordsY, 0xFFFFFF);
        });
    }

    public static void requestScan(MinecraftClient client, ChunkPos playerChunk, Block blockType) {
        if (scanning.get()) {
            return; // Prevent concurrent scans
        }
        scanning.set(true);
        CompletableFuture.runAsync(() -> {
            if (scanning.get() && Thread.currentThread().isInterrupted()) {
                scanning.set(false);
                return;
            }
            List<BlockPos> results = scanBlocks(client, playerChunk, blockType);
            client.execute(() -> {
                foundBlocks.clear();
                foundBlocks.addAll(results);
                scanning.set(false);
            });
        });
    }

    // Overload for old calls
    public static void requestScan(MinecraftClient client, ChunkPos playerChunk) {
        requestScan(client, playerChunk, blockToSearch);
    }

    private static List<BlockPos> scanBlocks(MinecraftClient client, ChunkPos playerChunk, Block blockType) {
        List<BlockPos> results = new ArrayList<>();
        int distance = scanDistance > 0 ? Math.min(scanDistance, MAX_SCAN_DISTANCE) : (client.options != null ? Math.min(client.options.getViewDistance().getValue(), MAX_SCAN_DISTANCE) : 8);
        BlockPos playerPos = client.player.getBlockPos();

        // Generate chunk offsets sorted by distance from player chunk
        List<int[]> chunkOffsets = new ArrayList<>();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                chunkOffsets.add(new int[]{dx, dz});
            }
        }
        chunkOffsets.sort((a, b) -> Integer.compare(a[0]*a[0] + a[1]*a[1], b[0]*b[0] + b[1]*b[1]));

        outer:
        for (int[] offset : chunkOffsets) {
            int dx = offset[0];
            int dz = offset[1];
            ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
            boolean loaded = client.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z);
            if (!loaded) {
                continue;
            }
            var chunk = client.world.getChunk(chunkPos.x, chunkPos.z);
            var chunkSections = chunk.getSectionArray();
            int bottomY = chunk.getBottomY();
            // Collect all candidate block positions in this chunk
            List<BlockPos> candidates = new ArrayList<>();
            for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                var section = chunkSections[sectionY];
                if (section == null || section.isEmpty()) continue;
                int yOffset = (sectionY * 16) + bottomY;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            if (section.getBlockState(x, y, z).isOf(blockType)) {
                                BlockPos found = new BlockPos(
                                    chunkPos.getStartX() + x,
                                    yOffset + y,
                                    chunkPos.getStartZ() + z
                                );
                                candidates.add(found);
                            }
                        }
                    }
                }
            }
            // Sort candidates by distance to player
            candidates.sort((a, b) -> Double.compare(a.getSquaredDistance(playerPos), b.getSquaredDistance(playerPos)));
            for (BlockPos found : candidates) {
                results.add(found);
                if (results.size() >= maxRenderedBlocks) {
                    break outer;
                }
            }
        }
        return results;
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || blockToSearch == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        long now = client.world.getTime();
        // Only scan if enough ticks have passed and not already scanning
        if (!scanning.get() && (now - lastScanTick >= scanIntervalTicks)) {
            requestScan(client, client.player.getChunkPos());
            lastScanTick = now;
        }
        // Chunk movement trigger
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
        for (BlockPos pos : foundBlocks) {
            double x = pos.getX() - cam.x;
            double y = pos.getY() - cam.y;
            double z = pos.getZ() - cam.z;
            // Bottom face (y = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, max - min, 0.0, 0.0, 0.0, 0.0, max - min, r, g, b, a, 0.0f, -1.0f, 0.0f);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, max - min, 0.0, 0.0, 0.0, 0.0, max - min, r, g, b, a, 0.0f, 1.0f, 0.0f);
            // Top face (y = max)
            drawQuad(matrices, quads, x + min, y + max, z + min, max - min, 0.0, 0.0, 0.0, 0.0, max - min, r, g, b, a, 0.0f, 1.0f, 0.0f);
            drawQuadReversed(matrices, quads, x + min, y + max, z + min, max - min, 0.0, 0.0, 0.0, 0.0, max - min, r, g, b, a, 0.0f, -1.0f, 0.0f);
            // North face (z = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, max - min, 0.0, 0.0, 0.0, max - min, 0.0, r, g, b, a, 0.0f, 0.0f, -1.0f);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, max - min, 0.0, 0.0, 0.0, max - min, 0.0, r, g, b, a, 0.0f, 0.0f, 1.0f);
            // South face (z = max)
            drawQuad(matrices, quads, x + min, y + min, z + max, max - min, 0.0, 0.0, 0.0, max - min, 0.0, r, g, b, a, 0.0f, 0.0f, 1.0f);
            drawQuadReversed(matrices, quads, x + min, y + min, z + max, max - min, 0.0, 0.0, 0.0, max - min, 0.0, r, g, b, a, 0.0f, 0.0f, -1.0f);
            // West face (x = min)
            drawQuad(matrices, quads, x + min, y + min, z + min, 0.0, max - min, 0.0, 0.0, 0.0, max - min, r, g, b, a, -1.0f, 0.0f, 0.0f);
            drawQuadReversed(matrices, quads, x + min, y + min, z + min, 0.0, max - min, 0.0, 0.0, 0.0, max - min, r, g, b, a, 1.0f, 0.0f, 0.0f);
            // East face (x = max)
            drawQuad(matrices, quads, x + max, y + min, z + min, 0.0, max - min, 0.0, 0.0, 0.0, max - min, r, g, b, a, 1.0f, 0.0f, 0.0f);
            drawQuadReversed(matrices, quads, x + max, y + min, z + min, 0.0, max - min, 0.0, 0.0, 0.0, max - min, r, g, b, a, -1.0f, 0.0f, 0.0f);
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

    // --- GUI/Config API for BlockSearchFeature ---
    public static void setBlockToSearch(Block block) {
        blockToSearch = block;
        enabled = true;
        lastPlayerChunk = null;
        // Clear previous results so old blocks are not rendered
        foundBlocks.clear();
        // Force scanning flag to false so a new scan always starts
        scanning.set(false);
        // Immediately trigger a scan for the new block type
        if (MinecraftClient.getInstance().player != null) {
            requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos(), block);
        }
    }

    public static void setScanDistance(int distance) {
        scanDistance = distance;
        if (enabled && MinecraftClient.getInstance().player != null) {
            foundBlocks.clear(); // Clear previous results
            scanning.set(false); // Ensure scan can run
            requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            if (MinecraftClient.getInstance().world != null) {
                MinecraftClient.getInstance().worldRenderer.reload();
            }
        }
    }

    public static void setMaxRenderedBlocks(int maxBlocks) {
        maxRenderedBlocks = maxBlocks;
        foundBlocks.clear(); // Clear previous results
        scanning.set(false); // Ensure scan can run
        if (enabled && MinecraftClient.getInstance().player != null) {
            requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            if (MinecraftClient.getInstance().world != null) {
                MinecraftClient.getInstance().worldRenderer.reload();
            }
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        foundBlocks.clear(); // Clear previous results
        scanning.set(false); // Ensure scan can run
        if (enabled && MinecraftClient.getInstance().player != null) {
            requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            if (MinecraftClient.getInstance().world != null) {
                MinecraftClient.getInstance().worldRenderer.reload();
            }
        } else if (MinecraftClient.getInstance().world != null) {
            // If disabling, force a rerender to clear highlights
            MinecraftClient.getInstance().worldRenderer.reload();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getScanDistance() {
        return scanDistance;
    }

    public static int getMaxRenderedBlocks() {
        return maxRenderedBlocks;
    }

    public static Block getBlockToSearch() {
        return blockToSearch;
    }

    public static void setScanIntervalTicks(int ticks) {
        scanIntervalTicks = ticks;
        foundBlocks.clear(); // Clear previous results
        scanning.set(false); // Ensure scan can run
        if (enabled && MinecraftClient.getInstance().player != null) {
            requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
            if (MinecraftClient.getInstance().world != null) {
                MinecraftClient.getInstance().worldRenderer.reload();
            }
        }
    }

    public static int getScanIntervalTicks() {
        return scanIntervalTicks;
    }
}