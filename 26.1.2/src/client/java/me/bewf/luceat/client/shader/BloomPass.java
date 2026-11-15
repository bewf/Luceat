package me.bewf.luceat.client.shader;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.BloomLevel;
import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class BloomPass {
	private static final Identifier LOW = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "bloom_low");
	private static final Identifier MEDIUM = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "bloom_medium");
	private static final Identifier HIGH = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "bloom_high");
	private static final Set<Identifier> TARGETS = Set.of(PostChain.MAIN_TARGET_ID);

	private static BloomLevel failed = BloomLevel.OFF;
	private static BloomLevel active = BloomLevel.OFF;

	private BloomPass() {
	}

	public static BloomLevel active() {
		return active;
	}

	public static void run(Minecraft minecraft, GraphicsResourceAllocator allocator) {
		LuceatConfig config = LuceatConfig.get();
		BloomLevel level = config.bloom;
		active = BloomLevel.OFF;
		if (level != failed) {
			failed = BloomLevel.OFF;
		}
		if (!config.enabled || level == BloomLevel.OFF || level == failed || minecraft.level == null || IrisCompat.packOn()) {
			return;
		}
		try {
			PostChain chain = minecraft.getShaderManager().getPostChain(identifier(level), TARGETS);
			if (chain == null) {
				fail(level, null);
				return;
			}
			chain.process(minecraft.getMainRenderTarget(), allocator);
			active = level;
		} catch (RuntimeException error) {
			fail(level, error);
		}
	}

	private static Identifier identifier(BloomLevel level) {
		return switch (level) {
			case LOW -> LOW;
			case MEDIUM -> MEDIUM;
			default -> HIGH;
		};
	}

	private static void fail(BloomLevel level, RuntimeException error) {
		failed = level;
		Luceat.LOGGER.warn("Luceat bloom ({}) could not run, so it is switched off until the setting changes", level, error);
	}
}
