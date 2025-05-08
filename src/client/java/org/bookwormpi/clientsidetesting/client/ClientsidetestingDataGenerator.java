package org.bookwormpi.clientsidetesting.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ClientsidetestingDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // Create pack but comment it out since we're not using it yet
        // FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        // Combat HUD data generation would go here if needed
    }
}
