package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.bookwormpi.clientsidetesting.client.combat.CombatHudFeature;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientsidetestingClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting");
    public static boolean showChunkPlayers = false;
    public static boolean showPlayerBoxes = false;
    public static int chunkCheckRadius = 3;
    public static boolean showCombatHud = true;
    
    // Combat HUD key binding
    private static KeyBinding targetCycleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Client Side Testing mod");
        
        // Register combat HUD target cycle key
        targetCycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientsidetesting.target_cycle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.clientsidetesting.combat"
        ));
        
        // Register tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (targetCycleKey.wasPressed() && showCombatHud) {
                CombatHudFeature.handleTargetCycleKeyPress();
            }
        });
        
        // Register features
        BlockSearchFeature.register();
        ChunkPlayersHudFeature.register();
        PlayerBoxRenderFeature.register();
        CombatHudFeature.register();
        
        LOGGER.info("Client Side Testing mod initialized");
    }
}