package org.bookwormpi.clientsidetesting.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ClientsideTestingConfigScreen extends Screen {
    private boolean chunkHudExpanded = false;
    private boolean playerBoxesExpanded = false;
    private boolean blockSearchExpanded = false;

    public ClientsideTestingConfigScreen() {
        super(Text.literal("Clientside Testing Features"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        int y = this.height / 2 - 50;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = this.width / 2 - buttonWidth / 2;

        // Chunk Player HUD dropdown
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal((chunkHudExpanded ? "▼ " : "► ") + "Chunk Player HUD: " + (ClientsidetestingClient.showChunkPlayers ? "ON" : "OFF")),
                btn -> {
                    chunkHudExpanded = !chunkHudExpanded;
                    this.init();
                }
        ).dimensions(x, y, buttonWidth, buttonHeight).build());
        int yOffset = y + buttonHeight + 5;

        if (chunkHudExpanded) {
            // Toggle HUD
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Toggle HUD: " + (ClientsidetestingClient.showChunkPlayers ? "ON" : "OFF")),
                    btn -> {
                        ClientsidetestingClient.showChunkPlayers = !ClientsidetestingClient.showChunkPlayers;
                        this.init();
                    }
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build());
            yOffset += buttonHeight + 5;

            // Chunk radius label
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Chunk Check Radius: " + ClientsidetestingClient.chunkCheckRadius),
                    btn -> {}
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build()).active = false;
            yOffset += buttonHeight + 2;

            // Decrease radius
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("-"),
                    btn -> {
                        if (ClientsidetestingClient.chunkCheckRadius > 1) {
                            ClientsidetestingClient.chunkCheckRadius--;
                            this.init();
                        }
                    }
            ).dimensions(x + 10, yOffset, 90, buttonHeight).build());

            // Increase radius
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("+"),
                    btn -> {
                        int maxRadius = MinecraftClient.getInstance().options.getViewDistance().getValue();
                        if (ClientsidetestingClient.chunkCheckRadius < maxRadius) {
                            ClientsidetestingClient.chunkCheckRadius++;
                            this.init();
                        }
                    }
            ).dimensions(x + 110, yOffset, 90, buttonHeight).build());
            yOffset += buttonHeight + 10;
        }

        // Player Boxes dropdown
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal((playerBoxesExpanded ? "▼ " : "► ") + "Player Boxes: " + (ClientsidetestingClient.showPlayerBoxes ? "ON" : "OFF")),
                btn -> {
                    playerBoxesExpanded = !playerBoxesExpanded;
                    this.init();
                }
        ).dimensions(x, yOffset, buttonWidth, buttonHeight).build());
        yOffset += buttonHeight + 5;

        if (playerBoxesExpanded) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Toggle Boxes: " + (ClientsidetestingClient.showPlayerBoxes ? "ON" : "OFF")),
                    btn -> {
                        ClientsidetestingClient.showPlayerBoxes = !ClientsidetestingClient.showPlayerBoxes;
                        this.init();
                    }
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build());
            yOffset += buttonHeight + 10;
        }

        // Block Search dropdown
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal((blockSearchExpanded ? "▼ " : "► ") + "Block Search: " + (BlockSearchFeature.enabled ? "ON" : "OFF")),
                btn -> {
                    blockSearchExpanded = !blockSearchExpanded;
                    this.init();
                }
        ).dimensions(x, yOffset, buttonWidth, buttonHeight).build());
        yOffset += buttonHeight + 5;

        if (blockSearchExpanded) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Toggle Block Search: " + (BlockSearchFeature.enabled ? "ON" : "OFF")),
                    btn -> {
                        BlockSearchFeature.enabled = !BlockSearchFeature.enabled;
                        this.init();
                    }
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build());
            yOffset += buttonHeight + 5;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Block: " + net.minecraft.registry.Registries.BLOCK.getId(BlockSearchFeature.blockToSearch)),
                    btn -> MinecraftClient.getInstance().setScreen(new BlockSearchConfigScreen(this))
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build());
            yOffset += buttonHeight + 5;

            // Scan button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Scan"),
                    btn -> {
                        BlockSearchFeature.enabled = true;
                        BlockSearchFeature.lastPlayerChunk = null;
                        // Force scan immediately
                        if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
                            BlockSearchFeature.rescanBlocks(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
                        }
                    }
            ).dimensions(x + 10, yOffset, buttonWidth - 20, buttonHeight).build());
            yOffset += buttonHeight + 10;
        }

        // Done
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> MinecraftClient.getInstance().setScreen(null)
        ).dimensions(x, yOffset, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}