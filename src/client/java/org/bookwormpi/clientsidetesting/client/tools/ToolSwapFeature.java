package org.bookwormpi.clientsidetesting.client.tools;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.bookwormpi.clientsidetesting.client.ClientsidetestingClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ToolSwapFeature {
    // Configuration
    public static boolean toolSwapEnabled = true;
    public static boolean autoSwapOnMine = true;
    private static final Map<Block, String> customToolOverrides = new HashMap<>();
    
    // Key binding for manual tool swap
    private static KeyBinding toolSwapKey;
    
    // Tool categories
    private static final String TOOL_PICKAXE = "pickaxe";
    private static final String TOOL_AXE = "axe";
    private static final String TOOL_SHOVEL = "shovel";
    private static final String TOOL_HOE = "hoe";
    private static final String TOOL_SHEARS = "shears";
    
    // Last looked at block to prevent constant swapping
    private static Block lastLookedAtBlock = null;
    private static long lastSwapTime = 0;
    private static final long SWAP_COOLDOWN_MS = 500; // Cooldown to prevent rapid swapping
    
    /**
     * Register the tool swap feature
     */
    public static void register() {
        // Register key binding for manual tool swap
        toolSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientsidetesting.tool_swap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R, // Default to R key
                "category.clientsidetesting.tools"
        ));
        
        // Register tick event for tool swap handling
        ClientTickEvents.END_CLIENT_TICK.register(ToolSwapFeature::onClientTick);
        
        ClientsidetestingClient.LOGGER.info("Tool Swap Feature registered");
    }
    
    /**
     * Client tick handler to detect when player is mining and swap tools
     */
    private static void onClientTick(MinecraftClient client) {
        if (!toolSwapEnabled || client.player == null || client.world == null) return;
        
        // Handle manual tool swap key press
        if (toolSwapKey.wasPressed()) {
            swapToBestTool(client);
            return;
        }
        
        // Skip auto-swap if it's disabled
        if (!autoSwapOnMine) return;
        
        // Check if player is breaking a block
        if (client.options.attackKey.isPressed()) {
            HitResult hit = client.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = client.world.getBlockState(pos);
                Block block = state.getBlock();
                
                // Only swap if we're looking at a different block or enough time has passed
                long currentTime = System.currentTimeMillis();
                if ((block != lastLookedAtBlock || currentTime - lastSwapTime > SWAP_COOLDOWN_MS) && 
                        isValidToolInHand(client.player)) {
                    swapToBestToolForBlock(client, block);
                    lastLookedAtBlock = block;
                    lastSwapTime = currentTime;
                }
            }
        } else {
            // Reset last block when not mining
            lastLookedAtBlock = null;
        }
    }
    
    /**
     * Checks if the player is holding a valid tool (not a sword or non-tool)
     */
    private static boolean isValidToolInHand(PlayerEntity player) {
        ItemStack heldItem = player.getMainHandStack();
        Item item = heldItem.getItem();
        
        // Only allow swapping if currently holding a tool or empty hand
        return heldItem.isEmpty() || isMiningTool(item);
    }
    
    /**
     * Check if an item is a mining tool
     */
    private static boolean isMiningTool(Item item) {
        return isAxe(item) || isShovel(item) || isPickaxe(item) || 
               isHoe(item) || item instanceof ShearsItem;
    }
    
    /**
     * Check if an item is an axe by examining the item's mining speed tags
     */
    private static boolean isAxe(Item item) {
        return item.toString().toLowerCase().contains("axe");
    }
    
    /**
     * Check if an item is a pickaxe
     */
    private static boolean isPickaxe(Item item) {
        return item.toString().toLowerCase().contains("pickaxe");
    }
    
    /**
     * Check if an item is a shovel
     */
    private static boolean isShovel(Item item) {
        return item.toString().toLowerCase().contains("shovel");
    }
    
    /**
     * Check if an item is a hoe
     */
    private static boolean isHoe(Item item) {
        return item.toString().toLowerCase().contains("hoe");
    }
    
    /**
     * Swap to the best tool for the looked at block
     */
    private static void swapToBestTool(MinecraftClient client) {
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();
            
            swapToBestToolForBlock(client, block);
        }
    }
    
    /**
     * Find and switch to the best tool for a specific block
     */
    private static void swapToBestToolForBlock(MinecraftClient client, Block block) {
        if (client.player == null) return;
        
        // Check if there's a custom override for this block
        Optional<String> toolTypeOverride = getToolOverride(block);
        String bestToolType = toolTypeOverride.orElseGet(() -> getDefaultToolType(block));
        
        // Find the best tool of this type in inventory
        int bestToolSlot = findBestToolSlot(client.player, bestToolType);
        
        // If we found a valid tool slot and it's not already selected, swap to it
        if (bestToolSlot != -1 && bestToolSlot != client.player.getInventory().getSelectedSlot()) {
            // If the slot is in hotbar, select it directly
            if (bestToolSlot < 9) {
                // Use the method to set selected slot
                client.player.getInventory().setSelectedSlot(bestToolSlot);
                client.player.sendMessage(Text.literal("Switched to " + 
                        client.player.getInventory().getStack(bestToolSlot).getName().getString()), true);
            } else {
                // If the slot is in inventory, swap with current slot
                swapWithHotbar(client.player.getInventory(), bestToolSlot, client.player.getInventory().getSelectedSlot());
                client.player.sendMessage(Text.literal("Swapped with " + 
                        client.player.getInventory().getStack(client.player.getInventory().getSelectedSlot()).getName().getString()), true);
            }
        }
    }
    
    /**
     * Determine the default best tool type for a block
     */
    private static String getDefaultToolType(Block block) {
        // Get the block's registry ID to help identify special cases
        Identifier blockId = Registries.BLOCK.getId(block);
        String blockPath = blockId.getPath();
        
        // Check based on effective tool/mining requirements
        // This is a simplified approach - in a full implementation, we'd check the block's effective tool
        
        // Wood, logs, planks typically need axe
        if (blockPath.contains("log") || blockPath.contains("wood") || blockPath.contains("plank")) {
            return TOOL_AXE;
        }
        
        // Dirt, sand, gravel need shovel
        if (blockPath.contains("dirt") || blockPath.contains("sand") || blockPath.contains("gravel") || 
            blockPath.contains("clay") || blockPath.contains("soul_soil") || blockPath.contains("farmland")) {
            return TOOL_SHOVEL;
        }
        
        // Leaves, vines, vegetation benefit from shears
        if (blockPath.contains("leaves") || blockPath.contains("vine") || blockPath.contains("wool")) {
            return TOOL_SHEARS;
        }
        
        // Plants, crops use hoe
        if (blockPath.contains("crop") || blockPath.contains("hay") || blockPath.contains("plant")) {
            return TOOL_HOE;
        }
        
        // Default to pickaxe for most blocks (stone, ores, etc.)
        return TOOL_PICKAXE;
    }
    
    /**
     * Find the best tool of a given type in the player's inventory
     * @return the inventory slot index of the best tool, or -1 if none found
     */
    private static int findBestToolSlot(PlayerEntity player, String toolType) {
        PlayerInventory inventory = player.getInventory();
        int bestSlot = -1;
        float bestSpeed = -1;
        
        // Check all inventory slots (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isMatchingToolType(stack.getItem(), toolType)) {
                // For simplicity, we'll just use the tool's material quality as a proxy for quality
                float miningSpeed = getMiningSpeed(stack.getItem());
                
                if (miningSpeed > bestSpeed) {
                    bestSpeed = miningSpeed;
                    bestSlot = i;
                }
            }
        }
        
        return bestSlot;
    }
    
    /**
     * Check if an item is of the desired tool type
     */
    private static boolean isMatchingToolType(Item item, String toolType) {
        switch (toolType) {
            case TOOL_PICKAXE:
                return isPickaxe(item);
            case TOOL_AXE:
                return isAxe(item);
            case TOOL_SHOVEL:
                return isShovel(item);
            case TOOL_HOE:
                return isHoe(item);
            case TOOL_SHEARS:
                return item instanceof ShearsItem;
            default:
                return false;
        }
    }
    
    /**
     * Get a simplified mining speed rating for a tool
     * Higher tier tools get higher values
     */
    private static float getMiningSpeed(Item item) {
        // Base speed value for the tool type
        float baseSpeed = 1.0f;
        String itemName = item.toString().toLowerCase();
        
        // Determine tool quality based on name (contains material type)
        if (itemName.contains("netherite")) {
            return baseSpeed + 6.0f;
        } else if (itemName.contains("diamond")) {
            return baseSpeed + 5.0f;
        } else if (itemName.contains("gold")) {
            // Gold is fast but low durability
            return baseSpeed + 4.0f;
        } else if (itemName.contains("iron")) {
            return baseSpeed + 3.0f;
        } else if (itemName.contains("stone")) {
            return baseSpeed + 2.0f;
        } else if (item == Items.SHEARS) {
            return baseSpeed + 1.5f;
        }
        
        // Default/wooden tools
        return baseSpeed;
    }
    
    /**
     * Swap an item from inventory with the hotbar
     */
    private static void swapWithHotbar(PlayerInventory inventory, int inventorySlot, int hotbarSlot) {
        // Copy items between the slots
        ItemStack invStack = inventory.getStack(inventorySlot);
        ItemStack hotbarStack = inventory.getStack(hotbarSlot);
        
        inventory.setStack(hotbarSlot, invStack);
        inventory.setStack(inventorySlot, hotbarStack);
    }
    
    /**
     * Get custom tool override for a block if one exists
     */
    public static Optional<String> getToolOverride(Block block) {
        return Optional.ofNullable(customToolOverrides.get(block));
    }
    
    /**
     * Set a custom tool override for a specific block
     */
    public static void setToolOverride(Block block, String toolType) {
        if (toolType == null || toolType.isEmpty()) {
            customToolOverrides.remove(block);
        } else {
            customToolOverrides.put(block, toolType);
        }
    }
    
    /**
     * Get all tool override mappings
     */
    public static Map<Block, String> getToolOverrides() {
        return customToolOverrides;
    }
}
