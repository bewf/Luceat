package me.bewf.luceat.client.compat;

import java.lang.reflect.Method;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShaderPackMode;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class IrisCompat {
	private static final int CHECK_EVERY = 20;

	private static boolean broken;
	private static Object api;
	private static Method inUse;
	private static Method getConfig;
	private static Method setEnabled;
	private static int counter;
	private static boolean packOn;

	private IrisCompat() {
	}

	public static boolean packOn() {
		return packOn;
	}

	public static boolean standDown() {
		return packOn && LuceatConfig.get().shaderPacks == ShaderPackMode.LEAVE;
	}

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	public static void tick(Minecraft minecraft) {
		if (broken || minecraft.level == null) {
			return;
		}
		counter++;
		if (counter < CHECK_EVERY) {
			return;
		}
		counter = 0;
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || !installed()) {
			packOn = false;
			return;
		}
		try {
			if (api == null) {
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = apiClass.getMethod("getInstance").invoke(null);
				inUse = apiClass.getMethod("isShaderPackInUse");
				getConfig = apiClass.getMethod("getConfig");
				Class<?> configClass = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
				setEnabled = configClass.getMethod("setShadersEnabledAndApply", boolean.class);
			}
			packOn = Boolean.TRUE.equals(inUse.invoke(api));
			if (packOn && config.shaderPacks == ShaderPackMode.TURN_OFF) {
				Object irisConfig = getConfig.invoke(api);
				setEnabled.invoke(irisConfig, false);
				packOn = false;
				Luceat.LOGGER.info("Luceat turned the shader pack off");
			}
		} catch (Throwable error) {
			broken = true;
			Luceat.LOGGER.warn("Luceat could not talk to Iris, so it will leave shader packs alone", error);
		}
	}
}
