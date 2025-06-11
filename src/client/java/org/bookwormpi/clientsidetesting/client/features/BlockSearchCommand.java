package org.bookwormpi.clientsidetesting.client.features;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BlockSearchCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("blocksearch")
                .then(ClientCommandManager.argument("block_id", StringArgumentType.word())
                    .executes(ctx -> {
                        String blockId = StringArgumentType.getString(ctx, "block_id");
                        String[] parts = blockId.split(":");
                        if (parts.length != 2) {
                            ctx.getSource().sendError(Text.literal("Invalid block ID format. Use 'namespace:block_name'."));
                            return Command.SINGLE_SUCCESS;
                        }
                        Block block = Registries.BLOCK.get(Identifier.of(parts[0], parts[1]));
                        if (block == null || block.getDefaultState().isAir()) {
                            ctx.getSource().sendError(Text.literal("Block not found: " + blockId));
                            return Command.SINGLE_SUCCESS;
                        }
                        BlockSearchFeature.blockToSearch = block;
                        BlockSearchFeature.enabled = true;
                        BlockSearchFeature.lastPlayerChunk = null;
                        ctx.getSource().sendFeedback(Text.literal("Searching for block: " + blockId));
                        return Command.SINGLE_SUCCESS;
                    })
                )
            );
        });
    }
}
