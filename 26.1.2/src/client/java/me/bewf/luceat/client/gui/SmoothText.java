package me.bewf.luceat.client.gui;

import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SmoothText {
	private static final Set<GpuTextureView> VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private SmoothText() {
	}

	public static void register(GpuTextureView view) {
		VIEWS.add(view);
	}

	public static boolean has(GpuTextureView view) {
		return VIEWS.contains(view);
	}
}
