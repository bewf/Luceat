package me.bewf.luceat.client.shader;

import net.minecraft.client.Minecraft;

public final class VariantWatcher {
	private static ShaderVariant asked;
	private static int stable;

	private VariantWatcher() {
	}

	public static boolean satisfies(boolean rays, boolean spread) {
		ShaderVariant built = TerrainShaderPatch.built();
		return built != null && (!rays || built.rays()) && (!spread || built.spread());
	}

	public static void rebuildNow(Minecraft client) {
		ShaderVariant built = TerrainShaderPatch.built();
		ShaderVariant wanted = ShaderVariant.wanted();
		if (client.level == null || built == wanted) {
			return;
		}
		asked = wanted;
		stable = 0;
		client.levelRenderer.allChanged();
	}

	public static void tick(Minecraft client) {
		ShaderVariant built = TerrainShaderPatch.built();
		if (client.level == null || built == null) {
			return;
		}
		ShaderVariant wanted = ShaderVariant.wanted();
		if (wanted == built) {
			asked = null;
			stable = 0;
			return;
		}
		if (client.screen != null || wanted == asked) {
			stable = 0;
			return;
		}
		if (++stable < 10) {
			return;
		}
		asked = wanted;
		stable = 0;
		client.levelRenderer.allChanged();
	}
}
