package me.bewf.luceat.client.light;

import java.io.IOException;
import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

import me.bewf.luceat.Luceat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class TextureFiles {
	private TextureFiles() {
	}

	public static NativeImage read(Identifier sprite) {
		Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		try {
			Resource resource = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
			if (resource == null) {
				return null;
			}
			try (InputStream stream = resource.open()) {
				return NativeImage.read(stream);
			}
		} catch (IOException | RuntimeException e) {
			Luceat.LOGGER.warn("Could not read texture {}", file, e);
			return null;
		}
	}
}
