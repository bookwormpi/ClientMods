package org.bookwormpi.clientsidetesting.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for consistent error handling and user feedback
 */
public class ErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting-errors");
    
    public static void logAndNotify(String context, Exception e) {
        logAndNotify(context, e, true);
    }
    
    public static void logAndNotify(String context, Exception e, boolean notifyUser) {
        String message = String.format("[%s] %s: %s", context, e.getClass().getSimpleName(), e.getMessage());
        LOGGER.error(message, e);
        
        if (notifyUser && MinecraftClient.getInstance().player != null) {
            showErrorMessage("ClientSide Testing: " + e.getMessage());
        }
    }
    
    public static void showErrorMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[Error] " + message).formatted(Formatting.RED), 
                false
            );
        }
    }
    
    public static void showInfoMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[Info] " + message).formatted(Formatting.GREEN), 
                false
            );
        }
    }
    
    public static void showWarningMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[Warning] " + message).formatted(Formatting.YELLOW), 
                false
            );
        }
    }
}
