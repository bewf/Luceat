package me.bewf.luceat.client.light;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class LightEngine {
	private static final LightBuffer BUFFER = new LightBuffer();

	private LightEngine() {
	}

	public static void upload(int program) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		List<LightSource> lights = player == null ? List.of() : HeldLights.collect(player, partialTick);
		BUFFER.update(lights, camera);
		BUFFER.bind(program);
	}
}
