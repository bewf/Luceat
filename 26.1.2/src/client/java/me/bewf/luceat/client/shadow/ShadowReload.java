package me.bewf.luceat.client.shadow;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.gui.preview.BlockVoxels;
import me.bewf.luceat.client.gui.preview.Scenes;
import me.bewf.luceat.client.light.block.BlockRules;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ShadowReload extends SimplePreparableReloadListener<Object> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "shadow_patterns");

	@Override
	protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
		return new Object();
	}

	@Override
	protected void apply(Object prepared, ResourceManager manager, ProfilerFiller profiler) {
		BlockVoxels.clear();
		Scenes.clear();
		BlockRules.rebuild();
		ShadowVolume.reset();
	}
}
