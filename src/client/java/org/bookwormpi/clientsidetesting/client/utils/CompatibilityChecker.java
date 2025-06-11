package org.bookwormpi.clientsidetesting.client.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Utility for checking mod and dependency compatibility
 */
public class CompatibilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting-compat");
    
    public static class CompatibilityResult {
        public final boolean compatible;
        public final String message;
        
        public CompatibilityResult(boolean compatible, String message) {
            this.compatible = compatible;
            this.message = message;
        }
    }
    
    /**
     * Check if all required dependencies are present and compatible
     */
    public static CompatibilityResult checkCompatibility() {
        // Check Fabric API version
        Optional<ModContainer> fabricApi = FabricLoader.getInstance().getModContainer("fabric-api");
        if (fabricApi.isEmpty()) {
            return new CompatibilityResult(false, "Fabric API is required but not installed");
        }
        
        // Check for known incompatible mods
        if (FabricLoader.getInstance().isModLoaded("optifine")) {
            LOGGER.warn("OptiFine detected - some features may not work correctly. Consider using Sodium + Iris instead. ;)");
            return new CompatibilityResult(true, "OptiFine detected - some rendering features may not work. Consider Sodium + Iris.");
        }
        
        // Check for recommended performance mods
        boolean hasSodium = FabricLoader.getInstance().isModLoaded("sodium");
        boolean hasIris = FabricLoader.getInstance().isModLoaded("iris");
        
        if (!hasSodium || !hasIris) {
            LOGGER.info("Performance mods not detected. For better performance, consider installing Sodium and Iris.");
        }
        
        return new CompatibilityResult(true, "All compatibility checks passed");
    }
    
    /**
     * Check if a specific mod version meets minimum requirements
     */
    public static boolean isVersionCompatible(String modId, String minVersion) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (mod.isEmpty()) {
            return false;
        }
        
        try {
            Version currentVersion = mod.get().getMetadata().getVersion();
            Version minimumVersion = Version.parse(minVersion);
            return currentVersion.compareTo(minimumVersion) >= 0;
        } catch (VersionParsingException e) {
            LOGGER.warn("Failed to parse version for mod {}: {}", modId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get Minecraft version compatibility info
     */
    public static String getMinecraftVersionInfo() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown");
    }
}
