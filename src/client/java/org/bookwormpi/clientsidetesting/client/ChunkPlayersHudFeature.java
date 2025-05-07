package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChunkPlayersHudFeature {
    private static final List<String> playersInChunk = new ArrayList<>();

    public static void register() {
        // Switch to the new HUD event if available in your Fabric API/Minecraft version.
        // If not, keep the deprecated HudRenderCallback for now, but add a comment for future migration.
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ClientsidetestingClient.showChunkPlayers) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            int renderDistance = client.options.getViewDistance().getValue();
            int chunkRadius = Math.min(ClientsidetestingClient.chunkCheckRadius, renderDistance);

            playersInChunk.clear();
            int localChunkX = client.player.getChunkPos().x;
            int localChunkZ = client.player.getChunkPos().z;
            String localName = client.player.getName().getString();

            for (PlayerEntity player : client.world.getPlayers()) {
                if (player == client.player) continue;

                int chunkX = player.getChunkPos().x;
                int chunkZ = player.getChunkPos().z;

                int dx = chunkX - localChunkX;
                int dz = chunkZ - localChunkZ;

                if (Math.abs(dx) <= chunkRadius && Math.abs(dz) <= chunkRadius) {
                    double blockDist = player.getPos().distanceTo(client.player.getPos());
                    playersInChunk.add(localName + "  " + player.getName().getString() + " (" + (int) blockDist + " blocks)");
                }
            }

            int x = 5;
            int y = 5;
            context.drawText(client.textRenderer, Text.literal("Nearby players:"), x, y, 0xFFFFFF, true);

            int offset = 12;
            int padding = 4;
            for (String name : playersInChunk) {
                context.drawText(client.textRenderer, Text.literal(name), x, y - offset, 0xFFAAAA, true);
                offset += 12 + padding;
            }
        });
    }
}