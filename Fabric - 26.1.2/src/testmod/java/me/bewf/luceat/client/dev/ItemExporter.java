
package me.bewf.luceat.client.dev;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class ItemExporter {
    private ItemExporter() {}

    public static void export() {
        Path output = FabricLoader.getInstance()
                .getGameDir()
                .resolve("luceat-items.txt");

        var items = BuiltInRegistries.ITEM.keySet().stream()
                .map(id -> {
                    var item = BuiltInRegistries.ITEM.get(id);
                    String name = id.toString();

                    if (name.startsWith("minecraft:")) {
                        name = name.substring("minecraft:".length());
                    }

                    if (item instanceof BlockItem blockItem) {
                        int light = blockItem.getBlock()
                                .defaultBlockState()
                                .getLightEmission();

                        if (light > 0) {
                            return name + " [light=" + light + "]";
                        }
                    }

                    return name;
                })
                .sorted()
                .collect(Collectors.toList());

        try {
            Files.write(output, items, StandardCharsets.UTF_8);
            System.out.println("[Luceat Dev] Exported " + items.size()
                    + " item IDs to " + output.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to export item registry", e);
        }
    }
}
