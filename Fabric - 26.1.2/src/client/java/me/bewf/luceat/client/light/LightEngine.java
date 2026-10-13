package me.bewf.luceat.client.light;

import java.util.List;

import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class LightEngine {
	private static final LightBuffer BUFFER = new LightBuffer();

	private LightEngine() {
	}

	public static void upload(int program) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		List<LightSource> lights = EntityLights.collect(minecraft, camera, partialTick);
		ShadowVolume.update(minecraft.level, camera);
		BUFFER.update(lights, camera);
		BUFFER.bind(program);
		ShadowVolume.bind(program);
	}
}
