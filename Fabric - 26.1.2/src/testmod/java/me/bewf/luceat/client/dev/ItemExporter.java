
        package me.bewf.luceat.client.dev;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ItemExporter implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path output = FabricLoader.getInstance()
                .getGameDir()
                .resolve("luceat-items.txt");

        var items = BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString)
                .sorted()
                .toList();

        try {
            Files.write(output, items, StandardCharsets.UTF_8);
            System.out.println("[Luceat Dev] Exported " + items.size()
                    + " item IDs to " + output.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to export item registry", e);
        }
    }
}
