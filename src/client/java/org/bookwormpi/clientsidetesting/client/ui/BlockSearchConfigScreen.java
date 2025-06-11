package org.bookwormpi.clientsidetesting.client.ui;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.bookwormpi.clientsidetesting.client.features.BlockSearchFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class BlockSearchConfigScreen extends Screen {
    private final Screen parent;
    private final List<Block> allBlocks;
    private int scrollOffset = 0;
    private static final int SLOT_SIZE = 18; // JEI uses 18x18 slots
    private static final int BORDER = 8;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 8;
    
    private TextFieldWidget searchField;
    private String searchText = "";
    private List<Block> filteredBlocks;
    private final int backgroundColor = 0xFF202020;
    private final int searchBarColor = 0xFF404040;

    public BlockSearchConfigScreen(Screen parent) {
        super(Text.translatable("blocksearch.screen.title"));
        this.parent = parent;
        this.allBlocks = new ArrayList<>(Registries.BLOCK.stream().toList());
        this.filteredBlocks = new ArrayList<>(allBlocks);
    }

    @Override
    protected void init() {
        super.init();
        
        // Calculate dimensions for JEI-like centered layout
        int totalWidth = ITEMS_PER_ROW * SLOT_SIZE + BORDER * 2;
        int totalHeight = VISIBLE_ROWS * SLOT_SIZE + BORDER * 2 + 20; // 20 for search box height
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2;

        // Create search box with JEI styling
        searchField = new TextFieldWidget(
            textRenderer,
            startX + BORDER,
            startY + BORDER,
            totalWidth - BORDER * 2,
            12,
            Text.literal("")
        );
        searchField.setMaxLength(32);
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setDrawsBackground(true);
        searchField.setVisible(true);
        searchField.setFocused(true); // Focus the search box so the cursor is active
        searchField.setText(searchText);
        searchField.setChangedListener(this::onSearchChanged);
        addSelectableChild(searchField);
    }

    private void onSearchChanged(String newText) {
        searchText = newText.toLowerCase();
        scrollOffset = 0; // Reset scroll position when search changes
        updateFilteredBlocks();
    }

    private void updateFilteredBlocks() {
        filteredBlocks = allBlocks.stream()
            .filter(block -> {
                if (block.getDefaultState().isAir()) return false;
                String name = block.getName().getString().toLowerCase();
                String id = Registries.BLOCK.getId(block).toString().toLowerCase();
                return name.contains(searchText) || id.contains(searchText);
            })
            .sorted(Comparator.comparing(block -> block.getName().getString().toLowerCase()))
            .collect(Collectors.toList());
        
        // Ensure scroll offset is valid after filtering
        int maxScroll = Math.max(0, (filteredBlocks.size() / ITEMS_PER_ROW) - VISIBLE_ROWS + 1);
        scrollOffset = Math.min(maxScroll, scrollOffset);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.client.world != null) {
            context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        } else {
            context.fillGradient(0, 0, this.width, this.height, 0xFF303030, 0xFF202020);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int totalWidth = ITEMS_PER_ROW * SLOT_SIZE + BORDER * 2;
        int totalHeight = VISIBLE_ROWS * SLOT_SIZE + BORDER * 2 + 20;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2;

        // Draw GUI container with solid background
        context.fill(startX, startY, startX + totalWidth, startY + totalHeight, backgroundColor);
        context.fill(startX + BORDER - 1, startY + BORDER - 1,
                startX + totalWidth - BORDER + 1, startY + BORDER + 14, searchBarColor);

        searchField.render(context, mouseX, mouseY, delta);
        
        // Calculate visible items
        int maxScroll = Math.max(0, (filteredBlocks.size() / ITEMS_PER_ROW) - VISIBLE_ROWS + 1);
        scrollOffset = Math.min(maxScroll, scrollOffset);
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int itemY = startY + BORDER + 20;
        
        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_ROW * VISIBLE_ROWS, filteredBlocks.size()); i++) {
            Block block = filteredBlocks.get(i);
            ItemStack stack = new ItemStack(block);
            int row = (i - startIndex) / ITEMS_PER_ROW;
            int col = (i - startIndex) % ITEMS_PER_ROW;
            int itemX = startX + BORDER + col * SLOT_SIZE;
            
            // Draw slot background
            context.fill(itemX, itemY + row * SLOT_SIZE,
                        itemX + SLOT_SIZE - 1, itemY + row * SLOT_SIZE + SLOT_SIZE - 1,
                        0xFF373737);
            
            // Draw item
            context.drawItem(stack, itemX + 1, itemY + row * SLOT_SIZE + 1);
            
            // Handle hover
            if (mouseX >= itemX && mouseX < itemX + SLOT_SIZE &&
                mouseY >= itemY + row * SLOT_SIZE && mouseY < itemY + row * SLOT_SIZE + SLOT_SIZE) {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(stack.getName());
                tooltip.add(Text.literal(Registries.BLOCK.getId(block).toString()).formatted(net.minecraft.util.Formatting.GRAY));
                context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalWidth = ITEMS_PER_ROW * SLOT_SIZE + BORDER * 2;
        int startX = (width - totalWidth) / 2;
        int startY = (height - (VISIBLE_ROWS * SLOT_SIZE + BORDER * 2 + 20)) / 2;
        int itemsY = startY + BORDER + 20;

        // Calculate which item was clicked
        if (mouseX >= startX + BORDER && mouseX < startX + totalWidth - BORDER &&
            mouseY >= itemsY && mouseY < itemsY + VISIBLE_ROWS * SLOT_SIZE) {
            
            int col = (int)((mouseX - (startX + BORDER)) / SLOT_SIZE);
            int row = (int)((mouseY - itemsY) / SLOT_SIZE);
            int index = scrollOffset * ITEMS_PER_ROW + row * ITEMS_PER_ROW + col;
            
            if (index >= 0 && index < filteredBlocks.size()) {
                BlockSearchFeature.blockToSearch = filteredBlocks.get(index);
                BlockSearchFeature.enabled = true;
                if (MinecraftClient.getInstance().player != null) {
                    System.out.println("[BlockSearchConfigScreen] Block selected, requesting scan...");
                    BlockSearchFeature.requestScan(MinecraftClient.getInstance(), MinecraftClient.getInstance().player.getChunkPos());
                }
                MinecraftClient.getInstance().setScreen(parent);
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, (filteredBlocks.size() / ITEMS_PER_ROW) - VISIBLE_ROWS + 1);
        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}