package org.bookwormpi.clientsidetesting.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ClientsideTestingConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 18;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private SliderWidget scanDistanceSlider;

    public ClientsideTestingConfigScreen() {
        super(Text.literal("Clientside Testing Features"));
    }

    @Override
    protected void init() {
        clearChildren();
        int padding = 16;
        int panelWidth = width - padding * 2;
        int panelHeight = height - padding * 2;
        int panelX = padding;
        int panelY = padding;
        int colPad = 16;
        int colGap = 16;
        int colWidth = (panelWidth - colGap - colPad * 2) / 2;
        int leftX = panelX + colPad;
        int rightX = leftX + colWidth + colGap;
        int yLeft = panelY + 28 - scrollOffset;
        int yRight = panelY + 28 - scrollOffset;
        int sectionPad = 12;
        int buttonWidth = Math.max(120, colWidth - 10);
        int indent = 18;
        // Left column: Chunk HUD
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Chunk HUD").formatted(Formatting.BOLD),
                btn -> {}
        ).dimensions(leftX, yLeft, buttonWidth, BUTTON_HEIGHT).build()).active = false;
        yLeft += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Toggle HUD: " + (ClientsidetestingClient.showChunkPlayers ? "ON" : "OFF")),
                btn -> { ClientsidetestingClient.showChunkPlayers = !ClientsidetestingClient.showChunkPlayers; }
        ).dimensions(leftX + indent, yLeft, buttonWidth - indent, BUTTON_HEIGHT).build());
        yLeft += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Chunk Check Radius: " + ClientsidetestingClient.chunkCheckRadius),
                btn -> {}
        ).dimensions(leftX + indent, yLeft, buttonWidth - indent, BUTTON_HEIGHT).build()).active = false;
        yLeft += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("-"),
                btn -> { if (ClientsidetestingClient.chunkCheckRadius > 1) { ClientsidetestingClient.chunkCheckRadius--; } }
        ).dimensions(leftX + indent, yLeft, 40, BUTTON_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("+"),
                btn -> { int maxRadius = MinecraftClient.getInstance().options.getViewDistance().getValue(); if (ClientsidetestingClient.chunkCheckRadius < maxRadius) { ClientsidetestingClient.chunkCheckRadius++; } }
        ).dimensions(leftX + indent + 50, yLeft, 40, BUTTON_HEIGHT).build());
        yLeft += BUTTON_HEIGHT + sectionPad;
        // Right column: Player Boxes
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Player Boxes").formatted(Formatting.BOLD),
                btn -> {}
        ).dimensions(rightX, yRight, buttonWidth, BUTTON_HEIGHT).build()).active = false;
        yRight += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Toggle Boxes: " + (ClientsidetestingClient.showPlayerBoxes ? "ON" : "OFF")),
                btn -> { ClientsidetestingClient.showPlayerBoxes = !ClientsidetestingClient.showPlayerBoxes; this.init(); }
        ).dimensions(rightX + indent, yRight, buttonWidth - indent, BUTTON_HEIGHT).build());
        yRight += BUTTON_HEIGHT + sectionPad;
        // Right column: Block Search
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Block Search").formatted(Formatting.BOLD),
                btn -> {}
        ).dimensions(rightX, yRight, buttonWidth, BUTTON_HEIGHT).build()).active = false;
        yRight += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Toggle Block Search: " + (BlockSearchFeature.enabled ? "ON" : "OFF")),
                btn -> { BlockSearchFeature.enabled = !BlockSearchFeature.enabled; this.init(); }
        ).dimensions(rightX + indent, yRight, buttonWidth - indent, BUTTON_HEIGHT).build());
        yRight += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Block: " + net.minecraft.registry.Registries.BLOCK.getId(BlockSearchFeature.blockToSearch)),
                btn -> MinecraftClient.getInstance().setScreen(new BlockSearchConfigScreen(this))
        ).dimensions(rightX + indent, yRight, buttonWidth - indent, BUTTON_HEIGHT).build());
        yRight += BUTTON_HEIGHT + 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Scan"),
                btn -> { BlockSearchFeature.enabled = true; BlockSearchFeature.lastPlayerChunk = null; if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) { BlockSearchFeature.rescanBlocks(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos()); } }
        ).dimensions(rightX + indent, yRight, buttonWidth - indent, BUTTON_HEIGHT).build());
        yRight += BUTTON_HEIGHT + 2;
        // Scan Distance Slider
        int sliderMin = 1;
        int sliderMax = 16;
        int sliderValue = BlockSearchFeature.scanDistance > 0 ? BlockSearchFeature.scanDistance : 8;
        scanDistanceSlider = new SliderWidget(rightX + indent, yRight, buttonWidth - indent, BUTTON_HEIGHT, Text.literal("Scan Distance: " + sliderValue), (sliderValue - sliderMin) / (float)(sliderMax - sliderMin)) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Scan Distance: " + getIntValue()));
            }
            @Override
            protected void applyValue() {
                BlockSearchFeature.scanDistance = getIntValue();
            }
            private int getIntValue() {
                return sliderMin + (int)Math.round((sliderMax - sliderMin) * this.value);
            }
        };
        this.addDrawableChild(scanDistanceSlider);
        yRight += BUTTON_HEIGHT + sectionPad;
        // Done button (always at the bottom of the panel)
        int doneY = Math.max(yLeft, yRight) + 8;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> MinecraftClient.getInstance().setScreen(null)
        ).dimensions(panelX + panelWidth / 2 - 40, doneY, 80, BUTTON_HEIGHT).build());
        // Calculate total content height for scrolling
        contentHeight = Math.max(yLeft, yRight) - (panelY + 28) + BUTTON_HEIGHT + sectionPad;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int padding = 16;
        int panelHeight = height - padding * 2;
        int maxScroll = Math.max(0, contentHeight - panelHeight + 40); // 40 for padding at bottom
        if (verticalAmount < 0) {
            scrollOffset = Math.min(scrollOffset + 20, maxScroll);
        } else if (verticalAmount > 0) {
            scrollOffset = Math.max(scrollOffset - 20, 0);
        }
        this.init();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int padding = 16;
        int panelWidth = width - padding * 2;
        int panelHeight = height - padding * 2;
        int panelX = padding;
        int panelY = padding;
        context.fill(0, 0, width, height, 0xA0000000);
        // Border
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF444444);
        // Panel background
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF222222);
        // Title with extra padding
        String title = "Clientside Testing Config";
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 18, 0xFFFFFF);
        // Visual scroll bar
        int doneButtonY = panelY + panelHeight - 32;
        int visibleHeight = doneButtonY - (panelY + 28);
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        if (contentHeight > visibleHeight) {
            int barHeight = Math.max(20, (int)((float)visibleHeight * visibleHeight / contentHeight));
            int barY = (int)((float)scrollOffset / maxScroll * (visibleHeight - barHeight)) + panelY + 28;
            int barX = panelX + panelWidth - 8;
            context.fill(barX, barY, barX + 6, barY + barHeight, 0xFF555555);
            context.fill(barX + 1, barY + 1, barX + 5, barY + barHeight - 1, 0xFFAAAAAA);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}