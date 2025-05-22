package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bookwormpi.clientsidetesting.client.combat.CombatHudFeature;

public class ClientsidetestingClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting");
    public static boolean showChunkPlayers = false;
    public static boolean showPlayerBoxes = false;
    public static int chunkCheckRadius = 3;
    public static boolean showCombatHud = true;
    
    // Key bindings
    private static KeyBinding targetCycleKey;
    private static KeyBinding aimLockKey;
    private static KeyBinding configScreenKey;
    private static KeyBinding arrowDebugToggleKey;

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
        
        // Register aim lock key
        aimLockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientsidetesting.aim_lock",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category.clientsidetesting.combat"
        ));
        
        // Register config screen key
        configScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientsidetesting.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.clientsidetesting.general"
        ));
        
        // Register arrow debug toggle key (F8)
        arrowDebugToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientsidetesting.arrow_debug_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.clientsidetesting.debug"
        ));
        
        // Register tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (targetCycleKey.wasPressed() && showCombatHud) {
                CombatHudFeature.handleTargetCycleKeyPress();
            }
            if (aimLockKey.wasPressed() && showCombatHud) {
                CombatHudFeature.handleAimLockKeyPress();
            }
            if (configScreenKey.wasPressed()) {
                client.setScreen(new ClientsideTestingConfigScreen());
            }
            // Arrow debug key
            if (arrowDebugToggleKey.wasPressed()) {
                // No action needed as ArrowDebugFeature is removed
            }
        });
        
        // Register features
        CombatHudFeature.register();
        BlockSearchFeature.register();
        
        LOGGER.info("Client Side Testing mod initialized");
    }
}