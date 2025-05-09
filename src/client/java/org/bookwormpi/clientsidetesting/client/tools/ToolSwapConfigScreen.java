package org.bookwormpi.clientsidetesting.client.tools;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.bookwormpi.clientsidetesting.client.ClientsidetestingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToolSwapConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 18;
    private static final int ENTRY_HEIGHT = 24;
    private final Screen parent;
    private int scrollOffset = 0;
    private List<OverrideEntry> overrideEntries = new ArrayList<>();
    private TextFieldWidget blockIdField;
    private TextFieldWidget toolTypeField;
    private String blockIdInput = "";
    private String toolTypeInput = "";
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private static final long MESSAGE_DURATION = 3000; // 3 seconds

    public ToolSwapConfigScreen(Screen parent) {
        super(Text.literal("Tool Swap Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        loadOverrides();
        
        int padding = 16;
        int width = this.width - (padding * 2);
        int centerX = this.width / 2;
        
        // Add back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                button -> this.client.setScreen(this.parent)
        ).dimensions(centerX - 154, this.height - 30, 150, 20).build());
        
        // Add save button (saves state on close, but this button gives feedback)
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                button -> {
                    saveOverrides();
                    showStatusMessage("Settings saved!");
                }
        ).dimensions(centerX + 4, this.height - 30, 150, 20).build());
        
        // Tool Swap toggle button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Tool Swap: " + (ToolSwapFeature.toolSwapEnabled ? "ON" : "OFF")),
                button -> {
                    ToolSwapFeature.toolSwapEnabled = !ToolSwapFeature.toolSwapEnabled;
                    button.setMessage(Text.literal("Tool Swap: " + (ToolSwapFeature.toolSwapEnabled ? "ON" : "OFF")));
                }
        ).dimensions(padding, padding, 150, BUTTON_HEIGHT).build());
        
        // Auto Swap on Mine toggle button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto Swap: " + (ToolSwapFeature.autoSwapOnMine ? "ON" : "OFF")),
                button -> {
                    ToolSwapFeature.autoSwapOnMine = !ToolSwapFeature.autoSwapOnMine;
                    button.setMessage(Text.literal("Auto Swap: " + (ToolSwapFeature.autoSwapOnMine ? "ON" : "OFF")));
                }
        ).dimensions(padding + 160, padding, 150, BUTTON_HEIGHT).build());
        
        // Keybind information
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Manual Swap: R"),
                button -> {}
        ).dimensions(padding + 320, padding, 150, BUTTON_HEIGHT).build()).active = false;
        
        // Add new override section - Block ID field
        int fieldY = padding + BUTTON_HEIGHT + 10;
        blockIdField = new TextFieldWidget(this.textRenderer, padding, fieldY, 150, 20, Text.literal(""));
        blockIdField.setMaxLength(50);
        blockIdField.setText(blockIdInput);
        blockIdField.setChangedListener(s -> blockIdInput = s);
        this.addDrawableChild(blockIdField);
        
        // Tool type field
        toolTypeField = new TextFieldWidget(this.textRenderer, padding + 160, fieldY, 100, 20, Text.literal(""));
        toolTypeField.setMaxLength(10);
        toolTypeField.setText(toolTypeInput);
        toolTypeField.setChangedListener(s -> toolTypeInput = s);
        this.addDrawableChild(toolTypeField);
        
        // Add button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Add Override"),
                button -> this.addToolOverride()
        ).dimensions(padding + 270, fieldY, 100, 20).build());
        
        // Add scroll buttons
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▲"),
                button -> scrollOffset = Math.max(0, scrollOffset - 1)
        ).dimensions(width - 30 + padding, padding + BUTTON_HEIGHT + 40, 20, 20).build());
        
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▼"),
                button -> {
                    if (overrideEntries.size() > 8) { // Only allow scrolling if we have more than 8 entries
                        scrollOffset = Math.min(overrideEntries.size() - 8, scrollOffset + 1);
                    }
                }
        ).dimensions(width - 30 + padding, this.height - 70, 20, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        
        // Draw title
        context.drawTextWithShadow(this.textRenderer, this.title, this.width / 2 - this.textRenderer.getWidth(this.title) / 2, 8, 0xFFFFFF);
        
        // Draw labels
        int padding = 16;
        int fieldY = padding + BUTTON_HEIGHT + 10;
        context.drawText(this.textRenderer, "Block ID (minecraft:block_name):", padding, fieldY - 10, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Tool Type (pickaxe/axe/shovel/hoe/shears):", padding + 160, fieldY - 10, 0xAAAAAA, false);
        
        // Draw section title
        int listY = fieldY + 30;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Block-Tool Overrides:").formatted(Formatting.YELLOW), padding, listY, 0xFFFFFF);
        
        // Draw column headers
        listY += 16;
        context.drawText(this.textRenderer, "Block", padding, listY, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Tool Type", padding + 250, listY, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Actions", padding + 350, listY, 0xAAAAAA, false);
        
        // Draw separator line
        listY += 12;
        context.fill(padding, listY, this.width - padding, listY + 1, 0x66FFFFFF);
        listY += 8;
        
        // Draw block-tool overrides list with offset
        int visibleEntries = Math.min(8, overrideEntries.size() - scrollOffset);
        for (int i = 0; i < visibleEntries; i++) {
            int index = i + scrollOffset;
            if (index < overrideEntries.size()) {
                OverrideEntry entry = overrideEntries.get(index);
                int entryY = listY + (i * ENTRY_HEIGHT);
                
                // Draw block name
                String blockName = Registries.BLOCK.getId(entry.block).toString();
                context.drawText(this.textRenderer, blockName, padding, entryY, 0xFFFFFF, false);
                
                // Draw tool type
                context.drawText(this.textRenderer, entry.toolType, padding + 250, entryY, 0xFFFFFF, false);
                
                // Draw delete button
                int deleteX = padding + 350;
                int deleteWidth = 60;
                boolean hoveringDelete = mouseX >= deleteX && mouseX < deleteX + deleteWidth &&
                                       mouseY >= entryY - 2 && mouseY < entryY + 16;
                int deleteColor = hoveringDelete ? 0xFFFF5555 : 0xFFAA5555;
                
                context.fill(deleteX, entryY - 2, deleteX + deleteWidth, entryY + 16, 0x66000000);
                context.drawCenteredTextWithShadow(this.textRenderer, "Remove", deleteX + deleteWidth / 2, entryY + 2, deleteColor);
            }
        }
        
        // Draw status message if active
        if (System.currentTimeMillis() - statusMessageTime < MESSAGE_DURATION) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusMessage, this.width / 2, this.height - 50, 0x00FF00);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle delete button clicks
        int padding = 16;
        int listY = padding + BUTTON_HEIGHT + 10 + 30 + 16 + 12 + 8;
        int deleteX = padding + 350;
        int deleteWidth = 60;
        
        int visibleEntries = Math.min(8, overrideEntries.size() - scrollOffset);
        for (int i = 0; i < visibleEntries; i++) {
            int index = i + scrollOffset;
            if (index < overrideEntries.size()) {
                int entryY = listY + (i * ENTRY_HEIGHT);
                
                if (mouseX >= deleteX && mouseX < deleteX + deleteWidth &&
                    mouseY >= entryY - 2 && mouseY < entryY + 16) {
                    // Click on delete button
                    OverrideEntry entry = overrideEntries.get(index);
                    ToolSwapFeature.setToolOverride(entry.block, null); // Remove the override
                    loadOverrides(); // Reload the list
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void close() {
        saveOverrides();
        this.client.setScreen(this.parent);
    }
    
    /**
     * Add a new tool override from the input fields
     */
    private void addToolOverride() {
        if (blockIdInput.isEmpty() || toolTypeInput.isEmpty()) {
            showStatusMessage("Block ID and Tool Type must not be empty!");
            return;
        }
        // Validate tool type
        String toolType = toolTypeInput.toLowerCase();
        if (!toolType.equals("pickaxe") && !toolType.equals("axe") && 
            !toolType.equals("shovel") && !toolType.equals("hoe") && 
            !toolType.equals("shears")) {
            showStatusMessage("Invalid tool type! Use: pickaxe, axe, shovel, hoe, or shears");
            return;
        }
        // Parse block ID
        String blockId = blockIdInput;
        if (!blockId.contains(":")) {
            blockId = "minecraft:" + blockId;
        }
        try {
            Identifier id = Identifier.of(blockId);
            Block block = Registries.BLOCK.get(id);
            if (block != null && !block.getDefaultState().isAir()) {
                ToolSwapFeature.setToolOverride(block, toolType);
                loadOverrides();
                blockIdInput = "";
                toolTypeInput = "";
                blockIdField.setText("");
                toolTypeField.setText("");
                showStatusMessage("Override added successfully!");
            } else {
                showStatusMessage("Block not found: " + blockId);
            }
        } catch (Exception e) {
            showStatusMessage("Invalid block ID format!");
        }
    }
    
    /**
     * Load overrides from the feature into the UI
     */
    private void loadOverrides() {
        Map<Block, String> overrides = ToolSwapFeature.getToolOverrides();
        overrideEntries.clear();
        
        for (Map.Entry<Block, String> entry : overrides.entrySet()) {
            overrideEntries.add(new OverrideEntry(entry.getKey(), entry.getValue()));
        }
    }
    
    /**
     * Save overrides from the UI back to the feature
     * (Currently a no-op since changes are applied directly to the feature)
     */
    private void saveOverrides() {
        // Changes are applied directly to ToolSwapFeature, so no need to save anything
        ClientsidetestingClient.LOGGER.info("Tool swap settings saved");
    }
    
    /**
     * Show a status message at the bottom of the screen
     */
    private void showStatusMessage(String message) {
        statusMessage = message;
        statusMessageTime = System.currentTimeMillis();
    }
    
    /**
     * Data class for tool overrides
     */
    private static class OverrideEntry {
        public final Block block;
        public final String toolType;
        
        public OverrideEntry(Block block, String toolType) {
            this.block = block;
            this.toolType = toolType;
        }
    }
}
