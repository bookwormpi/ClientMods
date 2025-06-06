package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChunkPlayersHudFeature {
    private static final List<String> playersInChunk = new ArrayList<>();
    private static final Identifier CHUNK_PLAYERS_LAYER = Identifier.of("clientsidetesting", "chunk-players-layer");

    public static void register() {
        // Use the new HUD Layer Registration API (replaces deprecated HudRenderCallback)
        HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> 
            layeredDrawer.attachLayerBefore(IdentifiedLayer.CHAT, CHUNK_PLAYERS_LAYER, ChunkPlayersHudFeature::render)
        );
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
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
                context.drawText(client.textRenderer, Text.literal(name), x, y + offset, 0xFFAAAA, true);
                offset += 12 + padding;
            }
    }
}