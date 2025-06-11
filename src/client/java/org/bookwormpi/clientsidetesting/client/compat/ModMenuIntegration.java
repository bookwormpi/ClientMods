package org.bookwormpi.clientsidetesting.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * ModMenu integration - provides config screen access from the mods menu
 * Only loads when ModMenu is present
 */
public class ModMenuIntegration {
    
    /**
     * Initialize ModMenu integration if ModMenu is available
     */
    public static void initialize() {
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                // Load ModMenu integration via reflection to avoid hard dependency
                Class.forName("org.bookwormpi.clientsidetesting.client.compat.ModMenuIntegrationImpl");
            } catch (ClassNotFoundException e) {
                // ModMenu classes not available, skip integration
            }
        }
    }
}
