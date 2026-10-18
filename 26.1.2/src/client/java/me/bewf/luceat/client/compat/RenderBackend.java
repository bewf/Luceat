package me.bewf.luceat.client.compat;

import java.lang.reflect.Method;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;

import me.bewf.luceat.Luceat;

import net.fabricmc.loader.api.FabricLoader;

public final class RenderBackend {
	public enum Kind {
		OPENGL("OpenGL"),
		VULKAN("Vulkan"),
		VULKANMOD("VulkanMod"),
		UNKNOWN("Unknown");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	private static Kind kind;
	private static boolean glFailed;

	private RenderBackend() {
	}

	public static Kind kind() {
		if (kind != null) {
			return kind;
		}
		if (FabricLoader.getInstance().isModLoaded("vulkanmod")) {
			kind = Kind.VULKANMOD;
			return kind;
		}
		String name = backendName();
		if (name == null) {
			return Kind.UNKNOWN;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("vulkan")) {
			kind = Kind.VULKAN;
		} else if (lower.contains("opengl") || lower.contains("gl")) {
			kind = Kind.OPENGL;
		} else {
			kind = Kind.UNKNOWN;
		}
		Luceat.LOGGER.info("Luceat sees the {} renderer", kind.label());
		return kind;
	}

	public static boolean terrainPathAvailable() {
		if (glFailed) {
			return false;
		}
		Kind found = kind();
		return found == Kind.OPENGL || found == Kind.UNKNOWN;
	}

	public static void glFailed(Throwable error) {
		if (!glFailed) {
			glFailed = true;
			Luceat.LOGGER.warn("Luceat could not use the OpenGL terrain path and will keep running without it", error);
		}
	}

	public static String note() {
		if (glFailed) {
			return "The terrain light path hit a graphics error on this setup. Held light on entities and the menu still work.";
		}
		switch (kind()) {
			case VULKAN:
				return "You are using the Vulkan renderer. Terrain light and shadows are not ready for Vulkan yet, so only the held and entity light shows.";
			case VULKANMOD:
				return "VulkanMod is installed. Terrain light and shadows are not ready for it yet, so only the held and entity light shows.";
			default:
				return null;
		}
	}

	private static String backendName() {
		try {
			Object device = RenderSystem.tryGetDevice();
			if (device == null) {
				return null;
			}
			Object text = call(device, "getBackendName");
			if (text == null) {
				Object info = call(device, "getDeviceInfo");
				if (info != null) {
					text = call(info, "backendName");
				}
			}
			return text == null ? null : text.toString();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object call(Object target, String name) {
		try {
			Method method = target.getClass().getMethod(name);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
