package org.bookwormpi.clientsidetesting.client.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.bookwormpi.clientsidetesting.client.config.ModConfig;
import org.bookwormpi.clientsidetesting.client.utils.BlockSearchCache;
import org.bookwormpi.clientsidetesting.client.utils.CompatibilityChecker;
import org.bookwormpi.clientsidetesting.client.utils.PerformanceMonitor;
import org.bookwormpi.clientsidetesting.client.utils.UpdateChecker;

/**
 * Debug commands for development and troubleshooting
 */
public class DebugCommands {
    
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("cst") // ClientSide Testing
                .then(ClientCommandManager.literal("debug")
                    .then(ClientCommandManager.literal("config")
                        .executes(ctx -> {
                            ModConfig config = ModConfig.getInstance();
                            ctx.getSource().sendFeedback(Text.literal("=== Current Configuration ===").formatted(Formatting.YELLOW));
                            ctx.getSource().sendFeedback(Text.literal("Block Search Enabled: " + config.blockSearchEnabled));
                            ctx.getSource().sendFeedback(Text.literal("Block to Search: " + config.blockToSearchId));
                            ctx.getSource().sendFeedback(Text.literal("Max Rendered Blocks: " + config.maxRenderedBlocks));
                            ctx.getSource().sendFeedback(Text.literal("Scan Interval: " + config.scanIntervalTicks + " ticks"));
                            ctx.getSource().sendFeedback(Text.literal("Show Combat HUD: " + config.showCombatHud));
                            ctx.getSource().sendFeedback(Text.literal("Show Player Boxes: " + config.showPlayerBoxes));
                            ctx.getSource().sendFeedback(Text.literal("Show Chunk Players: " + config.showChunkPlayers));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(ClientCommandManager.literal("performance")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("=== Performance Metrics ===").formatted(Formatting.YELLOW));
                            ctx.getSource().sendFeedback(Text.literal("Block scans: " + PerformanceMonitor.getCounter("block_scans")));
                            ctx.getSource().sendFeedback(Text.literal("Cache hits: " + PerformanceMonitor.getCounter("cache_hits")));
                            ctx.getSource().sendFeedback(Text.literal("Cache size: " + BlockSearchCache.getCacheSize()));
                            
                            double avgScanTime = PerformanceMonitor.getAverageDuration("block_scan");
                            if (avgScanTime > 0) {
                                ctx.getSource().sendFeedback(Text.literal(String.format("Avg scan time: %.2fms", avgScanTime)));
                            }
                            
                            PerformanceMonitor.logMetrics();
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(ClientCommandManager.literal("compatibility")
                        .executes(ctx -> {
                            CompatibilityChecker.CompatibilityResult result = CompatibilityChecker.checkCompatibility();
                            ctx.getSource().sendFeedback(Text.literal("=== Compatibility Check ===").formatted(Formatting.YELLOW));
                            ctx.getSource().sendFeedback(Text.literal("Status: " + (result.compatible ? "✓ Compatible" : "✗ Issues Found"))
                                .formatted(result.compatible ? Formatting.GREEN : Formatting.RED));
                            ctx.getSource().sendFeedback(Text.literal("Details: " + result.message));
                            ctx.getSource().sendFeedback(Text.literal("Minecraft: " + CompatibilityChecker.getMinecraftVersionInfo()));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(ClientCommandManager.literal("cache")
                        .then(ClientCommandManager.literal("clear")
                            .executes(ctx -> {
                                BlockSearchCache.clearCache();
                                ctx.getSource().sendFeedback(Text.literal("Cache cleared").formatted(Formatting.GREEN));
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                        .then(ClientCommandManager.literal("stats")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Text.literal("Cache size: " + BlockSearchCache.getCacheSize()));
                                BlockSearchCache.cleanExpiredEntries();
                                ctx.getSource().sendFeedback(Text.literal("Expired entries cleaned"));
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                    .then(ClientCommandManager.literal("update")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("Checking for updates...").formatted(Formatting.YELLOW));
                            UpdateChecker.checkForUpdates().thenAccept(updateInfo -> {
                                if (updateInfo.updateAvailable) {
                                    ctx.getSource().sendFeedback(Text.literal("Update available!").formatted(Formatting.GREEN));
                                    ctx.getSource().sendFeedback(Text.literal("Current: " + updateInfo.currentVersion));
                                    ctx.getSource().sendFeedback(Text.literal("Latest: " + updateInfo.latestVersion));
                                    ctx.getSource().sendFeedback(Text.literal("Download: " + updateInfo.downloadUrl));
                                } else {
                                    ctx.getSource().sendFeedback(Text.literal("Mod is up to date (" + updateInfo.currentVersion + ")").formatted(Formatting.GREEN));
                                }
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(ClientCommandManager.literal("config")
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("key", StringArgumentType.word())
                            .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key");
                                    String value = StringArgumentType.getString(ctx, "value");
                                    
                                    ModConfig config = ModConfig.getInstance();
                                    boolean changed = false;
                                    
                                    switch (key.toLowerCase()) {
                                        case "maxblocks" -> {
                                            try {
                                                config.maxRenderedBlocks = Integer.parseInt(value);
                                                changed = true;
                                            } catch (NumberFormatException e) {
                                                ctx.getSource().sendError(Text.literal("Invalid number: " + value));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                        }
                                        case "scaninterval" -> {
                                            try {
                                                config.scanIntervalTicks = Integer.parseInt(value);
                                                changed = true;
                                            } catch (NumberFormatException e) {
                                                ctx.getSource().sendError(Text.literal("Invalid number: " + value));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                        }
                                        case "blocksearch" -> {
                                            config.blockSearchEnabled = Boolean.parseBoolean(value);
                                            changed = true;
                                        }
                                        default -> {
                                            ctx.getSource().sendError(Text.literal("Unknown config key: " + key));
                                            ctx.getSource().sendFeedback(Text.literal("Available keys: maxblocks, scaninterval, blocksearch"));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                    }
                                    
                                    if (changed) {
                                        config.save();
                                        ctx.getSource().sendFeedback(Text.literal("Config updated: " + key + " = " + value).formatted(Formatting.GREEN));
                                    }
                                    
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                )
            );
        });
    }
}
