package org.bookwormpi.clientsidetesting.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralized configuration management for the mod
 * Handles saving/loading settings to/from JSON file
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("clientsidetesting.json");
    
    // HUD Settings
    public boolean showChunkPlayers = false;
    public boolean showPlayerBoxes = false;
    public boolean showCombatHud = true;
    public int chunkCheckRadius = 3;
    
    // Block Search Settings
    public boolean blockSearchEnabled = false;
    public String blockToSearchId = "minecraft:diamond_block";
    public int maxRenderedBlocks = 256;
    public int scanIntervalTicks = 5;
    public int searchDistance = 8; // chunks
    
    // Combat Settings
    public boolean aimLockEnabled = false;
    public float aimSmoothingFactor = 0.60f;
    
    private static ModConfig instance;
    
    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public static ModConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                LOGGER.info("Loaded configuration from {}", CONFIG_FILE);
                return config;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
        }
        
        ModConfig config = new ModConfig();
        config.save(); // Save default config
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_FILE, json);
            LOGGER.debug("Saved configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
    
    public Block getBlockToSearch() {
        try {
            return Registries.BLOCK.get(Identifier.of(blockToSearchId));
        } catch (Exception e) {
            LOGGER.warn("Invalid block ID: {}, using diamond block", blockToSearchId);
            return Blocks.DIAMOND_BLOCK;
        }
    }
    
    public void setBlockToSearch(Block block) {
        this.blockToSearchId = Registries.BLOCK.getId(block).toString();
        save();
    }
}
