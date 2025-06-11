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
import org.bookwormpi.clientsidetesting.client.features.BlockSearchFeature;
import org.bookwormpi.clientsidetesting.client.features.BlockSearchCommand;
import org.bookwormpi.clientsidetesting.client.ui.MainConfigScreen;
import org.bookwormpi.clientsidetesting.client.debug.DebugCommands;
import org.bookwormpi.clientsidetesting.client.utils.CompatibilityChecker;

public class ClientSideTestingClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting");
    public static boolean showChunkPlayers = false;
    public static boolean showPlayerBoxes = false;
    public static int chunkCheckRadius = 3;
    public static boolean showCombatHud = true;
    
    // Key bindings
    private static KeyBinding targetCycleKey;
    private static KeyBinding aimLockKey;
    private static KeyBinding configScreenKey;

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
        
        // Register tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (targetCycleKey.wasPressed() && showCombatHud) {
                CombatHudFeature.handleTargetCycleKeyPress();
            }
            if (aimLockKey.wasPressed() && showCombatHud) {
                CombatHudFeature.handleAimLockKeyPress();
            }
            if (configScreenKey.wasPressed()) {
                client.setScreen(new MainConfigScreen());
            }
        });
        
        // Register features
        CombatHudFeature.register();
        BlockSearchFeature.register();
        
        // Register commands
        BlockSearchCommand.register();
        DebugCommands.register();
        
        // Check compatibility
        CompatibilityChecker.CompatibilityResult compatResult = CompatibilityChecker.checkCompatibility();
        if (!compatResult.compatible) {
            LOGGER.warn("Compatibility issues detected: {}", compatResult.message);
        }
        
        LOGGER.info("Client Side Testing mod initialized");
    }
}