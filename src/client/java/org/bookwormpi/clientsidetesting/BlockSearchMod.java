package org.bookwormpi.clientsidetesting;

import net.fabricmc.api.ClientModInitializer;
// Ensure the correct package path for BlockSearchFeature
import org.bookwormpi.clientsidetesting.client.BlockSearchFeature; // Adjust the package path as needed

// IMPORTANT: Ensure your fabric.mod.json contains:
// "entrypoints": { "client": ["org.bookwormpi.clientsidetesting.BlockSearchMod"] }
// Otherwise, your mod will not initialize and BlockSearchFeature will not be registered.

public class BlockSearchMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockSearchFeature.register();
        // BlockSearchCommand.register(); // Revert to GUI-based search only
    }
}